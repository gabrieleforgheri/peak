package com.gabrieleforghieri.peak.ui.friends

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.gabrieleforghieri.peak.design.LocalAccent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.sync.PeakApiException
import com.gabrieleforghieri.peak.ui.friends.social.BoardRow
import com.gabrieleforghieri.peak.ui.friends.social.FriendLists
import com.gabrieleforghieri.peak.ui.friends.social.FriendPerson
import com.gabrieleforghieri.peak.ui.friends.social.FriendProfileData
import com.gabrieleforghieri.peak.ui.friends.social.FriendState
import com.gabrieleforghieri.peak.ui.friends.social.LeagueRow
import com.gabrieleforghieri.peak.ui.friends.social.NewsEvent
import com.gabrieleforghieri.peak.ui.friends.social.NoApi
import com.gabrieleforghieri.peak.ui.friends.social.SharedMax
import com.gabrieleforghieri.peak.ui.friends.social.SharedRow
import com.gabrieleforghieri.peak.ui.friends.social.SharedTemplate
import com.gabrieleforghieri.peak.ui.friends.social.SocialApi
import com.gabrieleforghieri.peak.ui.friends.social.SocialStore
import com.gabrieleforghieri.peak.ui.screenshot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gli screenshot di Amici in italiano, uno per schermata e stato (`android/docs/screens/friends-*.png`): senza account, vuoto, con amici,
 * errore di rete, profilo, invito. Le risposte del server sono finte ([FakeSocial]); i fogli stanno in una finestra a parte e non entrano
 * nello screenshot, quindi dell'invito e della conferma si disegna il contenuto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-" + RobolectricDeviceQualifiers.Pixel7)
class FriendsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = 86_400_000L
    private fun ago(days: Int) = System.currentTimeMillis() - days * day
    private val earlier = 1_790_000_000_000L // 21 settembre 2026: la data resta la stessa, qualunque sia il giorno in cui si registra

    private val marta = FriendPerson(2, "marta_r", "Marta")
    private val luca = FriendPerson(3, "luca.b", "Luca")
    private val sara = FriendPerson(4, "sara94", "Sara")

    private fun social(api: SocialApi?, available: Boolean = true) = SocialStore(MutableStateFlow(available)) { api }

    private val league = listOf(
        LeagueRow(1, "gab", true, 18_450.0, 9, 6, ago(0)),
        LeagueRow(2, "marta_r", false, 24_300.0, 11, 14, ago(0)),
        LeagueRow(3, "luca.b", false, 9_800.0, 4, 2, ago(1)),
        LeagueRow(4, "sara94", false, 0.0, 0, 0, null),
    )
    private val news = listOf(
        NewsEvent(2, "marta_r", "squat-barbell", "Squat", 100.0, 5, ago(0), passedYou = true),
        NewsEvent(3, "luca.b", "lat-pulldown", "Lat Machine", 65.0, 10, ago(1), passedYou = false),
        NewsEvent(4, "sara94", "", "Hip thrust a corpo libero", 40.0, 12, earlier, passedYou = false),
    )

    private fun withFriends() = object : SocialApi by NoApi {
        override suspend fun friends() = FriendLists(listOf(marta, luca, sara), received = listOf(FriendPerson(9, "giulia_k")))
        override suspend fun league() = this@FriendsScreenshotTest.league
        override suspend fun news() = this@FriendsScreenshotTest.news
        override suspend fun board(catalog: String) = listOf(
            BoardRow(2, "marta_r", false, 116.7, 100.0, 5), BoardRow(1, "gab", true, 105.0, 90.0, 5), BoardRow(3, "luca.b", false, 88.0, 80.0, 3),
        )
        override suspend fun searchUsers(query: String) = listOf(
            FriendPerson(10, "marco_t") to FriendState.none, FriendPerson(11, "marta_r") to FriendState.friends, FriendPerson(12, "martina.v") to FriendState.sent,
        )
    }

    private fun noFriends() = object : SocialApi by NoApi {
        override suspend fun friends() = FriendLists()
        override suspend fun league() = emptyList<LeagueRow>()
        override suspend fun news() = emptyList<NewsEvent>()
    }

    private fun offline() = object : SocialApi by NoApi {
        override suspend fun friends(): FriendLists = throw PeakApiException.Offline()
        override suspend fun league(): List<LeagueRow> = throw PeakApiException.Offline()
        override suspend fun news(): List<NewsEvent> = throw PeakApiException.Offline()
        override suspend fun profile(id: Int): FriendProfileData = throw PeakApiException.Offline()
        override suspend fun createInvite(): String = throw PeakApiException.Offline()
    }

    /** I tuoi macchinari collegati al catalogo, con un massimale, per la classifica «Esercizio per esercizio» e il confronto nel profilo. */
    @Before fun myExercises() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PeakApplication>()
        app.database.exercises().upsert(ExerciseEntity("00000000-0000-4000-8000-000000000001", name = "Squat", muscleGroup = "gambe", catalogID = "squat-barbell"))
        app.database.exercises().upsert(ExerciseEntity("00000000-0000-4000-8000-000000000002", name = "Panca piana", muscleGroup = "petto", catalogID = "bench-press-barbell", sortIndex = 1))
        app.database.exercises().upsert(ExerciseEntity("00000000-0000-4000-8000-000000000003", name = "Lat Machine", muscleGroup = "schiena", catalogID = "lat-pulldown", sortIndex = 2))
        app.database.records().upsert(MaxRecordEntity("00000000-0000-4000-8000-0000000000A1", "00000000-0000-4000-8000-000000000001", date = earlier, weight = 90.0, reps = 5))
        app.database.records().upsert(MaxRecordEntity("00000000-0000-4000-8000-0000000000A2", "00000000-0000-4000-8000-000000000002", date = earlier, weight = 70.0, reps = 5))
        app.database.records().upsert(MaxRecordEntity("00000000-0000-4000-8000-0000000000A3", "00000000-0000-4000-8000-000000000003", date = earlier, weight = 60.0, reps = 8))
    }

    // ---- la scheda Amici ----

    @Test fun friendsWithoutAccount() = compose.screenshot("friends-senza-account") {
        FriendsScreen(onOpenFriend = {}, social = social(null, available = false))
    }

    @Test fun friendsEmpty() = compose.screenshot("friends-vuoto") {
        FriendsScreen(onOpenFriend = {}, social = social(noFriends()))
    }

    @Config(qualifiers = "it-w411dp-h1500dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    @Test fun friendsWithFriends() = compose.screenshot("friends-con-amici") {
        FriendsScreen(onOpenFriend = {}, social = social(withFriends()))
    }

    /** Come sull'iPhone la rete assente non ha una schermata sua: l'elenco resta vuoto e mostra l'invito. */
    @Test fun friendsNetworkError() = compose.screenshot("friends-errore-rete") {
        FriendsScreen(onOpenFriend = {}, social = social(offline()))
    }

    @Config(qualifiers = "it-w411dp-h1500dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    @Test fun friendsSearchResults() {
        compose.setContent {
            com.gabrieleforghieri.peak.ui.nav.PeakEnvironment(ApplicationProvider.getApplicationContext()) {
                PeakTheme { Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current))) { FriendsScreen(onOpenFriend = {}, social = social(withFriends())) } }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("mar")
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("@marco_t").assertIsDisplayed()
        compose.onNodeWithText("Richiesta inviata").assertIsDisplayed()
        capture("friends-ricerca")
    }

    // ---- il profilo ----

    private val profile = FriendProfileData(
        marta,
        listOf(
            SharedMax("squat-barbell", "Squat", 116.7, 100.0, 5, ago(0)),
            SharedMax("bench-press-barbell", "Bench Press", 105.0, 90.0, 5, ago(1)),
            SharedMax("lat-pulldown", "Lat Machine", 60.0, 60.0, 1, earlier),
            SharedMax("", "Hip thrust a corpo libero", 110.0, 100.0, 3, earlier),
        ),
        listOf(
            SharedTemplate("T1", "Gambe", listOf(SharedRow("squat-barbell", "Squat", 4, 6, 8), SharedRow("leg-extension", "Leg Extension", 3, 12, 12), SharedRow("", "Hip thrust a corpo libero", 3, 10, 12))),
            SharedTemplate("T2", "Solo uno", listOf(SharedRow("lat-pulldown", "Lat Machine", 0, 0, 0))),
        ),
    )

    private fun profileApi(data: FriendProfileData?) = object : SocialApi by NoApi {
        override suspend fun friends() = FriendLists(listOf(marta))
        override suspend fun profile(id: Int): FriendProfileData = data ?: throw PeakApiException.Offline()
    }

    @Config(qualifiers = "it-w411dp-h1150dp-normal-long-notround-any-420dpi-keyshidden-nonav")
    @Test fun profileWithData() = compose.screenshot("friends-profilo-con-dati") {
        FriendProfileScreen("marta_r", onBack = {}, social = social(profileApi(profile)))
    }

    @Test fun profileEmpty() = compose.screenshot("friends-profilo-vuoto") {
        FriendProfileScreen("marta_r", onBack = {}, social = social(profileApi(FriendProfileData(marta, emptyList(), emptyList()))))
    }

    @Test fun profileNetworkError() = compose.screenshot("friends-profilo-errore-rete") {
        FriendProfileScreen("marta_r", onBack = {}, social = social(profileApi(null)))
    }

    @Test fun profileOfSomeoneNotAFriend() = compose.screenshot("friends-profilo-sconosciuto") {
        FriendProfileScreen("fantasma", onBack = {}, social = social(profileApi(profile)))
    }

    @Test fun removeFriendSheet() = compose.screenshot("friends-profilo-togli") {
        Box(Modifier.fillMaxWidth().background(PeakTheme.bgRaised).padding(top = 40.dp)) { RemoveFriendContent("marta_r", {}, {}) }
    }

    // ---- l'invito ----

    @Test fun inviteWithQr() = compose.screenshot("friends-invito-qr") {
        Box(Modifier.fillMaxWidth().background(PeakTheme.bgRaised)) {
            InviteContent(social(object : SocialApi by NoApi { override suspend fun createInvite() = "Zk3_-Zk3_-Zk3_-Zk3_-Zk3_-Zk3_-Zk3_-Zk3_-Zk3_-Zk" }), onClose = {})
        }
    }

    @Test fun inviteNetworkError() = compose.screenshot("friends-invito-errore-rete") {
        Box(Modifier.fillMaxWidth().background(PeakTheme.bgRaised)) { InviteContent(social(offline()), onClose = {}) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screens/$name.png")
    }
}
