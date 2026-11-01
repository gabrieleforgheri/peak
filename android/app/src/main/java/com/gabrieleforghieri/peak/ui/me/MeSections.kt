package com.gabrieleforghieri.peak.ui.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.design.CountText
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.Motion
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.drawIn
import com.gabrieleforghieri.peak.design.growUp
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Medal
import com.gabrieleforghieri.peak.domain.MedalID
import com.gabrieleforghieri.peak.domain.Medals
import com.gabrieleforghieri.peak.domain.Stats
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.util.tr
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// Le parti di Me (`MeSections.swift`). Ognuna riceve già i numeri: i conti stanno in `MeStats.kt`.

// ---- Attività -----------------------------------------------------------------------------

private const val HEATMAP_WEEKS = 14

/** Una colonna per settimana (lunedì in alto), un quadratino per giorno; i giorni futuri valgono −1. */
fun heatmapColumns(perDay: Map<LocalDate, Int>, today: LocalDate): List<List<Pair<LocalDate, Int>>> {
    val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val first = monday.minusWeeks((HEATMAP_WEEKS - 1).toLong())
    return (0 until HEATMAP_WEEKS).map { c ->
        (0 until 7).map { r ->
            val d = first.plusDays((c * 7 + r).toLong())
            d to (if (d.isAfter(today)) -1 else perDay[d] ?: 0)
        }
    }
}

private fun heatColor(n: Int, accent: Color): Color = when {
    n < 0 -> Color.Transparent
    n == 0 -> Color.White.copy(alpha = 0.06f)
    n < 8 -> accent.copy(alpha = 0.3f)
    n < 16 -> accent.copy(alpha = 0.55f)
    n < 24 -> accent.copy(alpha = 0.8f)
    else -> accent
}

