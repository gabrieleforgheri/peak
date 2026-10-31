package com.gabrieleforghieri.peak.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.domain.MuscleGroup

/**
 * Le icone dell'interfaccia, tutte vettoriali (mai emoji): su iPhone sono SF Symbols, qui i disegni equivalenti (24 × 24, forme di
 * Material Symbols, Apache 2.0) scritti come percorsi. Si tingono con `tint` e crescono senza sgranare.
 */
object PeakIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) b.addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        return b.build()
    }

    val plus = icon("plus", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val minus = icon("minus", "M19,13H5v-2h14v2z")
    val chevronRight = icon("chevron_right", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    private val arrowUp = icon("arrow_up_right", "M9,5v2h6.59L4,18.59 5.41,20 17,8.41V15h2V5H9z")
    private val arrowDown = icon("arrow_down_right", "M19,9h-2v6.59L5.41,4 4,5.41 15.59,17H9v2h10V9z")
    fun arrow(up: Boolean) = if (up) arrowUp else arrowDown

    /** Scheda Allenamento (`figure.strengthtraining.traditional`): il manubrio. */
    val workouts = icon(
        "workouts",
        "M20.57,14.86L22,13.43 20.57,12 17,15.57 8.43,7 12,3.43 10.57,2 9.14,3.43 7.71,2 5.57,4.14 4.14,2.71 2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57 3.43,12 7,8.43 15.57,17 12,20.57 13.43,22l1.43,-1.43L16.29,22l2.14,-2.14 1.43,1.43 1.43,-1.43 -1.43,-1.43L22,16.29z",
    )

    /** Scheda Me (`person.crop.circle.fill`). */
    val me = icon(
        "me",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,6c1.93,0 3.5,1.57 3.5,3.5S13.93,13 12,13s-3.5,-1.57 -3.5,-3.5S10.07,6 12,6zM12,20c-2.03,0 -4.43,-0.82 -6.14,-2.88C7.55,15.8 9.68,15 12,15s4.45,0.8 6.14,2.12C16.43,19.18 14.03,20 12,20z",
    )

    /** Scheda Amici (`person.2.fill`). */
    val friends = icon(
        "friends",
        "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z",
    )

    private val body = icon(
        "body",
        "M20.5,6c-2.61,0.7 -5.67,1 -8.5,1s-5.89,-0.3 -8.5,-1L3,8c1.86,0.5 4,0.83 6,1v13h2v-6h2v6h2V9c2,-0.17 4.14,-0.5 6,-1l-0.5,-2zM12,6c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z",
    )
    private val run = icon(
        "run",
        "M13.49,5.48c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM9.89,19.38l1,-4.4 2.1,2v6h2v-7.5l-2.1,-2 0.6,-3c1.3,1.5 3.3,2.5 5.5,2.5v-2c-1.9,0 -3.5,-1 -4.3,-2.4l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1 -0.3,0 -0.5,0.1 -0.8,0.1l-5.2,2.2v4.7h2v-3.4l1.8,-0.7 -1.6,8.1 -4.9,-1 -0.4,2 7,1.4z",
    )
    private val target = icon(
        "target",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5z",
    )

    /** L'icona di un gruppo muscolare (i `symbol` di `MuscleGroup.swift`). */
    fun group(group: MuscleGroup): ImageVector = when (group) {
        MuscleGroup.petto, MuscleGroup.schiena, MuscleGroup.spalle -> body
        MuscleGroup.gambe -> run
        MuscleGroup.braccia -> workouts
        MuscleGroup.core -> target
    }
}
