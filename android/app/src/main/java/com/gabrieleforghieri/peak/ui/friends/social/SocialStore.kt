package com.gabrieleforghieri.peak.ui.friends.social

import androidx.compose.runtime.Composable
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.sync.SyncEngine
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Gli amici letti dal server, tenuti in memoria per le schermate (`Store/SocialStore.swift`). Niente di tutto questo si salva sul
 * telefono: senza account yG o senza rete restano vuoti e le schermate lo dicono.
 *
 * `available` = serve un telefono collegato a yG; `api` dà il client col token di adesso (o `null`).
 */
class SocialStore(val available: StateFlow<Boolean>, private val api: () -> SocialApi?) {
    private val _friends = MutableStateFlow<FriendLists?>(null)
    private val _league = MutableStateFlow<List<LeagueRow>?>(null)
    private val _news = MutableStateFlow<List<NewsEvent>?>(null)
    private val _failed = MutableStateFlow(false)

    val friends: StateFlow<FriendLists?> = _friends
    val league: StateFlow<List<LeagueRow>?> = _league
    val news: StateFlow<List<NewsEvent>?> = _news

    /** L'ultima richiesta non è andata a buon fine (niente rete, o il server non risponde). */
    val failed: StateFlow<Boolean> = _failed

    val isAvailable: Boolean get() = available.value
    val friendCount: Int get() = _friends.value?.friends?.size ?: 0

    /** Il client di adesso, per le chiamate che la schermata fa da sé (ricerca, richieste, classifica). */
    fun client(): SocialApi? = api()

    suspend fun refresh() {
        if (!isAvailable) return
        val api = api() ?: return
        try {
            coroutineScope {
                val f = async { api.friends() }
                val l = async { api.league() }
                val n = async { api.news() }
                _friends.value = f.await()
                _league.value = l.await()
                _news.value = n.await()
            }
            _failed.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _failed.value = true
        }
    }

    /** Dopo una richiesta, un'accettazione o una rimozione. */
    suspend fun reload() = refresh()
}

/** Lo store dell'app, uno solo (come `SocialStore.shared`): il client prende il token della sincronizzazione al momento della chiamata. */
object Social {
    private var instance: SocialStore? = null

    fun of(app: PeakApplication): SocialStore = instance ?: synchronized(this) {
        instance ?: run {
            val engine = app.sync.engine
            val available = engine.status.map { it.state == SyncEngine.State.Connected }
                .stateIn(app.mainScope, SharingStarted.Eagerly, engine.status.value.state == SyncEngine.State.Connected)
            SocialStore(available) { engine.status.value.credentials?.let { HttpSocialApi(app.http, it.token) } }
        }.also { instance = it }
    }
}

@Composable
fun rememberSocialStore(): SocialStore = Social.of(LocalApp.current)
