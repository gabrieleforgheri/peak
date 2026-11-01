package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.catalogEntry
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.figureMuscles
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.MuscleFigureView
import com.gabrieleforghieri.peak.design.MuscleGroupChip
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.MuscleId
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/**
 * **Firma condivisa** (iPhone: `Features/Workouts/MuscleDetailSheet.swift`). Proprietario: l'area **workouts**, che ne riscrive il corpo
 * (lo zoom a tutto schermo di `MuscleZoomView`, i consigli di setup); la firma non cambia.
 * Usata da: workouts (sessione, editor dei giorni), machines (dettaglio del macchinario).
 *
 * I muscoli coinvolti da un macchinario, con la figura fronte e retro, i chip dei muscoli e le note.
 */
@Composable
fun MuscleDetailSheet(exercise: ExerciseEntity, onDismiss: () -> Unit) {
    val muscles = exercise.figureMuscles
    val notes = exercise.notes.trim().ifEmpty { exercise.catalogEntry?.notes?.trim().orEmpty() }
    PeakSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.displayName(), style = PeakTheme.rounded(22, FontWeight.Bold), color = PeakTheme.textPrimary)
                MuscleGroupChip(exercise.group)
            }
            GlassCard {
                SectionHeader("Mappa muscolare")
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for ((view, label) in listOf(AnatomyView.front to "Fronte", AnatomyView.back to "Retro")) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MuscleFigureView(muscles, Modifier.fillMaxWidth().height(220.dp), view = view, zoom = false)
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
                SectionHeader("Come si usa")
                Text(notes, Modifier.padding(top = 8.dp), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary)
            }
        }
    }
}

/** I chip dei muscoli, colorati per gruppo (`MuscleChipGrid`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleChipGrid(muscles: List<MuscleId>, modifier: Modifier = Modifier) {
    val italian = Lang.isItalian()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in muscles) {
            Text(
                if (italian) m.title else m.titleEn,
                Modifier.background(m.group.tint.copy(alpha = 0.16f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                style = PeakTheme.rounded(12, FontWeight.Bold), color = m.group.tint,
            )
        }
    }
}
