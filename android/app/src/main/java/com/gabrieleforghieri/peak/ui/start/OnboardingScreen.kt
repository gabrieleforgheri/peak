package com.gabrieleforghieri.peak.ui.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.MachineGlyphView
import com.gabrieleforghieri.peak.design.Motion
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.enter
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.CatalogEntry
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.Onboarding
import com.gabrieleforghieri.peak.ui.machines.CatalogPickerSheet
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.util.SoundFX
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Il primo avvio di un'app vuota, a tutto schermo e senza scorciatoie (`OnboardingView.swift`): benvenuto, quanti giorni, quali gruppi,
 * quali macchinari, scheda pronta. I dati si creano entrando nell'ultima schermata (e si tolgono se torni indietro); finisce con
 * `onFinish`, che fa partire il tutorial. `state` sta fuori solo perché le prove possano partire da un passo qualunque.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier, state: OnboardingState = remember { OnboardingState() }) {
    val app = LocalApp.current
    val settings = LocalAppSettings.current
    val update = rememberUpdateSettings()
    val reduce = LocalReduceMotion.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val actions = remember(app) { StartActions(app.database) }
    var pending by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // il benvenuto: titolo, riga e pulsante arrivano a salita finita
    LaunchedEffect(Unit) {
        if (reduce) { state.late = 3; return@LaunchedEffect }
        if (state.late >= 3) return@LaunchedEffect
        delay(2500); state.late = 1
        delay(150); state.late = 2
        delay(200); state.late = 3
    }

    fun go(to: Int) {
        if (state.busy) return
        state.busy = true
        if (state.step == 0) { state.heroSeen = true; state.late = 3 }
        state.dir = if (to > state.step) 1 else -1
        state.step = to
        scope.launch { delay(if (reduce) 170 else 360); state.busy = false }
    }

    fun back() {
        pending?.cancel()
        val plan = state.plan
        if (state.step == state.last && plan != null) {
            state.plan = null
            state.composed = false
            scope.launch {
                actions.undoPlan(plan)
                update { copy(activeProgram = if (activeProgram.uppercase() == plan.programUuid) "" else activeProgram) }
            }
        }
        app.haptics.tap()
        go(state.step - 1)
    }

    /** Il collegamento a yG, se ce n'è già uno: con dati sul server il primo avvio è finito, altrimenti si parte da zero. */
    fun connected() {
        state.connecting = false
        scope.launch {
            if (actions.hasData()) onFinish() else { state.goal = settings.weeklyGoal; go(1) }
        }
    }

    fun toReady() {
        val days = state.days { app.tr(it) }
        scope.launch {
            val plan = actions.createPlan(app.tr("La mia scheda"), days, settings)
            state.plan = plan
            state.made = days.map { MadeDay(it.group, it.name, it.machines.size) }
            update { copy(activeProgram = plan.programUuid) }
            app.sounds.play(SoundFX.Effect.goal)
            app.haptics.success()
            go(state.last)
        }
    }

    BackHandler(enabled = state.connecting || state.step > 0) { if (state.connecting) state.connecting = false else back() }

    BoxWithConstraints(modifier.fillMaxSize().then(PeakTheme.backgroundModifier(accent)).statusBarsPadding().navigationBarsPadding()) {
        val width = maxWidth
        val height = maxHeight
        val colW = minOf(width - 48.dp, 460.dp)
        if (state.connecting) {
            ConnectFlowContent(onFinish = ::connected, onCancel = { state.connecting = false }, firstRun = true, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize()) {
                Header(state, ::back)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    val density = LocalDensity.current
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = {
                            val shift = with(density) { 36.dp.roundToPx() } * state.dir
                            if (reduce) fadeIn(androidx.compose.animation.core.tween(160)) togetherWith fadeOut(androidx.compose.animation.core.tween(160))
                            else (slideInHorizontally(Motion.out(350)) { shift } + fadeIn(Motion.out(350))) togetherWith
                                (slideOutHorizontally(Motion.out(350)) { -shift } + fadeOut(Motion.out(350)))
                        },
                        label = "onboarding-step",
                    ) { step ->
                        val pane = Pane(width, height, colW)
                        when (step) {
                            0 -> pane.Welcome(state, onStart = { app.haptics.tap(); go(1) }, onHaveAccount = { state.connecting = true })
                            1 -> pane.Days(state, selected = state.goal) { n ->
                                state.goal = n
                                update { copy(weeklyGoal = n) }
                                app.sounds.playDay(n - 1)
                                app.haptics.tap()
                                pending?.cancel()
                                pending = scope.launch { delay(250); go(2) }
                            }
                            2 -> pane.Groups(state, onContinue = { state.seedMachines(); go(3) })
                            3 -> pane.Machines(state, onContinue = ::toReady)
                            else -> pane.Ready(state, onFinish = { app.haptics.tap(); onFinish() })
                        }
                    }
                }
            }
        }
    }

    if (state.searching) CatalogPickerSheet(
        onPick = { entry, _ -> entry?.let(state::add) },
        onDismiss = { state.searching = false },
        title = "Cerca nel catalogo", allowsCustom = false,
    )
}

