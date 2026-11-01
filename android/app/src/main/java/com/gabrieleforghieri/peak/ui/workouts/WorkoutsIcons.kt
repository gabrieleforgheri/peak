package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.ui.graphics.vector.ImageVector
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.domain.MedalID
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.TemplateIcon

/**
 * Le icone solo dell'area Allenamenti (gli SF Symbols delle viste di `Features/Workouts/`), disegnate come quelle di `PeakIcons`:
 * 24 × 24, forme di Material Symbols (Apache 2.0) scritte come percorsi. Mai emoji.
 */
object WorkoutsIcons {
    val back = PeakIcons.icon("back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val play = PeakIcons.icon("play", "M8,5v14l11,-7z")
    val bolt = PeakIcons.icon(
        "bolt",
        "M11,21h-1l1,-7H7.5c-0.58,0 -0.57,-0.32 -0.38,-0.66 0.19,-0.34 0.05,-0.08 0.07,-0.12C8.48,10.94 10.42,7.54 13,3h1l-1,7h3.5c0.49,0 0.56,0.33 0.47,0.51l-0.07,0.15C12.96,17.55 11,21 11,21z",
    )
    val trash = PeakIcons.icon("trash", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    val flame = PeakIcons.icon("flame", "M12,2C12,2 6,8.5 6,14a6,6 0 0 0 12,0c0,-2.2 -1.1,-4 -2.2,-5.4C15.5,10 14.5,11 13.5,11 13.5,8 12,5.5 12,2z")
    val list = PeakIcons.icon("list", "M3,13h2v-2H3v2zM3,17h2v-2H3v2zM3,9h2V7H3v2zM7,13h14v-2H7v2zM7,17h14v-2H7v2zM7,7v2h14V7H7z")
    val pencil = PeakIcons.icon(
        "pencil",
        "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
    )
    val chevronUp = PeakIcons.icon("chevronUp", "M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z")
    val chevronDown = PeakIcons.icon("chevronDown", "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z")
    val swap = PeakIcons.icon("swap", "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z")
    val info = PeakIcons.icon("info", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z")
    val more = PeakIcons.icon(
        "more",
        "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
    )
    val history = PeakIcons.icon(
        "history",
        "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z",
    )
    val redo = PeakIcons.icon("redo", "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z")
    val checkCircle = PeakIcons.icon(
        "checkCircle",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z",
    )
    val plusCircle = PeakIcons.icon(
        "plusCircle",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM17,13h-4v4h-2v-4H7v-2h4V7h2v4h4v2z",
    )
    val clock = PeakIcons.icon(
        "clock",
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
    )
    val listNumber = PeakIcons.icon(
        "listNumber",
        "M2,17h2v0.5H3v1h1v0.5H2v1h3v-4H2v1zM3,8h1V4H2v1h1v3zM2,11h1.8L2,13.1v0.9h3v-1H3.2L5,10.9V10H2v1zM7,5v2h14V5H7zM7,19h14v-2H7v2zM7,13h14v-2H7v2z",
    )
    val arrowUpRight = PeakIcons.icon("arrowUpRight", "M6,6v2h8.59L5,17.59 6.41,19 16,9.41V18h2V6z")
    val trophy = PeakIcons.icon(
        "trophy",
        "M19,5h-2V3H7v2H5c-1.1,0 -2,0.9 -2,2v1c0,2.55 1.92,4.63 4.39,4.94 0.63,1.5 1.98,2.63 3.61,2.96V19H7v2h10v-2h-4v-3.1c1.63,-0.33 2.98,-1.46 3.61,-2.96C19.08,12.63 21,10.55 21,8V7c0,-1.1 -0.9,-2 -2,-2zM5,8V7h2v3.82C5.84,10.4 5,9.3 5,8zM19,8c0,1.3 -0.84,2.4 -2,2.82V7h2v1z",
    )
    val scale = PeakIcons.icon(
        "scale",
        "M5,3h14c1.1,0 2,0.9 2,2v14c0,1.1 -0.9,2 -2,2H5c-1.1,0 -2,-0.9 -2,-2V5c0,-1.1 0.9,-2 2,-2zM12,6.5c-2.5,0 -4.5,1.6 -4.5,3.5h9c0,-1.9 -2,-3.5 -4.5,-3.5z",
    )
    val dumbbell = PeakIcons.icon(
        "dumbbell",
        "M20.57,14.86L22,13.43 20.57,12 17,15.57 8.43,7 12,3.43 10.57,2 9.14,3.43 7.71,2 5.57,4.14 4.14,2.71 2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57 3.43,12 7,8.43 15.57,17 12,20.57 13.43,22l1.43,-1.43L16.29,22l2.14,-2.14 1.43,1.43 1.43,-1.43 -1.43,-1.43L22,16.29z",
    )

    // ---- le icone dei giorni della scheda (`TemplateIcon.named`) ----

    private val star = PeakIcons.icon("star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")
    private val heart = PeakIcons.icon(
        "heart",
        "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z",
    )
    private val timer = PeakIcons.icon(
        "timer",
        "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
    )
    private val repeat = PeakIcons.icon("repeat", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
    private val compass = PeakIcons.icon(
        "compass",
        "M12,10.9c-0.61,0 -1.1,0.49 -1.1,1.1s0.49,1.1 1.1,1.1c0.61,0 1.1,-0.49 1.1,-1.1s-0.49,-1.1 -1.1,-1.1zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM14.19,14.19L6,18l3.81,-8.19L18,6l-3.81,8.19z",
    )
    private val target = PeakIcons.icon(
        "target",
        "M12,2A10,10 0 1,0 12,22A10,10 0 1,0 12,2zM12,6A6,6 0 1,1 12,18A6,6 0 1,1 12,6zM12,10A2,2 0 1,0 12,14A2,2 0 1,0 12,10z",
    )
    private val weight = PeakIcons.icon(
        "weight",
        "M12,2a3,3 0 0 0 -3,3c0,0.55 0.15,1.06 0.4,1.5C6.8,7.7 5,10.1 5,13v6c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2v-6c0,-2.9 -1.8,-5.3 -4.4,-6.5C14.85,6.06 15,5.55 15,5a3,3 0 0 0 -3,-3z",
    )
    private val medal = PeakIcons.icon(
        "medal",
        "M17,10.43V2H7v8.43c0,0.35 0.18,0.68 0.49,0.86l4.18,2.51 -0.99,2.34 -3.41,0.29 2.59,2.24L9.07,22 12,20.23 14.93,22l-0.78,-3.33 2.59,-2.24 -3.41,-0.29 -0.99,-2.34 4.18,-2.51c0.31,-0.18 0.49,-0.51 0.49,-0.86zM13,11.41l-1,0.6 -1,-0.6V4h2v7.41z",
    )
    private val bike = PeakIcons.icon(
        "bike",
        "M15.5,5.5c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM5,12c-2.8,0 -5,2.2 -5,5s2.2,5 5,5 5,-2.2 5,-5 -2.2,-5 -5,-5zM5,20.5c-1.9,0 -3.5,-1.6 -3.5,-3.5s1.6,-3.5 3.5,-3.5 3.5,1.6 3.5,3.5 -1.6,3.5 -3.5,3.5zM10.8,10.5l2.4,-2.4 0.8,0.8c1.3,1.3 3,2.1 5.1,2.1V9c-1.5,0 -2.7,-0.6 -3.6,-1.5l-1.9,-1.9c-0.5,-0.4 -1,-0.6 -1.6,-0.6s-1.1,0.2 -1.4,0.6L7.8,8.4c-0.4,0.4 -0.6,0.9 -0.6,1.4 0,0.6 0.2,1.1 0.6,1.4L11,14v5h2v-6.2l-2.2,-2.3zM19,12c-2.8,0 -5,2.2 -5,5s2.2,5 5,5 5,-2.2 5,-5 -2.2,-5 -5,-5zM19,20.5c-1.9,0 -3.5,-1.6 -3.5,-3.5s1.6,-3.5 3.5,-3.5 3.5,1.6 3.5,3.5 -1.6,3.5 -3.5,3.5z",
    )
    private val fist = PeakIcons.icon(
        "fist",
        "M23,5.5V20c0,2.2 -1.8,4 -4,4h-7.3c-1.08,0 -2.1,-0.43 -2.85,-1.19L1,14.83s1.26,-1.23 1.3,-1.25c0.22,-0.19 0.49,-0.29 0.79,-0.29 0.22,0 0.42,0.06 0.6,0.16 0.04,0.01 4.31,2.46 4.31,2.46V4c0,-0.83 0.67,-1.5 1.5,-1.5S11,3.17 11,4v7h1V1.5c0,-0.83 0.67,-1.5 1.5,-1.5S15,0.67 15,1.5V11h1V2.5c0,-0.83 0.67,-1.5 1.5,-1.5s1.5,0.67 1.5,1.5V11h1V5.5c0,-0.83 0.67,-1.5 1.5,-1.5s1.5,0.67 1.5,1.5z",
    )
    private val pulse = PeakIcons.icon("pulse", "M2,12h4l2,-6 4,13 3,-9 1.5,2H22v2h-6.5l-0.5,-0.7 -2.5,7.7 -3.5,-11.5 -1,3.5H2v-2z")
    private val rocket = PeakIcons.icon("rocket", "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z")
    private val crown = PeakIcons.icon("crown", "M5,16L3,5l5.5,5L12,4l3.5,6L21,5l-2,11H5zM19,19c0,0.6 -0.4,1 -1,1H6c-0.6,0 -1,-0.4 -1,-1v-1h14v1z")
    private val person = PeakIcons.icon(
        "person",
        "M20.5,6c-2.61,0.7 -5.67,1 -8.5,1s-5.89,-0.3 -8.5,-1L3,8c1.86,0.5 4,0.83 6,1v13h2v-6h2v6h2V9c2,-0.17 4.14,-0.5 6,-1l-0.5,-2zM12,6c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z",
    )
    private val mountain = PeakIcons.icon("mountain", "M14,6l-3.75,5 2.85,3.8 -1.6,1.2C9.81,13.75 7,10 7,10l-6,8h22L14,6z")

    private val named: Map<String, ImageVector> = mapOf(
        "dumbbell" to dumbbell, "flame" to flame, "bolt" to bolt, "mountain" to mountain, "trophy" to trophy, "star" to star,
        "timer" to timer, "repeat" to repeat, "compass" to compass, "scale" to scale, "target" to target, "heart" to heart,
        "weight" to weight, "medal" to medal, "bike" to bike, "fist" to fist, "pulse" to pulse, "rocket" to rocket,
        "crown" to crown, "person" to person,
    )

    /** L'icona di un giorno (`TemplateIcon.symbol`): quella del gruppo (`muscle:petto`) o una dell'elenco; il manubrio se non la conosce. */
    fun template(icon: String): ImageVector {
        TemplateIcon.group(icon)?.let { return PeakIcons.group(it) }
        return named[icon] ?: dumbbell
    }

    /** L'icona di una medaglia (`MedalID.symbol`). */
    fun medal(kind: MedalID): ImageVector = when (kind) {
        MedalID.sessions -> dumbbell
        MedalID.records -> trophy
        MedalID.streak -> flame
        MedalID.volume -> weight
        MedalID.strength -> bolt
        MedalID.variety -> compass
        MedalID.friends -> PeakIcons.friends
    }

    /** Il gruppo delle icone scelte nell'editor: i sei gruppi, poi le venti dell'elenco. */
    val templateChoices: List<String> = MuscleGroup.entries.map { "muscle:${it.raw}" } + TemplateIcon.named
}