@Composable
fun HeatmapCard(perDay: Map<LocalDate, Int>, count: Int, today: LocalDate = LocalDate.now()) {
    val accent = LocalAccent.current
    val columns = remember(perDay, today) { heatmapColumns(perDay, today) }
    val locale = Locale.getDefault()
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().drawIn()) {
                val gap = 4.dp
                val label = 14.dp
                val cell = (maxWidth - label - 6.dp - gap * (HEATMAP_WEEKS - 1)) / HEATMAP_WEEKS
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    // il mese sta sopra la colonna che contiene il giorno 1 (guardo la domenica), non sopra il primo lunedì
                    Box(Modifier.fillMaxWidth().height(12.dp).padding(start = label + 6.dp)) {
                        columns.forEachIndexed { i, column ->
                            val month = column[6].first.monthValue
                            val prev = if (i > 0) columns[i - 1][6].first.monthValue else -1
                            if (month != prev) {
                                Text(
                                    column[6].first.month.getDisplayName(TextStyle.SHORT, locale),
                                    Modifier.offset(x = (cell + gap) * i), softWrap = false,
                                    style = PeakTheme.rounded(9, FontWeight.Medium), color = PeakTheme.textTertiary,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            for (r in 0 until 7) {
                                Box(Modifier.width(label).height(cell), contentAlignment = Alignment.Center) {
                                    Text(
                                        DayOfWeek.of(r + 1).getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                                        style = PeakTheme.rounded(9, FontWeight.Medium), color = PeakTheme.textTertiary,
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (column in columns) {
                                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                    for ((date, sets) in column) {
                                        val shape = RoundedCornerShape(4.dp)
                                        Box(
                                            Modifier.size(cell).background(heatColor(sets, accent), shape)
                                                // il giorno 1 del mese è bordato: si vede dove inizia il mese dentro la settimana
                                                .then(if (date.dayOfMonth == 1) Modifier.border(1.5.dp, PeakTheme.textSecondary, shape) else Modifier),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(tr("Una colonna = una settimana, un quadratino = un giorno"), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
            Text(
                if (count == 1) tr("1 allenamento in 14 settimane") else tr("%lld allenamenti in 14 settimane", count),
                style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary,
            )
        }
    }
}

// ---- Volume settimanale ---------------------------------------------------------------------

@Composable
fun WeeklyBarsCard(weeks: List<HomeStats.Week>, unit: WeightUnit) {
    val accent = LocalAccent.current
    val top = max(1.0, weeks.maxOfOrNull { it.volume } ?: 1.0)
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                weeks.forEachIndexed { i, w ->
                    val now = i == weeks.size - 1
                    val shape = RoundedCornerShape(5.dp)
                    Box(
                        Modifier.weight(1f).height(max(4f, (w.volume / top * 100).toFloat()).dp)
                            .then(if (now) Modifier.shadow(8.dp, shape, ambientColor = accent.copy(alpha = 0.45f), spotColor = accent.copy(alpha = 0.45f)) else Modifier)
                            .growUp(i)
                            .clip(shape)
                            .background(
                                if (w.volume == 0.0) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.07f)))
                                else Brush.verticalGradient(listOf(accent.copy(alpha = if (now) 1f else 0.7f), accent.copy(alpha = if (now) 0.5f else 0.25f))),
                            ),
                    )
                }
            }
            // asse delle settimane: il giorno in cui inizia la settimana, una barra su due
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weeks.forEachIndexed { i, w ->
                    Text(
                        if (i % 2 == (weeks.size - 1) % 2) shortDateText(w.start) else "",
                        Modifier.weight(1f), maxLines = 1, softWrap = false, textAlign = TextAlign.Center, overflow = TextOverflow.Clip,
                        style = PeakTheme.rounded(8, FontWeight.Medium), color = PeakTheme.textTertiary,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("1 barra = 1 settimana"), Modifier.weight(1f), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                Text(tr("questa settimana: %@", Fmt.volume(weeks.lastOrNull()?.volume ?: 0.0, unit)), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textSecondary)
            }
        }
    }
}

// ---- Dove va il lavoro ----------------------------------------------------------------------

@Composable
fun WorkDonutCard(groups: List<Stats.GroupVolume>, unit: WeightUnit) {
    val total = max(1.0, groups.sumOf { it.volume })
    val reduce = LocalReduceMotion.current
    val sweep = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) sweep.animateTo(1f, Motion.out(Motion.CHART, 0)) }
    GlassCard {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val line = 14.dp.toPx()
                    val box = Size(size.width - line, size.height - line)
                    val topLeft = Offset(line / 2, line / 2)
                    drawArc(Color.White.copy(alpha = 0.06f), 0f, 360f, false, topLeft, box, style = Stroke(line))
                    // la maschera ad anello scopre le fette da ore 12 in senso orario
                    val visible = sweep.value * 360f
                    var at = 0.0
                    for (g in groups) {
                        val from = at
                        at += g.volume / total
                        val start = (from * 360).toFloat()
                        val length = (max(from, at - 0.006) * 360).toFloat() - start
                        val shown = minOf(length, visible - start)
                        if (shown > 0f) drawArc(g.group.tint, -90f + start, shown, false, topLeft, box, style = Stroke(line))
                    }
                }
                Text(Fmt.volume(total, unit), style = PeakTheme.rounded(15, FontWeight.ExtraBold), color = PeakTheme.textPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in groups) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(g.group.tint, CircleShape))
                        Text(tr(g.group.title), Modifier.padding(start = 8.dp).weight(1f), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textPrimary)
                        Text("${(g.volume / total * 100).roundToInt()}%", style = PeakTheme.rounded(13, FontWeight.Bold), color = PeakTheme.textPrimary)
                    }
                }
                Text(tr("Ultimi 30 giorni"), style = PeakTheme.rounded(10, FontWeight.Medium), color = PeakTheme.textTertiary)
            }
        }
    }
}

// ---- Medaglie -------------------------------------------------------------------------------

/**
 * Una medaglia come sul sito: disco metallico col simbolo e un anello sottile che si riempie verso il livello dopo (a oro preso è
 * pieno). `sweep`: all'apertura l'anello parte da vuoto e si riempie.
 */
