package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.catalogEntry
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.figureMuscles
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.MuscleFigureView
import com.gabrieleforghieri.peak.design.MuscleGroupChip
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.SheetDetent
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.MuscleId
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr

/**
 * **Firma condivisa** (iPhone: `Features/Workouts/MuscleDetailSheet.swift`). Proprietario: l'area **workouts**.
 * Usata da: workouts (sessione, editor dei giorni), machines (dettaglio del macchinario).
 *
 * I muscoli coinvolti da un macchinario: la figura davanti e dietro (si tocca per ingrandirla a tutto schermo), i chip dei muscoli e le note.
 */
@Composable
fun MuscleDetailSheet(exercise: ExerciseEntity, onDismiss: () -> Unit) {
    PeakSheet(onDismiss, detent = SheetDetent.Medium) { close -> MuscleDetailContent(exercise, close) }
}

/** Il contenuto del foglio, senza il foglio (si prova negli screenshot). */
@Composable
fun MuscleDetailContent(exercise: ExerciseEntity, onClose: () -> Unit) {
    val muscles = exercise.figureMuscles
    val tint = exercise.group.tint
    val notes = exercise.notes.trim().ifEmpty { exercise.catalogEntry?.notes?.trim().orEmpty() }
    // la figura aperta a tutto schermo, se c'è
    var zoomSide by remember { mutableStateOf<AnatomyView?>(null) }

    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            Text(tr("Dettaglio muscolare"), style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(
                tr("Chiudi"), Modifier.weight(1f).clickableText(onClose).padding(vertical = 6.dp),
                style = PeakTheme.rounded(15, FontWeight.SemiBold), color = tint, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.displayName(), Modifier.weight(1f, fill = false), style = PeakTheme.rounded(22, FontWeight.Bold), color = PeakTheme.textPrimary)
                MuscleGroupChip(exercise.group)
            }

            GlassCard {
                SectionHeader("Mappa muscolare")
                Row(Modifier.padding(top = 12.dp).widthIn(max = 340.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for ((view, label) in listOf(AnatomyView.front to "Fronte", AnatomyView.back to "Retro")) {
                        val zoomLabel = tr("Ingrandisci")
                        Column(
                            Modifier.weight(1f).pressable { zoomSide = view }.semantics { contentDescription = zoomLabel },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                MuscleFigureView(muscles, Modifier.height(220.dp), view = view, zoom = false)
                                ZoomLens()
                            }
                            Text(tr(label), style = PeakTheme.rounded(11, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                        }
                    }
                }
            }

            if (muscles.isNotEmpty()) GlassCard {
                SectionHeader("Muscoli coinvolti")
                MuscleChipGrid(muscles, Modifier.padding(top = 12.dp))
            }

            if (notes.isNotEmpty()) GlassCard {
                SectionHeader("Consigli e setup")
                Text(tr(notes), Modifier.padding(top = 8.dp), style = PeakTheme.rounded(13, FontWeight.Normal).copy(lineHeight = 19.sp), color = PeakTheme.textSecondary)
            }
        }
    }
    zoomSide?.let { MuscleZoomScreen(muscles, it) { zoomSide = null } }
}

/** I muscoli come targhette col colore del loro gruppo (`MuscleChipGrid`): due per riga. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleChipGrid(muscles: List<MuscleId>, modifier: Modifier = Modifier) {
    val italian = Lang.isItalian()
    FlowRow(modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in muscles) {
            Row(
                Modifier.weight(1f).background(m.group.tint.copy(alpha = 0.18f), RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(m.group.tint, CircleShape))
                Text(if (italian) m.title else m.titleEn, style = PeakTheme.rounded(13, FontWeight.SemiBold), color = PeakTheme.textPrimary, maxLines = 1)
            }
        }
    }
}

/** La lente nell'angolo della figura: dice che si può toccare per ingrandirla. */
@Composable
fun ZoomLens(modifier: Modifier = Modifier) {
    Box(modifier.size(26.dp).background(PeakTheme.cardStrong, CircleShape), contentAlignment = Alignment.Center) {
        Icon(PeakIcons.zoom, null, Modifier.size(14.dp), tint = PeakTheme.textPrimary)
    }
}
