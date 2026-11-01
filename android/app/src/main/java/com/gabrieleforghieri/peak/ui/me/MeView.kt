package com.gabrieleforghieri.peak.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.PeakRepository
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.design.CountText
import com.gabrieleforghieri.peak.design.DeltaBadge
import com.gabrieleforghieri.peak.design.ExerciseThumbnail
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PRBadge
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.SheetDetent
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.design.glassSurface
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MedalID
import com.gabrieleforghieri.peak.domain.Medals
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.nav.Tab
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Quello che ha letto Me: i dati e, dietro, le liste da cui nascono (servono alle date del foglio di una medaglia). */
private class MeSnapshot(
    val workouts: List<WorkoutEntity>,
    val sets: List<WorkoutSetEntity>,
    val exercises: List<ExerciseEntity>,
    val records: List<MaxRecordEntity>,
    val data: MeData,
)

/** Rilegge e ricalcola a ogni modifica di allenamenti, serie, macchinari o massimali (`Insights`), fuori dal thread principale. */
private fun meSnapshots(repo: PeakRepository, friends: Int): Flow<MeSnapshot> =
    combine(repo.workouts, repo.sets, repo.exercises, repo.records) { w, s, e, r ->
        MeSnapshot(w, s, e, r, MeData.compute(w, s, e, r, friends))
    }.flowOn(Dispatchers.Default)

/**
 * Il tuo profilo: i numeri, l'attività e le medaglie per esteso. In alto a destra le impostazioni (`MeView`).
 *
 * Gli amici non ci sono ancora (l'area amici non ha un conteggio da leggere): `friendCount` è 0 finché qualcuno non lo fornisce.
 */
@Composable
fun MeScreen(friendCount: Int = 0) {
    val repo = LocalRepository.current
    val app = LocalApp.current
    val nav = LocalPeakNav.current
    val unit = LocalAppSettings.current.unit
    val scope = rememberCoroutineScope()

    val snapshot by remember(repo, friendCount) { meSnapshots(repo, friendCount) }.collectAsState(null)
    val weights by repo.bodyWeights.collectAsState(emptyList())
    val status by app.sync.engine.status.collectAsState()

    MeContent(
        name = status.credentials?.name ?: "",
        friendCount = friendCount,
        data = snapshot?.data,
        weights = weights,
        unit = unit,
        onSettings = { nav.openSettings() },
        onFriends = { nav.goTab(Tab.friends) },
        onSaveWeight = { entry -> scope.launch { app.database.bodyWeights().upsert(entry) } },
        onDeleteWeight = { entry -> scope.launch { app.database.bodyWeights().delete(entry.uuid) } },
        loadMedalDates = { kind ->
            val s = snapshot
            if (s == null) List(3) { null }
            else withContext(Dispatchers.Default) { medalDates(s.workouts, s.sets, s.exercises, s.records, friendCount, s.data.medals)[kind] ?: List(3) { null } }
        },
    )
}

/**
 * La scheda Me, senza accesso ai dati: tutto arriva dai parametri (così si prova e si fotografa). Il peso corporeo si registra anche
 * senza aver mai fatto un allenamento, quindi la sua card resta sempre a schermo.
 */
@Composable
fun MeContent(
    name: String,
    friendCount: Int,
    data: MeData?,
    weights: List<BodyWeightEntity>,
    unit: WeightUnit,
    onSettings: () -> Unit,
    onFriends: () -> Unit,
    onSaveWeight: (BodyWeightEntity) -> Unit,
    onDeleteWeight: (BodyWeightEntity) -> Unit,
    loadMedalDates: suspend (MedalID) -> List<Long?>,
) {
    val accent = LocalAccent.current
    var openMedal by remember { mutableStateOf<MedalID?>(null) }
    val hasTrainingData = data?.hasTrainingData == true

    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(accent))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(name, onSettings)
            FriendsButton(friendCount, onFriends)

            if (data != null && hasTrainingData) {
                val s = data.stats
                Section("Panoramica") { Overview(s, unit) }
                RecentRecords(data, unit)
                val totals = Medals.totals(data.medals)
                Section("Medaglie", "${totals.earned} / ${totals.total}") { MedalsCard(data.medals, unit) { openMedal = it } }
                Section("Attività", "ultime 14 settimane") { HeatmapCard(s.perDay, s.last98Days) }
                Section("Volume settimanale", "ultime 12 settimane") { WeeklyBarsCard(s.weeks, unit) }
                if (s.groups.isNotEmpty()) Section("Dove va il lavoro", "ultimi 30 giorni") { WorkDonutCard(s.groups, unit) }
            }

            BodyWeightCard(weights, unit, onSaveWeight, onDeleteWeight)

            if (data != null && data.progression.isNotEmpty()) Section("Progressione") { ProgressionCard(data.progression, unit) }
            if (data != null && data.movers.isNotEmpty()) MoversCard(data.movers, unit)

            if (!hasTrainingData) EmptyHint()
        }
    }

    val kind = openMedal
    val medal = kind?.let { k -> data?.medals?.firstOrNull { it.kind == k } }
    if (medal != null) {
        PeakSheet(onDismiss = { openMedal = null }, detent = SheetDetent.Medium) { close ->
            val dates by produceState<List<Long?>>(List(3) { null }, medal.kind) { value = loadMedalDates(medal.kind) }
            MedalSheetContent(medal, dates, unit, close)
        }
    }
}

