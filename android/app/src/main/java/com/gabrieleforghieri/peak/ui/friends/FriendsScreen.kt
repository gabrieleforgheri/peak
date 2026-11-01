package com.gabrieleforghieri.peak.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.design.glassSurface
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.ui.friends.social.BoardRow
import com.gabrieleforghieri.peak.ui.friends.social.FriendPerson
import com.gabrieleforghieri.peak.ui.friends.social.FriendState
import com.gabrieleforghieri.peak.ui.friends.social.SocialStore
import com.gabrieleforghieri.peak.ui.friends.social.rememberSocialStore
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.start.ConnectSheet
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La scheda Amici (`Features/Friends/FriendsView.swift`): invito (link e QR), ricerca per username, richieste, la lega con il podio, la
 * classifica per esercizio e le novità. Senza account yG mostra l'invito a collegarsi. `onOpenFriend(username)` apre il profilo.
 */
@Composable
fun FriendsScreen(onOpenFriend: (String) -> Unit, social: SocialStore = rememberSocialStore()) {
    val accent = LocalAccent.current
    val repo = LocalRepository.current
    val exercises by repo.exercises.collectAsState(emptyList())
    val records by repo.records.collectAsState(emptyList())
    val available by social.available.collectAsState()
    val lists by social.friends.collectAsState()
    val friendCount = lists?.friends?.size ?: 0
    val linkedMine = remember(exercises, records) { FriendsLogic.linkedMine(exercises, records) }

    var showingInvite by remember { mutableStateOf(false) }
    var showingConnect by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf("") }
    val current = pick.ifEmpty { linkedMine.firstOrNull()?.catalogID ?: "" }
    var board by remember { mutableStateOf<List<BoardRow>?>(null) }

    LaunchedEffect(Unit) { social.refresh() }
    // la classifica del macchinario scelto: cambiando macchinario o numero di amici si rilegge, e nel frattempo gira la rotella
    LaunchedEffect(current, friendCount) {
        board = null
        if (current.isEmpty() || friendCount == 0) return@LaunchedEffect
        val api = social.client() ?: return@LaunchedEffect
        board = try { api.board(current) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(accent))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(tr("Amici"), Modifier.padding(top = 8.dp), style = PeakTheme.display(34), color = PeakTheme.textPrimary)
            if (!available) {
                ConnectCard(onConnect = { showingConnect = true }, Modifier.appear(0))
            } else {
                InviteBlock(social, onInvite = { showingInvite = true }, Modifier.appear(0))
                Requests(social, Modifier.appear(1))
                if (friendCount == 0) EmptyState(Modifier.appear(2)) else {
                    League(social, onOpenFriend, Modifier.appear(2))
                    ByExercise(linkedMine.map { it.catalogID to (Catalog.entry(it.catalogID)?.name(Lang.isItalian()) ?: it.name) }, current, { pick = it }, board, Modifier.appear(3))
                    News(social, onOpenFriend, Modifier.appear(4))
                }
            }
        }
    }
    if (showingInvite) InviteSheet(social, onDismiss = { showingInvite = false })
    if (showingConnect) ConnectSheet(onDismiss = { showingConnect = false })
}

// ---- collegamento e inviti -----------------------------------------------------------------

@Composable
private fun ConnectCard(onConnect: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(PeakIcons.friends, null, Modifier.size(40.dp), tint = LocalAccent.current)
            Text(tr("Gli amici servono l'account yG"), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(
                tr("Collega Peak a yG: potrai invitare gli amici, vedere i loro massimali e fare la classifica."),
                style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center,
            )
            IconPrimaryButton(FriendsIcons.connect, "Collega a yG", onConnect)
        }
    }
}

