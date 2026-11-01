package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.util.tr

/**
 * «2 br | 1 br» (gambe «2 gb | 1 gb»): una pillola piccola accanto al titolo del peso (`Design/SideSegment.swift`). `singleArm` è il flag
 * della serie o del massimale; il core non la mostra mai (vedi `MuscleGroup.hasSides`: chi la usa decide se mostrarla).
 */
@Composable
fun SideSegment(singleArm: Boolean, onChange: (Boolean) -> Unit, group: MuscleGroup, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val label = tr(group.side(MuscleGroup.SideKind.Long))
    Row(
        modifier.background(PeakTheme.cardStrong, RoundedCornerShape(9.dp)).padding(2.dp)
            .semantics { contentDescription = label },
    ) {
        for (value in listOf(false, true)) {
            val selected = singleArm == value
            Text(
                tr(group.side(if (value) MuscleGroup.SideKind.One else MuscleGroup.SideKind.Two)),
                Modifier
                    .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(7.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {
                        if (singleArm != value) { onChange(value); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    }
                    .semantics { this.selected = selected }
                    .padding(vertical = 4.dp, horizontal = 9.dp),
                style = PeakTheme.rounded(12, FontWeight.Bold), color = if (selected) PeakTheme.textPrimary else PeakTheme.textTertiary,
            )
        }
    }
}