@Composable
private fun Header(name: String, onSettings: () -> Unit) {
    val accent = LocalAccent.current
    val settingsLabel = tr("Impostazioni")
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).appear(0), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp)
                .shadow(10.dp, CircleShape, ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.35f))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f))), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initialOf(name), style = PeakTheme.rounded(28, FontWeight.ExtraBold), color = Color.Black.copy(alpha = 0.8f))
        }
        Text(
            name.ifEmpty { tr("Me") }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = PeakTheme.rounded(26, FontWeight.ExtraBold), color = PeakTheme.textPrimary,
        )
        Box(
            Modifier.size(44.dp).glassSurface(22.dp).pressable(onClick = onSettings).semantics { contentDescription = settingsLabel },
            contentAlignment = Alignment.Center,
        ) { Icon(MeIcons.gear, null, Modifier.size(20.dp), tint = PeakTheme.textPrimary) }
    }
}

/** La lettera nel cerchio: la prima del nome, «P» senza nome. */
fun initialOf(name: String): String = (name.firstOrNull() ?: 'P').uppercase()

@Composable
private fun FriendsButton(friendCount: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).appear(1).glassSurface(16.dp).pressable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PeakIcons.friends, null, Modifier.size(20.dp), tint = PeakTheme.textPrimary)
        Text(
            when {
                friendCount == 1 -> tr("1 amico")
                friendCount > 1 -> tr("%lld amici", friendCount)
                else -> tr("Aggiungi amici")
            },
            style = PeakTheme.rounded(15), color = PeakTheme.textPrimary,
        )
    }
}

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.appear(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, Modifier.padding(top = 6.dp), trailing)
        content()
    }
}

@Composable
private fun Overview(s: HomeStats, unit: WeightUnit) {
    @Composable
    fun Cell(icon: ImageVector, value: Double, label: String, modifier: Modifier, format: (Double) -> String = { it.roundToInt().toString() }) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(26.dp).padding(horizontal = 1.dp), tint = LocalAccent.current)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                CountText(value, style = PeakTheme.rounded(22, FontWeight.ExtraBold).copy(color = PeakTheme.textPrimary, fontFeatureSettings = "tnum"), format = format)
                Text(tr(label), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary, maxLines = 1)
            }
        }
    }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row {
                Cell(MeIcons.flame, s.streak.toDouble(), "Settimane di fila", Modifier.weight(1f))
                Cell(PeakIcons.workouts, s.finished.toDouble(), "Allenamenti", Modifier.weight(1f))
            }
            Row {
                Cell(MeIcons.scale, s.totalVolume, "Sollevati", Modifier.weight(1f), format = { Fmt.volume(it, unit) })
                Cell(MeIcons.trophy, s.recordCount.toDouble(), "Record", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecentRecords(data: MeData, unit: WeightUnit) {
    val latest = data.recentByExercise
    if (latest.isEmpty()) return
    val gold = PeakTheme.gold
    Section("Record recenti") {
        GlassCard(padding = 6.dp) {
            latest.forEachIndexed { i, (exercise, record) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(gold.copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(MeIcons.trophy, null, Modifier.size(16.dp), tint = gold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(exercise.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
                        Text(relativeDay(record.date), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                    }
                    Text(Fmt.setLine(record.weight, record.reps, unit), style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary)
                }
                if (i < latest.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(PeakTheme.stroke))
            }
        }
    }
}

@Composable
private fun MoversCard(movers: List<MoverRow>, unit: WeightUnit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Cresciuti di più", trailing = "ultimi 90 giorni")
            for (m in movers) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumbnail(m.exercise, size = 32.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.exercise.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = PeakTheme.rounded(14), color = PeakTheme.textPrimary)
                        Text(
                            if (m.previous > 0) "${Fmt.weight(m.previous, unit)} → ${Fmt.weight(m.current, unit)}" else tr("primo massimale: %@", Fmt.weight(m.current, unit)),
                            style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                        )
                    }
                    if (m.previous > 0) DeltaBadge(m.deltaPercent) else PRBadge(text = tr("NUOVO"))
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MeIcons.trend, null, Modifier.size(18.dp), tint = PeakTheme.textPrimary)
                Text(tr("Ancora nessun allenamento"), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
            }
            Text(
                tr("Registra un massimale o completa una sessione: qui compariranno volumi, record e andamento."),
                style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary,
            )
        }
    }
}