@Composable
fun MedalDisc(medal: Medal, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 52.dp, sweep: Boolean = false) {
    val accent = LocalAccent.current
    val reduce = LocalReduceMotion.current
    val ring = remember { Animatable(if (sweep && !reduce) 0f else 1f) }
    LaunchedEffect(Unit) { if (sweep && !reduce) ring.animateTo(1f, Motion.out(700, 120)) }
    val tint: Color
    val hi: Color
    val lo: Color
    val ink: Color
    when (medal.tier) {
        1 -> { tint = PeakTheme.bronze; hi = Color(0xFFF1B27A); lo = Color(0xFFA35F2A); ink = Color(0xFF3A1D08) }
        2 -> { tint = PeakTheme.silver; hi = Color(0xFFF4F6FA); lo = Color(0xFF98A1B1); ink = Color(0xFF232A36) }
        3 -> { tint = PeakTheme.gold; hi = Color(0xFFFFE69A); lo = Color(0xFFD39A14); ink = Color(0xFF3D2A00) }
        else -> { tint = accent; hi = Color.White.copy(alpha = 0.08f); lo = Color.White.copy(alpha = 0.03f); ink = PeakTheme.textTertiary }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val line = this.size.minDimension * 0.045f
            val inset = this.size.minDimension * 0.04f
            val box = Size(this.size.width - 2 * inset - line, this.size.height - 2 * inset - line)
            val topLeft = Offset(inset + line / 2, inset + line / 2)
            drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, topLeft, box, style = Stroke(line))
            drawArc(tint, -90f, (medal.frac * ring.value * 360).toFloat(), false, topLeft, box, style = Stroke(line))
            val disc = this.size.minDimension * 0.11f
            val radius = (this.size.minDimension - 2 * disc) / 2
            drawCircle(
                Brush.radialGradient(listOf(hi, lo), center = Offset(disc + radius * 2 * 0.32f, disc + radius * 2 * 0.26f), radius = this.size.minDimension * 0.5f),
                radius, center,
            )
        }
        Icon(MeIcons.medal(medal.kind), null, Modifier.size(size * 0.4f), tint = ink)
    }
}

