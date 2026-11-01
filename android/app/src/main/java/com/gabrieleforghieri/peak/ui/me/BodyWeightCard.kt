package com.gabrieleforghieri.peak.ui.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MonthStack
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.WeightStepper
import com.gabrieleforghieri.peak.design.drawIn
import com.gabrieleforghieri.peak.domain.ChartRange
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Stats
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.util.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/** I limiti del server: 20-400 kg (fuori, la pesata verrebbe scartata). */
val BODY_WEIGHT_KG = 20.0..400.0

/**
 * Peso corporeo: ultima pesata, andamento e inserimento rapido (`BodyWeightCard`). `entries` dalla più recente.
 * Sta nei Progressi perché è lì che si guarda l'andamento di tutto il resto. `onSave`/`onDelete` scrivono nel database.
 *
 * Il pulsante «Da Salute» e la scrittura in Salute non ci sono: Health Connect è della fase 4.
 */
@Composable
fun BodyWeightCard(
    entries: List<BodyWeightEntity>,
    unit: WeightUnit,
    onSave: (BodyWeightEntity) -> Unit,
    onDelete: (BodyWeightEntity) -> Unit,
) {
    val accent = LocalAccent.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var showingEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BodyWeightEntity?>(null) }
    var showingAll by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(ChartRange.all) }

    val latest = entries.firstOrNull()
    val delta = monthDelta(entries)
    val available = availableRanges(entries.minOfOrNull { it.date })
    val active = activeRange(range, available)
    val points = bodyWeightPoints(entries, active)

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(tr("Peso corporeo"), trailing = latest?.let { relativeDay(it.date) })

            if (latest != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.weight(latest.weight, unit, includeSymbol = false), style = PeakTheme.display(38), color = PeakTheme.textPrimary)
                    Text(unit.symbol, Modifier.padding(bottom = 6.dp), style = PeakTheme.rounded(15), color = PeakTheme.textSecondary)
                    if (delta != null && abs(delta) >= 0.05) {
                        val sign = if (delta > 0) "+" else "−"
                        Text(
                            tr("%@%@ in 30 gg", sign, Fmt.weight(abs(delta), unit)), Modifier.padding(bottom = 6.dp),
                            style = PeakTheme.rounded(11), color = PeakTheme.textTertiary,
                        )
                    }
                }
            } else {
                Text(tr("Nessuna pesata registrata"), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary)
            }

            if (entries.size >= 2) {
                if (available.size > 1) RangePicker(available, active, { range = it }, accent)
                if (points.size >= 2) {
                    val values = points.map { unit.fromKilograms(it.weight) }
                    val (domain, ticks) = yDomainOf(values, 4, minStep = 1.0)
                    LineChart(
                        points = points.mapIndexed { i, e -> ChartPoint(i.toDouble(), values[i], e.fromHealth) },
                        xDomain = -0.5..(maxOf(points.size - 1, 1) + 0.5),
                        yDomain = domain,
                        yTicks = ticks.map { AxisTick(it, it.roundToInt().toString()) },
                        xTicks = Stats.tickIndices(points.size, 3).map { AxisTick(it.toDouble(), shortDateText(points[it].date)) },
                        color = accent, height = 120.dp, modifier = Modifier.drawIn(),
                    )
                } else NotEnoughData(80)
            }

            Box(
                Modifier.fillMaxWidth().background(accent.copy(alpha = 0.20f), RoundedCornerShape(11.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { showingEditor = true }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PeakIcons.plus, null, Modifier.size(14.dp), tint = accent)
                    Text(tr("Aggiungi pesata"), style = PeakTheme.rounded(13, FontWeight.Bold), color = accent)
                }
            }

            // Le ultime pesate: toccane una per correggerla o cancellarla.
            if (entries.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(PeakTheme.stroke))
                entries.take(5).forEach { WeighRow(it, unit) { editing = it } }
                if (entries.size > 5) {
                    Row(
                        Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { showingAll = !showingAll }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(PeakIcons.chevronRight, null, Modifier.size(14.dp).rotate(if (showingAll) 90f else 0f), tint = PeakTheme.textSecondary)
                        Text(tr("Tutte le pesate"), Modifier.weight(1f), style = PeakTheme.rounded(13, FontWeight.Bold), color = PeakTheme.textSecondary)
                        Text("${entries.size}", style = PeakTheme.rounded(11), color = PeakTheme.textSecondary)
                    }
                    AnimatedVisibility(showingAll) {
                        MonthStack(entries, { it.date }, { if (it == 1) context.tr("1 pesata") else context.tr("%lld pesate", it) }, openFirst = false) { entry ->
                            WeighRow(entry, unit) { editing = entry }
                        }
                    }
                }
            }
        }
    }

    if (showingEditor) {
        PeakSheet(onDismiss = { showingEditor = false }) { close ->
            BodyWeightSheetContent(latest?.weight, null, unit, onSave = { onSave(it) }, onDelete = {}, onClose = close)
        }
    }
    editing?.let { entry ->
        PeakSheet(onDismiss = { editing = null }) { close ->
            BodyWeightSheetContent(entry.weight, entry, unit, onSave = { onSave(it) }, onDelete = { onDelete(entry) }, onClose = close)
        }
    }
}

