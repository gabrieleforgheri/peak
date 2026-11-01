package com.gabrieleforghieri.peak.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.SecondaryButton
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.SheetDetent
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.ui.friends.social.FriendProfileData
import com.gabrieleforghieri.peak.ui.friends.social.SharedMax
import com.gabrieleforghieri.peak.ui.friends.social.SharedTemplate
import com.gabrieleforghieri.peak.ui.friends.social.SocialStore
import com.gabrieleforghieri.peak.ui.friends.social.rememberSocialStore
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Il profilo di un amico (`Features/Friends/FriendProfileView.swift`): i suoi massimali (confrontati ai tuoi) e le sue schede, che si
 * copiano fra le tue. La rotta porta solo lo `username`: l'id si trova fra i tuoi amici (e, se l'elenco non è ancora arrivato, lo si
 * rilegge dal server).
 */
@Composable
fun FriendProfileScreen(username: String, onBack: () -> Unit, social: SocialStore = rememberSocialStore()) {
    val repo = LocalRepository.current
    val exercises by repo.exercises.collectAsState(emptyList())
    val records by repo.records.collectAsState(emptyList())
    val sets by repo.sets.collectAsState(emptyList())
    val mine = remember(exercises, records, sets) { FriendsLogic.myBestByCatalog(exercises, records, sets) }

    var personId by remember(username) { mutableStateOf<Int?>(null) }
    var profile by remember(username) { mutableStateOf<FriendProfileData?>(null) }
    var failed by remember(username) { mutableStateOf(false) }
    var copiedMessage by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(username) {
        failed = false
        val api = social.client()
        if (api == null) { failed = true; return@LaunchedEffect }
        fun find() = social.friends.value?.friends?.firstOrNull { it.username == username }
        var person = find()
        if (person == null) { social.refresh(); person = find() }
        if (person == null) { failed = true; return@LaunchedEffect }
        personId = person.id
        try { profile = api.profile(person.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
    }

    val app = LocalApp.current
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val unit = settings.unit
    val accent = LocalAccent.current

    fun copy(shared: SharedTemplate) = scope.launch {
        val plan = FriendsLogic.planCopy(
            shared, repo.exercises.first(), repo.templates.first(), repo.programs.first(),
            settings.activeProgram, Lang.isItalian(), System.currentTimeMillis(),
        )
        app.database.areaFriends().addSharedDay(plan.newExercises, plan.newProgram, plan.template)
        app.haptics.success()
        copiedMessage = context.tr("Giorno copiato fra i tuoi.")
    }

    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(accent))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar("@$username", onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val p = profile
                when {
                    p != null -> {
                        SectionHeader("Massimali")
                        if (p.maxes.isEmpty()) {
                            Note(tr("Ancora nessun massimale."), Modifier.padding(vertical = 14.dp))
                        } else {
                            GlassCard(Modifier.appear(0), padding = 6.dp) {
                                p.maxes.forEachIndexed { i, m ->
                                    MaxRow(m, mine[m.catalog], unit)
                                    if (i < p.maxes.size - 1) HorizontalDivider(color = PeakTheme.stroke)
                                }
                            }
                        }

                        SectionHeader("Schede", Modifier.padding(top = 8.dp))
                        if (p.templates.isEmpty()) {
                            Note(tr("Nessuna scheda condivisa."), Modifier.padding(vertical = 14.dp))
                        } else {
                            p.templates.forEachIndexed { i, t -> TemplateCard(t, { copy(t) }, Modifier.appear(i + 1)) }
                        }

                        Box(Modifier.padding(top = 14.dp)) {
                            IconSecondaryButton(FriendsIcons.removeFriend, "Togli dagli amici", { confirmRemove = true }, tint = PeakTheme.negative)
                        }
                    }
                    failed -> Note(tr("Non riesco a caricare questo profilo."), Modifier.padding(vertical = 14.dp))
                    else -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
                }
            }
        }
    }
    copiedMessage?.let { MessageSheet(it, onDismiss = { copiedMessage = null }) }
    if (confirmRemove) {
        RemoveFriendSheet(
            username, onDismiss = { confirmRemove = false },
            onConfirm = {
                // dopo la rimozione si torna all'elenco, che si rilegge
                scope.launch {
                    val id = personId
                    if (id != null) {
                        try { social.client()?.removeFriend(id) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                        social.reload()
                    }
                    onBack()
                }
            },
        )
    }
}