/** La sezione Medaglie di Me: le tre più vicine al livello dopo, le altre dietro «Mostra tutte». Toccando una riga si apre il foglio. */
@Composable
fun MedalsCard(medals: List<Medal>, unit: WeightUnit, onOpen: (MedalID) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    val list = remember(medals) { Medals.ordered(medals) }
    GlassCard(padding = 0.dp) {
        Column(Modifier.padding(horizontal = PeakTheme.padding)) {
            list.take(3).forEachIndexed { i, md ->
                if (i > 0) Divider()
                MedalRow(md, unit, onOpen)
            }
            AnimatedVisibility(showAll, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    for (md in list.drop(3)) {
                        Divider()
                        MedalRow(md, unit, onOpen)
                    }
                }
            }
            if (list.size > 3) {
                Divider()
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(remember { MutableInteractionSource() }, null) { showAll = !showAll },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (showAll) tr("Mostra meno") else tr("Mostra tutte (%lld)", list.size),
                        style = PeakTheme.rounded(14, FontWeight.SemiBold), color = PeakTheme.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(PeakTheme.stroke))

@Composable
private fun MedalRow(md: Medal, unit: WeightUnit, onOpen: (MedalID) -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(source, null) { onOpen(md.kind) }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        MedalDisc(md)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Text(tr(md.kind.title), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
                Text(tierName(md.tier), style = PeakTheme.rounded(12, if (md.tier == 0) FontWeight.SemiBold else FontWeight.ExtraBold), color = metalTint(md.tier))
            }
            MedalBar(md.frac)
            Text(progressLine(md, unit), style = PeakTheme.tabular(11, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
    }
}

/** «12 / 25», o solo il valore a oro preso. */
fun progressLine(md: Medal, unit: WeightUnit): String {
    val value = medalValue(md.kind, md.value, unit)
    val next = md.next ?: return value
    return "$value / ${medalValue(md.kind, next, unit)}"
}

/** La barretta sotto il nome: si riempie da sinistra quando compare. */
@Composable
private fun MedalBar(frac: Double) {
    val accent = LocalAccent.current
    val reduce = LocalReduceMotion.current
    val grown = remember { Animatable(if (reduce) frac.toFloat() else 0f) }
    LaunchedEffect(frac) { grown.animateTo(frac.toFloat(), Motion.out(500, 0)) }
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(PeakTheme.stroke)) {
        Box(Modifier.fillMaxWidth(max(grown.value, 0.0001f)).height(4.dp).background(accent))
    }
}

/** Il foglio di una medaglia: il contenuto (il foglio vero lo apre `MeScreen` dentro `PeakSheet`). */
@Composable
fun MedalSheetContent(medal: Medal, dates: List<Long?>, unit: WeightUnit, onClose: () -> Unit) {
    val accent = LocalAccent.current
    val reduce = LocalReduceMotion.current
    var appeared by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { appeared = true }
    fun fmt(v: Double) = medalValue(medal.kind, v, unit)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(60.dp))
            Text(tr(medal.kind.title), Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(
                tr("Chiudi"), Modifier.width(60.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
                textAlign = TextAlign.End, style = PeakTheme.rounded(15, FontWeight.SemiBold), color = accent,
            )
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tr(medal.kind.info), Modifier.widthIn(max = 260.dp).padding(bottom = 12.dp), textAlign = TextAlign.Center,
                    style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary,
                )
                MedalDisc(medal, size = 112.dp, sweep = true)
                CountText(
                    medal.value, Modifier.padding(top = 12.dp), style = PeakTheme.tabular(34, FontWeight.ExtraBold).copy(color = PeakTheme.textPrimary),
                    format = { fmt(it) },
                )
                Text(
                    tierName(medal.tier).uppercase(),
                    style = PeakTheme.rounded(12, FontWeight.ExtraBold).copy(letterSpacing = 0.9.sp), color = metalTint(medal.tier),
                )
                val next = medal.next
                Text(
                    if (next == null) tr("Oro conquistato.") else tr("Mancano %@ per %@.", fmt(next - medal.value), tierName(medal.tier + 1)),
                    textAlign = TextAlign.Center, style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary,
                )
            }
            Spacer(Modifier.height(18.dp))
            // dal basso (zero) in su: bronzo, argento, oro. Il tratto si riempie fino al valore di adesso
            Column(Modifier.padding(horizontal = 4.dp)) {
                for (i in listOf(2, 1, 0)) PathRow(medal, i, dates.getOrNull(i), appeared, unit)
            }
        }
    }
}

/** Quanto di ogni tratto del percorso è fatto: il tratto verso il bronzo parte da 0, gli altri dalla soglia sotto. */
fun pathFill(medal: Medal, i: Int): Double {
    val t = medal.kind.tiers
    val from = if (i == 0) 0.0 else t[i - 1]
    return max(0.0, minOf(1.0, (medal.value - from) / (t[i] - from)))
}

