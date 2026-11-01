package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.bounce
import com.gabrieleforghieri.peak.design.glassSurface
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.domain.Motivation
import com.gabrieleforghieri.peak.domain.Stats
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.Tab
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.SoundFX
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * L'eroe di Allenamento: la montagna e una frase, il saluto, l'obiettivo della settimana e le settimane di fila (`WorkoutHero.swift`).
 * Quello che mostra parte da quanto è già stato festeggiato (`Celebrated`) e sale al valore vero se c'è una novità (un allenamento registrato
 * dal web, arrivato con la sincronizzazione); se non c'è niente di nuovo coincide con quello vero. `celebrated == null`: niente festa.
 */
@Composable
fun WorkoutHero(
    name: String,
    streak: Int,
    weekSessions: Int,
    goal: Int,
    onAvatar: () -> Unit,
    onChangeGoal: () -> Unit,
    modifier: Modifier = Modifier,
    celebrated: Celebrated? = null,
    feedback: WorkoutFeedback = WorkoutFeedback.None,
    hour: Int = LocalTime.now().hour,
) {
    val accent = LocalAccent.current
    val reduce = LocalReduceMotion.current
    val nav = LocalPeakNav.current
    val tab by nav.tab.collectAsState()
    val resumed = rememberResumed()
    val weekStart = Stats.weekStart(System.currentTimeMillis())

    var index by remember { mutableIntStateOf(Motivation.startIndex()) }
    var shownWeek by remember { mutableIntStateOf(minOf(weekSessions, celebrated?.week(weekStart) ?: weekSessions)) }
    var shownStreak by remember { mutableIntStateOf(minOf(streak, celebrated?.streak ?: streak)) }
    var flameTick by remember { mutableIntStateOf(0) }

    // le frasi girano da sole ogni 7 secondi (non con «Riduci movimento»)
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        while (true) { delay(7000); index = (index + 1) % Motivation.italian.size }
    }

    // se i numeri sono saliti mentre la scheda Allenamento non si vedeva e il riepilogo li ha già festeggiati, la barra li segue senza festa
    LaunchedEffect(weekSessions, streak, tab) {
        if ((celebrated?.week(weekStart) ?: 0) >= weekSessions) shownWeek = weekSessions
        if ((celebrated?.streak ?: 0) >= streak) shownStreak = streak
    }

    // festeggia solo ciò che non è ancora stato festeggiato e solo quando la scheda Allenamento si vede (le altre schede restano vive sotto).
    // Una novità non ancora vista: la barra si riempie dal vecchio al nuovo valore (una nota per segmento, più alta a ogni giorno),
    // `Obiettivo raggiunto` se l'ha appena raggiunto, e la fiamma si scuote mentre la serie sale. Prima volta in assoluto, nessun aumento
    // o calo: si allinea e basta. I valori nuovi si salvano subito, così non si ripete.
    LaunchedEffect(weekSessions, streak, resumed, tab == Tab.workouts) {
        if (celebrated == null) { shownWeek = weekSessions; shownStreak = streak; return@LaunchedEffect }
        // fuori vista non si fa e non si salva nulla: la festa tocca quando la scheda Allenamento si guarda davvero
        if (!resumed || tab != Tab.workouts) return@LaunchedEffect
        val seenWeek = celebrated.week(weekStart)
        val seenStreak = celebrated.streak
        celebrated.store(weekSessions, weekStart)
        celebrated.streak = streak

        val weekUp = seenWeek?.let { weekSessions > it } ?: false
        val streakUp = seenStreak?.let { streak > it } ?: false
        if (!weekUp && !streakUp) { shownWeek = weekSessions; shownStreak = streak; return@LaunchedEffect }
        try {
            if (seenWeek != null && weekUp) {
                shownWeek = seenWeek
                delay(400) // il tempo di vedere la scheda Allenamento, poi parte
                val segments = minOf(weekSessions, goal) // la barra ha un segmento per allenamento, fino all'obiettivo
                for (k in seenWeek until segments) {
                    shownWeek = k + 1
                    feedback.day(k)
                    feedback.tap()
                    delay(140)
                }
                shownWeek = weekSessions
                if (weekSessions >= goal && seenWeek < goal) {
                    delay(150)
                    feedback.sound(SoundFX.Effect.goal)
                    feedback.success()
                    delay(500)
                }
            } else {
                shownWeek = weekSessions
                delay(400)
            }
            if (streakUp) {
                flameTick += 1
                shownStreak = streak
                feedback.sound(SoundFX.Effect.streak)
                feedback.commit()
            } else shownStreak = streak
        } catch (e: CancellationException) {
            // lasciata a metà (cambio scheda): si allinea senza festa
            shownWeek = weekSessions
            shownStreak = streak
            throw e
        }
    }

    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().heightIn(min = 224.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), Color(0xFF0A0E18).copy(alpha = 0.9f))))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.76f, size.height * 0.12f), radius = 260.dp.toPx()))
            }
            .border(1.dp, accent.copy(alpha = 0.3f), shape),
    ) {
        Mountain(accent, Modifier.matchParentSize())
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(tr(if (hour < 12) "Buongiorno" else if (hour < 18) "Buon pomeriggio" else "Buonasera"), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary)
                    Text(name, style = PeakTheme.rounded(19, FontWeight.ExtraBold), color = PeakTheme.textPrimary, maxLines = 1)
                }
                val settingsLabel = tr("Impostazioni")
                Box(
                    Modifier.size(36.dp).tourTarget("avatar").background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f))), CircleShape)
                        .pressable(onClick = onAvatar).semantics { contentDescription = settingsLabel; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text(name.take(1).uppercase(), style = PeakTheme.rounded(15, FontWeight.ExtraBold), color = Color.Black.copy(alpha = 0.8f)) }
            }

            // mai troncata: va a capo quanto serve e lascia libera la vetta a destra
            AnimatedContent(
                index, Modifier.padding(top = 8.dp).heightIn(min = 66.dp).fillMaxWidth().padding(end = 84.dp), label = "phrase",
                transitionSpec = {
                    if (reduce) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    else (fadeIn(tween(600)) + slideInVertically(tween(600)) { 10 }) togetherWith fadeOut(tween(300))
                },
            ) { i ->
                Text(
                    tr(Motivation.italian[i]).replace(", ", ",\n"),
                    style = TextStyle(
                        fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, lineHeight = 26.sp,
                        shadow = Shadow(accent.copy(alpha = 0.45f), blurRadius = 28f),
                    ),
                    color = PeakTheme.textPrimary,
                )
            }

            Spacer(Modifier.weight(1f, fill = false).height(8.dp))
            GoalBar(shownWeek, goal, weekSessions, accent, reduce, onChangeGoal)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("$shownStreak", tr("settimane di fila"), accent, icon = true, flameTick = flameTick)
                if (weekSessions > goal) Pill(null, tr("+%lld oltre l'obiettivo", weekSessions - goal), accent)
            }
        }
    }
}