/** La barra del profilo: indietro e il titolo `@username` (`navigationBarTitleDisplayMode(.inline)`). */
@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(FriendsIcons.back, tr("Indietro"), Modifier.size(40.dp).pressable(onClick = onBack).padding(8.dp), tint = LocalAccent.current)
        Text(title, Modifier.weight(1f), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.size(40.dp)) // lo spazio del tasto indietro, perché il titolo stia al centro
    }
}

/** Un massimale dell'amico, con quanto sta sopra o sotto il tuo (`maxRow`). `mine` = il tuo miglior 1RM per la stessa voce del catalogo. */
@Composable
private fun MaxRow(m: SharedMax, mine: Double?, unit: com.gabrieleforghieri.peak.domain.WeightUnit) {
    val italian = Lang.isItalian()
    val entry = Catalog.entry(m.catalog)
    val diff = if (m.catalog.isEmpty()) null else FriendsLogic.percentAbove(m.oneRm, mine)
    val shown = entry?.name(italian) ?: m.name
    val today = tr("Oggi")
    val yesterday = tr("Ieri")
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background((entry?.muscleGroup ?: MuscleGroup.core).tint, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(shown, maxLines = 1, overflow = TextOverflow.Ellipsis, style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(
                (if (entry != null && shown != m.name) "${m.name} · " else "") + FriendsLogic.relativeDay(m.date, today, yesterday),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(Fmt.setLine(m.weight, m.reps, unit), style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary)
            if (diff != null && diff != 0) {
                Text(
                    if (diff > 0) tr("%lld%% sopra di te", diff) else tr("%lld%% sotto di te", -diff),
                    style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                )
            }
        }
    }
}

/** Un giorno di scheda dell'amico, con il pulsante per copiarlo fra i tuoi (`templateCard`). */
@Composable
private fun TemplateCard(t: SharedTemplate, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val italian = Lang.isItalian()
    GlassCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, Modifier.weight(1f), style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.textPrimary)
                Text(
                    if (t.rows.size == 1) tr("1 esercizio") else tr("%lld esercizi", t.rows.size),
                    style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                )
            }
            for (r in t.rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Catalog.entry(r.catalog)?.name(italian) ?: r.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textPrimary)
                    Text(FriendsLogic.target(r), Modifier.padding(start = 8.dp), style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textSecondary)
                }
            }
            SecondaryButton(tr("Copia nelle mie schede"), onCopy)
        }
    }
}

/** Il messaggio con un solo «Ok» (l'`alert` di SwiftUI). `text` è già tradotto. */
@Composable
fun MessageSheet(text: String, onDismiss: () -> Unit) {
    PeakSheet(onDismiss, detent = SheetDetent.Height(190.dp)) { close ->
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
            PrimaryButton(tr("Ok"), close)
        }
    }
}

/**
 * «Togliere @utente dagli amici?» (`confirmationDialog`). Come `ConfirmSheet`, ma con il nome dentro al titolo: `ConfirmSheet` traduce il
 * titolo senza argomenti, qui serve `tr("Togliere @%@ dagli amici?", utente)`. L'azione parte a foglio chiuso.
 */
@Composable
fun RemoveFriendSheet(username: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    PeakSheet(onDismiss = { onDismiss(); if (confirmed) onConfirm() }, detent = SheetDetent.Height(210.dp)) { close ->
        RemoveFriendContent(username, onConfirm = { confirmed = true; close() }, onCancel = close)
    }
}

@Composable
fun RemoveFriendContent(username: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tr("Togliere @%@ dagli amici?", username), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(tr("Togli"), onConfirm, tint = PeakTheme.negative)
            SecondaryButton(tr("Annulla"), onCancel)
        }
    }
}