@Composable
private fun PathRow(medal: Medal, i: Int, date: Long?, appeared: Boolean, unit: WeightUnit) {
    val reduce = LocalReduceMotion.current
    val on = medal.tier > i
    val lit = on && appeared
    val tint = metalTint(i + 1)
    val delay = i * 200
    val grow by animateFloatAsState(
        if (appeared) pathFill(medal, i).toFloat() else 0f,
        if (reduce) Motion.out(1, 0) else Motion.out(400, delay + 150), label = "pathGrow",
    )
    val track = PeakTheme.stroke
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .graphicsLayer { alpha = if (on) 1f else 0.55f }
            .drawBehind {
                // il tratto sotto il nodo, fino al nodo di sotto
                val length = max(0f, size.height - 20.dp.toPx() + if (i == 0) 0f else 4.dp.toPx())
                val left = 12.5.dp.toPx()
                val top = 20.dp.toPx()
                val shape = 1.5.dp.toPx()
                drawRoundRect(track, Offset(left, top), Size(3.dp.toPx(), length), androidx.compose.ui.geometry.CornerRadius(shape))
                val filled = length * grow
                drawRoundRect(tint, Offset(left, top + length - filled), Size(3.dp.toPx(), filled), androidx.compose.ui.geometry.CornerRadius(shape))
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(28.dp).padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.size(14.dp)
                    .then(if (lit) Modifier.shadow(6.dp, CircleShape, ambientColor = tint, spotColor = tint) else Modifier)
                    .background(if (lit) tint else Color.Transparent, CircleShape)
                    .border(2.dp, if (lit) tint else PeakTheme.strokeStrong, CircleShape),
            )
        }
        Column(Modifier.weight(1f).padding(top = 1.dp)) {
            Text(tierName(i + 1), style = PeakTheme.rounded(15, FontWeight.Bold), color = if (on) tint else PeakTheme.textPrimary)
            Text(medalValue(medal.kind, medal.kind.tiers[i], unit), style = PeakTheme.tabular(12, FontWeight.Medium), color = PeakTheme.textSecondary)
        }
        if (on && date != null) Text(dateText(date), Modifier.padding(top = 3.dp), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary)
    }
}

// ---- Il grafico di un massimale -----------------------------------------------------------------

/** Il 1RM stimato di un macchinario nel tempo, con gli intervalli (`LiftChartCard`; lo usa anche il dettaglio del macchinario). `records`: i massimali a due braccia. */
@Composable
fun LiftChartCard(name: String, records: List<com.gabrieleforghieri.peak.data.MaxRecordEntity>, unit: WeightUnit) {
    val accent = LocalAccent.current
    var range by remember { mutableStateOf(com.gabrieleforghieri.peak.domain.ChartRange.all) }
    val available = availableRanges(records.minOfOrNull { it.date })
    val active = activeRange(range, available)
    val cutoff = active.days?.let { System.currentTimeMillis() - (it * 86_400_000L).toLong() }
    val pts = records.sortedBy { it.date }.filter { cutoff == null || it.date >= cutoff }
        .map { ProgressPoint(it.date, com.gabrieleforghieri.peak.domain.OneRepMax.estimate(it.weight, it.reps)) }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
                val a = pts.firstOrNull()
                val b = pts.lastOrNull()
                if (a != null && b != null) TrendBadge("${Fmt.weight(a.oneRm, unit)} → ${Fmt.weight(b.oneRm, unit)}", true)
            }
            Text(tr("1RM stimato"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary)
            if (available.size > 1) RangePicker(available, active, { range = it }, accent)
            if (pts.size >= 2) {
                val lo = pts.minOf { it.oneRm } * 0.97
                val hi = pts.maxOf { it.oneRm } * 1.03
                LineChart(
                    pts.map { ChartPoint(it.date.toDouble(), unit.fromKilograms(it.oneRm)) },
                    pts.first().date.toDouble()..pts.last().date.toDouble(), unit.fromKilograms(lo)..unit.fromKilograms(hi),
                    emptyList(), dateTicks(pts.first().date, pts.last().date, 3), accent, 150.dp, Modifier.drawIn(), lineWidth = 2.dp,
                )
            } else NotEnoughData(80)
        }
    }
}

/** La pastiglia «50 kg → 60 kg» sopra i grafici. */
@Composable
fun TrendBadge(text: String, up: Boolean) {
    val color = if (up) PeakTheme.positive else PeakTheme.negative
    Text(text, Modifier.background(color.copy(alpha = 0.15f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp), style = PeakTheme.rounded(11, FontWeight.Bold), color = color)
}

/** «Dati non sufficienti per questo intervallo». */
@Composable
fun NotEnoughData(height: Int) {
    Box(Modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) {
        Text(tr("Dati non sufficienti per questo intervallo"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary, textAlign = TextAlign.Center)
    }
}