// MARK: - Intestazione e passaggi

@Composable
private fun Header(state: OnboardingState, onBack: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val accent = LocalAccent.current
    val fill by animateFloatAsState(state.step.toFloat() / state.last, if (reduce) androidx.compose.animation.core.snap() else Motion.out(350), label = "progress")
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp).alpha(if (state.step == 0) 0f else 1f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.offset(x = (-10).dp).size(44.dp).then(if (state.step == 0) Modifier else Modifier.pressable(onClick = onBack)),
            contentAlignment = Alignment.Center,
        ) { Icon(StartIcons.chevronLeft, tr("Indietro"), Modifier.size(26.dp), tint = PeakTheme.textSecondary) }
        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(fill).fillMaxSize().clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(22.dp))
    }
}

/** Le misure di questo schermo, passate ai cinque passi. */
private class Pane(val width: Dp, val height: Dp, val colW: Dp)

/** Il corpo scorre se non ci sta (schermi piccoli, testi grandi); `centered` lo mette a metà altezza come `ob-mid`. */
@Composable
private fun ColumnScope.Scrolling(pane: Pane, centered: Boolean = true, hero: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = (if (hero) minOf(pane.width - 48.dp, 640.dp) else pane.colW) + 48.dp).fillMaxWidth()
                .heightIn(min = maxOf(0.dp, pane.height - 56.dp - 100.dp))
                .padding(top = 12.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = if (centered) Arrangement.Center else Arrangement.Top,
            horizontalAlignment = if (hero) Alignment.CenterHorizontally else Alignment.Start,
            content = content,
        )
    }
}

@Composable
private fun Foot(title: String, pane: Pane, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        PrimaryButton(tr(title), { if (enabled) onClick() }, modifier.widthIn(max = pane.colW).alpha(if (enabled) 1f else 0.4f))
    }
}