@Composable
private fun rememberResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/** L'obiettivo della settimana: un segmento per allenamento. Si tocca per cambiarlo. */
@Composable
private fun GoalBar(shown: Int, goal: Int, weekSessions: Int, accent: Color, reduce: Boolean, onChange: () -> Unit) {
    val label = tr("Obiettivo della settimana, tocca per cambiarlo")
    val value = tr("%lld su %lld", minOf(weekSessions, goal), goal)
    Column(
        Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, onClick = onChange)
            .semantics(mergeDescendants = true) { contentDescription = label; stateDescription = value; role = Role.Button },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(tr(if (shown >= goal) "Obiettivo raggiunto!" else "Obiettivo della settimana"), Modifier.weight(1f), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary)
            Text("$shown / $goal", style = PeakTheme.tabular(15, FontWeight.ExtraBold), color = PeakTheme.textPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in 0 until goal) {
                val filled = k < shown
                // il segmento pieno cresce da sinistra sopra quello vuoto
                val fill by animateFloatAsState(if (filled || reduce) 1f else 0.3f, spring(dampingRatio = 0.85f, stiffness = 400f), label = "segment")
                Box(
                    Modifier.weight(1f).height(12.dp)
                        .then(if (k == shown - 1) Modifier.shadow(7.dp, CircleShape, ambientColor = accent.copy(alpha = 0.7f), spotColor = accent.copy(alpha = 0.7f)) else Modifier)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape),
                ) {
                    if (filled) Box(Modifier.fillMaxWidth(fill).height(12.dp).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent)), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun Pill(number: String?, text: String, accent: Color, icon: Boolean = false, flameTick: Int = 0) {
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) Icon(WorkoutsIcons.flame, null, Modifier.size(14.dp).bounce(flameTick, 1.3f, 10f), tint = accent)
        if (number != null) Text(number, style = PeakTheme.rounded(14, FontWeight.ExtraBold), color = accent)
        Text(text, style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary)
    }
}

/** Le creste, in basso a destra come sul web (`viewBox 360×220`, ancorate all'angolo). */
@Composable
private fun Mountain(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = maxOf(size.width / 360f, size.height / 220f)
        withTransform({
            translate(size.width - 360f * s, size.height - 220f * s)
            scale(s, s, Offset.Zero)
        }) {
            fun poly(vararg p: Pair<Float, Float>) = Path().apply {
                moveTo(p[0].first, p[0].second)
                for (q in p.drop(1)) lineTo(q.first, q.second)
                close()
            }
            drawPath(poly(0f to 150f, 40f to 122f, 84f to 140f, 132f to 92f, 182f to 128f, 232f to 72f, 292f to 122f, 332f to 96f, 360f to 114f, 360f to 220f, 0f to 220f), Color.White.copy(alpha = 0.05f))
            drawPath(
                poly(0f to 182f, 58f to 146f, 112f to 164f, 168f to 110f, 214f to 142f, 262f to 48f, 304f to 104f, 338f to 84f, 360f to 96f, 360f to 220f, 0f to 220f),
                Brush.verticalGradient(listOf(accent.copy(alpha = 0.38f), accent.copy(alpha = 0.04f)), 0f, 220f),
            )
            drawPath(poly(262f to 48f, 246f to 78f, 256f to 72f, 262f to 82f, 270f to 72f, 282f to 80f), Color.White.copy(alpha = 0.85f))
            drawPath(poly(0f to 220f, 0f to 184f, 70f to 170f, 132f to 188f, 202f to 160f, 262f to 180f, 330f to 164f, 360f to 176f, 360f to 220f), Color.Black.copy(alpha = 0.38f))
            val flag = Path().apply { moveTo(262f, 48f); lineTo(262f, 26f); lineTo(282f, 33f); lineTo(262f, 40f) }
            drawPath(flag, accent)
            drawPath(flag, accent, style = Stroke(1.5f))
        }
    }
}

/** La barra «cosa faccio adesso»: il giorno di oggi, un tocco e si parte (`NextWorkoutBar`). */
@Composable
fun NextWorkoutBar(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier.fillMaxWidth().glassSurface(18.dp).pressable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(accent, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = Color.Black.copy(alpha = 0.8f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(subtitle, style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary, maxLines = 1)
        }
        Icon(com.gabrieleforghieri.peak.design.PeakIcons.chevronRight, null, Modifier.size(14.dp), tint = PeakTheme.textTertiary)
    }
}