@Composable
private fun InviteBlock(social: SocialStore, onInvite: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val haptics = LocalApp.current.haptics
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Pair<FriendPerson, FriendState>>>(emptyList()) }
    var searched by remember { mutableStateOf("") }

    // ogni cambio del testo annulla la ricerca di prima: così dopo 300 ms parte solo l'ultima
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.replace("@", "").length < 2) { found = emptyList(); searched = ""; return@LaunchedEffect }
        delay(300)
        val api = social.client() ?: return@LaunchedEffect
        found = try { api.searchUsers(q) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        searched = q
    }

    fun ask(person: FriendPerson) = scope.launch {
        val api = social.client() ?: return@launch
        val state = try { api.requestFriend(person.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { return@launch }
        found = found.map { if (it.first.id == person.id) it.first to state else it }
        haptics.success()
        social.reload()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconPrimaryButton(FriendsIcons.qr, "Invita un amico", onInvite, Modifier.tourTarget("invite"))
        Text(tr("Il link vale una volta sola: chi lo apre diventa tuo amico subito."), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)

        Row(Modifier.fillMaxWidth().glassSurface(14.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PeakIcons.search, null, Modifier.size(20.dp), tint = PeakTheme.textTertiary)
            Box(Modifier.weight(1f).padding(start = 8.dp)) {
                if (query.isEmpty()) Text(tr("Cerca un username"), style = PeakTheme.rounded(16, FontWeight.Normal), color = PeakTheme.textTertiary)
                BasicTextField(
                    query, { query = it }, singleLine = true, cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    textStyle = PeakTheme.rounded(16, FontWeight.Normal).copy(color = PeakTheme.textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (searched.isNotEmpty() && query.isNotEmpty()) {
            if (found.isEmpty()) {
                Text(tr("Nessun utente con questo username."), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary)
            } else {
                GlassCard(padding = 6.dp) {
                    for ((person, state) in found) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("@${person.username}", Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
                            when (state) {
                                FriendState.none -> TextAction("Aggiungi") { ask(person) }
                                FriendState.received -> TextAction("Accetta") { ask(person) }
                                FriendState.friends -> Text(tr("Amici"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                                FriendState.sent -> Text(tr("Richiesta inviata"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Un'azione in testo colorato dall'accento (`Button("Aggiungi")` con `.foregroundStyle(accent)`). */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(tr(text), Modifier.pressable(onClick = onClick).padding(vertical = 4.dp), style = PeakTheme.rounded(13, FontWeight.Bold), color = LocalAccent.current)
}

@Composable
private fun Requests(social: SocialStore, modifier: Modifier = Modifier) {
    val lists by social.friends.collectAsState()
    val received = lists?.received.orEmpty()
    if (received.isEmpty()) return
    val scope = rememberCoroutineScope()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Richieste", Modifier.padding(top = 6.dp))
        GlassCard(padding = 6.dp) {
            for (person in received) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("@${person.username}", Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
                    TextAction("Accetta") {
                        scope.launch {
                            try { social.client()?.acceptFriend(person.id) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                            social.reload()
                        }
                    }
                    Icon(
                        PeakIcons.close, null,
                        Modifier.padding(start = 8.dp).size(20.dp).pressable {
                            scope.launch {
                                try { social.client()?.removeFriend(person.id) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                                social.reload()
                            }
                        },
                        tint = PeakTheme.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(84.dp).background(accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(PeakIcons.friends, null, Modifier.size(38.dp), tint = accent)
            }
            Text(tr("Allenarsi è più bello insieme"), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(
                tr("Invita un amico con il link o il QR, oppure cerca il suo username. Vedrete i massimali e le schede l'uno dell'altro, e potrete fare la classifica."),
                style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center,
            )
        }
    }
}

// ---- la lega -------------------------------------------------------------------------------

/** Un pulsante a capsula per scegliere fra le voci di una fila (`Metric`, gli esercizi): scuro sull'accento se scelto. */
@Composable
private fun PickChip(text: String, selected: Boolean, onClick: () -> Unit, verbatim: Boolean = false) {
    val accent = LocalAccent.current
    Text(
        if (verbatim) text else tr(text),
        Modifier.background(if (selected) accent else PeakTheme.card, CircleShape).pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        style = PeakTheme.rounded(13, FontWeight.SemiBold), color = if (selected) Color.Black.copy(alpha = 0.85f) else PeakTheme.textSecondary, maxLines = 1,
    )
}

@Composable
private fun League(social: SocialStore, onOpenFriend: (String) -> Unit, modifier: Modifier = Modifier) {
    val rows by social.league.collectAsState()
    val lists by social.friends.collectAsState()
    val accent = LocalAccent.current
    val unit = LocalAppSettings.current.unit
    var metric by remember { mutableStateOf(LeagueMetric.volume) }
    val all = rows ?: return
    val ranked = FriendsLogic.ranked(all, metric)
    val top = maxOf(1.0, ranked.maxOfOrNull { metric.value(it) } ?: 1.0)
    val mine = FriendsLogic.myPlace(ranked)
    val today = tr("Oggi")
    val yesterday = tr("Ieri")
    val never = tr("Mai allenato")
    val you = tr("Tu")
    fun text(r: com.gabrieleforghieri.peak.ui.friends.social.LeagueRow) = if (metric == LeagueMetric.volume) Fmt.volume(r.volume30, unit) else metric.value(r).toInt().toString()
    fun open(id: Int) { lists?.friends?.firstOrNull { it.id == id }?.let { onOpenFriend(it.username) } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("La lega", Modifier.padding(top = 6.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in LeagueMetric.entries) PickChip(m.title, metric == m, { metric = m })
                }
                Podium(
                    ranked.take(3).map { PodiumEntry(it.id, if (it.isMe) you else "@${it.username}", it.username, text(it), metric.value(it), it.isMe) },
                    onPick = ::open,
                )
                if (mine > 0) {
                    Text(
                        if (mine == 1) tr("Sei in testa. Tienila stretta.") else tr("Sei %lld° su %lld.", mine, ranked.size),
                        Modifier.fillMaxWidth(), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center,
                    )
                }
            }
        }
        GlassCard(padding = 6.dp) {
            ranked.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (r.isMe) accent.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(12.dp))
                        .pressable { if (!r.isMe) open(r.id) }.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${i + 1}", Modifier.width(22.dp), style = PeakTheme.rounded(14, FontWeight.ExtraBold), color = if (r.isMe) accent else PeakTheme.textTertiary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (r.isMe) you else "@${r.username}", Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text(r), style = PeakTheme.tabular(15, FontWeight.ExtraBold), color = PeakTheme.textPrimary)
                        }
                        ProgressBar(FriendsLogic.barFraction(metric.value(r), top), if (r.isMe) accent else Color.White.copy(alpha = 0.35f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(FriendsIcons.flame, null, Modifier.size(10.dp), tint = PeakTheme.textTertiary)
                            Text(
                                "${r.streak} · ${r.last?.let { FriendsLogic.relativeDay(it, today, yesterday) } ?: never}",
                                style = PeakTheme.rounded(10, FontWeight.Medium), color = PeakTheme.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- esercizio per esercizio ---------------------------------------------------------------

/** `linked`: i tuoi macchinari collegati al catalogo, come (voce del catalogo, nome). `board` è `null` finché si carica. */
@Composable
private fun ByExercise(linked: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, board: List<BoardRow>?, modifier: Modifier = Modifier) {
    if (linked.isEmpty()) return
    val accent = LocalAccent.current
    val unit = LocalAppSettings.current.unit
    val you = tr("Tu")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Esercizio per esercizio", Modifier.padding(top = 6.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((id, name) in linked) PickChip(name, current == id, { onPick(id) }, verbatim = true)
                }
                when {
                    board == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = accent, strokeWidth = 2.dp)
                    }
                    board.size < 2 -> Text(tr("Nessun amico ha ancora questo esercizio."), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary)
                    else -> {
                        val top = maxOf(1.0, board.maxOf { it.oneRm })
                        board.forEachIndexed { i, r ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}", Modifier.width(22.dp), style = PeakTheme.rounded(14, FontWeight.ExtraBold), color = if (i == 0) PeakTheme.gold else PeakTheme.textTertiary)
                                    Text(
                                        if (r.isMe) you else "@${r.username}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = PeakTheme.rounded(14, if (r.isMe) FontWeight.ExtraBold else FontWeight.SemiBold), color = PeakTheme.textPrimary,
                                    )
                                    Text(Fmt.weight(r.oneRm, unit), style = PeakTheme.tabular(14, FontWeight.ExtraBold), color = PeakTheme.textPrimary)
                                    Text(Fmt.setLine(r.weight, r.reps, unit), Modifier.padding(start = 8.dp), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                                }
                                ProgressBar(FriendsLogic.barFraction(r.oneRm, top), if (r.isMe) accent else Color.White.copy(alpha = 0.35f))
                            }
                        }
                        Text(tr("1RM stimato dalle serie migliori."), style = PeakTheme.rounded(10, FontWeight.Medium), color = PeakTheme.textTertiary)
                    }
                }
            }
        }
    }
}

// ---- novità --------------------------------------------------------------------------------

@Composable
private fun News(social: SocialStore, onOpenFriend: (String) -> Unit, modifier: Modifier = Modifier) {
    val list by social.news.collectAsState()
    val lists by social.friends.collectAsState()
    val unit = LocalAppSettings.current.unit
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Novità", Modifier.padding(top = 6.dp))
        val events = list.orEmpty().take(6)
        if (events.isNotEmpty()) {
            GlassCard(padding = 6.dp) {
                events.forEachIndexed { i, e ->
                    NewsRow(e, unit, Modifier.pressable { lists?.friends?.firstOrNull { it.id == e.friendID }?.let { onOpenFriend(it.username) } })
                    if (i < events.size - 1) HorizontalDivider(color = PeakTheme.stroke)
                }
            }
        } else {
            Note(tr("Nessun record dei tuoi amici negli ultimi giorni."))
        }
    }
}