// 1. il benvenuto: la montagna che si disegna
@Composable
private fun Pane.Welcome(state: OnboardingState, onStart: () -> Unit, onHaveAccount: () -> Unit) {
    val word = minOf(60f, maxOf(44f, width.value * 0.13f))
    Column(Modifier.fillMaxSize()) {
        Scrolling(this@Welcome, hero = true) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val heroW = minOf(width - 48.dp, 640.dp, minOf(height * 0.5f, 460.dp) * (360f / 260f))
                SummitDraw(Modifier.width(heroW), still = state.heroSeen)
                Column(Modifier.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Peak", Modifier.rise(state.late >= 1),
                        style = PeakTheme.rounded(word.toInt(), FontWeight.Black).copy(fontSize = word.sp, letterSpacing = (-0.04f * word).sp), color = PeakTheme.textPrimary,
                    )
                    Text(
                        tr("Ogni allenamento, un po' più in alto."), Modifier.rise(state.late >= 2),
                        style = PeakTheme.rounded(18, FontWeight.Normal), color = PeakTheme.textSecondary, textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(Modifier.padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Foot("Iniziamo", this@Welcome, modifier = Modifier.rise(state.late >= 3), onClick = onStart)
            // chi ha già un account yG lo ritrova qui: niente scelte sui dati, si prende quello che c'è sul server
            val show by animateFloatAsState(if (state.late >= 3) 1f else 0f, Motion.out(400), label = "have-account")
            Text(
                tr("Ho già un account yG"),
                Modifier.alpha(show).then(if (state.late >= 3) Modifier.pressable(onClick = onHaveAccount) else Modifier).heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                style = PeakTheme.rounded(14, FontWeight.SemiBold), color = PeakTheme.textTertiary,
            )
        }
    }
}

// 2. i giorni: si tocca un numero e dopo un attimo si va avanti
@Composable
private fun Pane.Days(state: OnboardingState, selected: Int, onPick: (Int) -> Unit) {
    val side = (colW - 30.dp) / 4
    Column(Modifier.fillMaxSize()) {
        Scrolling(this@Days) {
            StartTitle(tr("Quante volte ti alleni?"), width.value)
            StartSub(tr("a settimana"))
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { for (n in 1..4) NumberTile(n, side, n == selected) { onPick(n) } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { for (n in 5..7) NumberTile(n, side, n == selected) { onPick(n) } }
            }
        }
    }
}

@Composable
private fun NumberTile(n: Int, side: Dp, isOn: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.size(side)
            .then(if (isOn) Modifier.shadow(14.dp, shape, ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.35f)) else Modifier)
            .background(if (isOn) accent else PeakTheme.card, shape)
            .border(2.dp, if (isOn) accent else PeakTheme.strokeStrong, shape)
            .semantics { selected = isOn }
            .pressable(0.93f, onClick),
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = PeakTheme.tabular(34, FontWeight.Black), color = if (isOn) PeakTheme.bg else PeakTheme.textPrimary) }
}

// 3. i gruppi
@Composable
private fun Pane.Groups(state: OnboardingState, onContinue: () -> Unit) {
    val tap = rememberTap()
    Column(Modifier.fillMaxSize()) {
        Scrolling(this@Groups) {
            StartTitle(tr("Cosa alleni?"), width.value)
            StartSub(tr("Un giorno per ogni gruppo."))
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in MuscleGroup.entries.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (g in row) GroupTile(g, g in state.groups, Modifier.weight(1f)) { tap(); state.toggleGroup(g) }
                    }
                }
            }
        }
        Foot("Continua", this@Groups, enabled = state.groups.isNotEmpty(), onClick = onContinue)
    }
}

@Composable
private fun GroupTile(g: MuscleGroup, isOn: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.heightIn(min = 104.dp)
            .background(if (isOn) g.tint.copy(alpha = 0.16f) else PeakTheme.card, shape)
            .border(2.dp, if (isOn) g.tint else PeakTheme.strokeStrong, shape)
            .semantics { selected = isOn }
            .pressable(0.96f, onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) { Icon(PeakIcons.group(g), null, Modifier.size(30.dp), tint = g.tint) }
            Text(tr(g.title), style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1, overflow = TextOverflow.Clip)
        }
        SelectCheck(isOn, g.tint, Modifier.align(Alignment.TopEnd).padding(2.dp))
    }
}