/** Una pesata: toccala per correggerla o cancellarla. */
@Composable
private fun WeighRow(entry: BodyWeightEntity, unit: WeightUnit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateText(entry.date), Modifier.weight(1f), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
        Text(Fmt.weight(entry.weight, unit), style = PeakTheme.rounded(13), color = PeakTheme.textPrimary)
        Icon(MeIcons.pencil, null, Modifier.size(11.dp), tint = PeakTheme.textTertiary)
    }
}

/**
 * Inserimento di una pesata (`BodyWeightSheet`): il contenuto del foglio. Con `entry` corregge (o cancella) quella pesata invece di
 * crearne una. `onSave` riceve la pesata da scrivere; il foglio si chiude con `onClose`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightSheetContent(
    lastWeight: Double?,
    entry: BodyWeightEntity?,
    unit: WeightUnit,
    onSave: (BodyWeightEntity) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val accent = LocalAccent.current
    val zone = ZoneId.systemDefault()
    var weight by remember { mutableDoubleStateOf(lastWeight ?: 75.0) }
    var date by remember { mutableStateOf(entry?.date ?: now) }
    var picking by remember { mutableStateOf(false) }
    val valid = weight in BODY_WEIGHT_KG

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, Modifier.width(88.dp)) { Text(tr("Annulla"), color = PeakTheme.textSecondary) }
            Text(
                tr(if (entry == null) "Pesata" else "Modifica pesata"), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary,
            )
            TextButton(
                onClick = {
                    // la pesata nuova prende il giorno scelto e l'ora di adesso; una corretta tiene la sua
                    onSave(BodyWeightEntity(entry?.uuid ?: java.util.UUID.randomUUID().toString().uppercase(), date, weight, entry?.fromHealth ?: false))
                    onClose()
                },
                Modifier.width(88.dp), enabled = valid,
            ) { Text(tr("Salva"), style = PeakTheme.rounded(16, FontWeight.Bold), color = if (valid) accent else PeakTheme.textTertiary) }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(padding = 18.dp, tint = accent) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SectionHeader(tr("Peso"))
                    WeightStepper(weight, { weight = it }, step = 0.0, unit = unit, tint = accent, nativeStep = unit.bodyWeightStep)
                }
            }
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Data"), Modifier.weight(1f), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textPrimary)
                    Text(
                        dateText(date),
                        Modifier.background(accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                            .clickable(remember { MutableInteractionSource() }, null) { picking = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = PeakTheme.rounded(14, FontWeight.SemiBold), color = accent,
                    )
                }
            }
            if (entry != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clickable(remember { MutableInteractionSource() }, null) { onDelete(); onClose() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MeIcons.trash, null, Modifier.size(16.dp), tint = PeakTheme.negative)
                    Text(tr("Cancella pesata"), Modifier.padding(start = 8.dp), style = PeakTheme.rounded(14), color = PeakTheme.negative)
                }
            }
        }
    }

    if (picking) {
        val today = LocalDate.now(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Instant.ofEpochMilli(date).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { utc ->
                        val picked = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        date = pickDay(date, picked, zone)
                    }
                    picking = false
                }) { Text(tr("Fatto"), color = accent) }
            },
            dismissButton = { TextButton({ picking = false }) { Text(tr("Annulla"), color = PeakTheme.textSecondary) } },
        ) { DatePicker(state) }
    }
}

/** Cambia il giorno di una data lasciando l'ora (come il selettore a sola data di iOS). */
fun pickDay(ms: Long, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().atDate(day).atZone(zone).toInstant().toEpochMilli()