// 4. i macchinari
@Composable
private fun Pane.Machines(state: OnboardingState, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Scrolling(this@Machines, centered = false) {
            StartTitle(tr("I tuoi macchinari"), width.value)
            StartSub(tr("Tocca quelli che hai in palestra."))
            if (Catalog.entries.isEmpty()) {
                Text(
                    tr("Il catalogo non si carica: serve la connessione."),
                    Modifier.padding(top = 16.dp).fillMaxWidth().background(Color(0xFFFF9500).copy(alpha = 0.16f), RoundedCornerShape(12.dp)).padding(12.dp),
                    style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textPrimary,
                )
            }
            for (g in state.picked) MachineSection(g, state, colW)
            SecondaryIconButton(tr("Cerca nel catalogo"), PeakIcons.search, { state.searching = true }, Modifier.padding(top = 22.dp))
        }
        Foot("Continua", this@Machines, enabled = state.total > 0, onClick = onContinue)
    }
}

@Composable
private fun MachineSection(g: MuscleGroup, state: OnboardingState, colW: Dp) {
    val accent = LocalAccent.current
    val tap = rememberTap()
    Column(Modifier.fillMaxWidth().padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(g.tint, CircleShape))
            Text(
                tr(g.title).uppercase(), Modifier.weight(1f),
                style = PeakTheme.rounded(13, FontWeight.ExtraBold).copy(letterSpacing = 1.sp), color = g.tint,
            )
            Text("${state.chosen(g).size + state.extrasOf(g).size}", style = PeakTheme.tabular(13, FontWeight.ExtraBold), color = PeakTheme.textTertiary)
        }
        // la griglia adattiva di SwiftUI: tante colonne quante ne stanno a 130 dp l'una
        val columns = maxOf(1, ((colW.value + 10) / 140).toInt())
        val tiles: List<Pair<CatalogEntry, Boolean>> =
            Onboarding.suggested[g].orEmpty().mapNotNull { id -> Catalog.entry(id)?.let { it to (id in state.on) } } + state.extrasOf(g).map { it to true }
        for (row in tiles.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((e, isOn) in row) MachineTile(e, isOn, accent, Modifier.weight(1f)) {
                    tap()
                    if (e in state.extras) state.removeExtra(e.id) else state.toggleMachine(e.id)
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MachineTile(e: CatalogEntry, isOn: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val italian = com.gabrieleforghieri.peak.util.Lang.isItalian()
    Box(
        modifier.heightIn(min = 118.dp)
            .background(if (isOn) accent.copy(alpha = 0.1f) else PeakTheme.card, shape)
            .border(2.dp, if (isOn) accent else PeakTheme.strokeStrong, shape)
            .semantics { selected = isOn }
            .pressable(0.97f, onClick)
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(e.muscleGroup.tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                MachineGlyphView(e.machineGlyph, tint = e.muscleGroup.tint, size = (56 * 0.64f).dp)
            }
            Text(e.name(italian), style = PeakTheme.rounded(13, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        SelectCheck(isOn, accent, Modifier.align(Alignment.TopEnd))
    }
}

// 5. i dati: una scheda con un giorno per gruppo, nell'ordine di sempre
@Composable
private fun Pane.Ready(state: OnboardingState, onFinish: () -> Unit) {
    LaunchedEffect(Unit) { state.composed = true }
    Column(Modifier.fillMaxSize()) {
        Scrolling(this@Ready) {
            StartTitle(tr("Tutto pronto"), width.value)
            StartSub(tr("Ecco la tua scheda."))
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.made.forEachIndexed { i, d -> DayRow(d, i, state.composed) }
            }
        }
        Foot("Fai il giro", this@Ready, onClick = onFinish)
    }
}

/** I giorni si compongono uno dopo l'altro. */
@Composable
private fun DayRow(d: MadeDay, i: Int, composed: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).enter(composed, 16f, 280 + i * 80)
            .background(PeakTheme.card, shape).background(d.group.tint.copy(alpha = 0.1f), shape)
            .border(1.dp, d.group.tint.copy(alpha = 0.4f), shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).background(d.group.tint.copy(alpha = 0.2f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(PeakIcons.group(d.group), null, Modifier.size(22.dp), tint = d.group.tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(d.name, style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (d.count == 1) tr("1 macchinario") else tr("%lld macchinari", d.count),
                style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary,
            )
        }
    }
}
