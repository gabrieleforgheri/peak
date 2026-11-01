package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.ui.graphics.vector.ImageVector
import com.gabrieleforghieri.peak.design.PeakIcons

/** Le icone dell'area Macchinari (forme di Material Symbols, Apache 2.0): gli SF Symbols di `Features/Maxes/`. */
object MachinesIcons {
    /** `archivebox` */
    val archive: ImageVector = PeakIcons.icon(
        "archive",
        "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.16,0.55L3.46,5.23C3.17,5.57 3,6.02 3,6.5V19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27zM12,17.5L6.5,12H10v-2h4v2h3.5L12,17.5zM5.12,5l0.81,-1h12l0.94,1H5.12z",
    )

    /** `trash` */
    val trash: ImageVector = PeakIcons.icon("trash", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")

    /** `slider.horizontal.3` */
    val sliders: ImageVector = PeakIcons.icon(
        "sliders",
        "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z",
    )

    /** `plus.circle` */
    val plusCircle: ImageVector = PeakIcons.icon(
        "plus_circle",
        "M13,7h-2v4H7v2h4v4h2v-4h4v-2h-4V7zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z",
    )

    /** `arrow.up.circle.fill` */
    val arrowUpCircle: ImageVector = PeakIcons.icon(
        "arrow_up_circle",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,16h-2v-4.17l-1.59,1.58L8,12l4,-4 4,4 -1.41,1.41L13,11.83V16z",
    )

    /** `chevron.left` */
    val chevronLeft: ImageVector = PeakIcons.icon("chevron_left", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")

    /** `photo.on.rectangle` */
    val photos: ImageVector = PeakIcons.icon(
        "photos",
        "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4zM2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z",
    )

    /** `icloud.slash` */
    val cloudOff: ImageVector = PeakIcons.icon(
        "cloud_off",
        "M19.35,10.04C18.67,6.59 15.64,4 12,4c-1.48,0 -2.85,0.43 -4.01,1.17l1.46,1.46C10.21,6.23 11.08,6 12,6c3.04,0 5.5,2.46 5.5,5.5v0.5H19c1.66,0 3,1.34 3,3 0,1.13 -0.64,2.11 -1.56,2.62l1.45,1.45C23.16,18.16 24,16.68 24,15c0,-2.64 -2.05,-4.78 -4.65,-4.96zM3,5.27l2.75,2.74C2.56,8.15 0,10.77 0,14c0,3.31 2.69,6 6,6h11.73l2,2L21,20.73 4.27,4 3,5.27zM7.73,10l8,8H6c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4h1.73z",
    )

    /** `link.badge.plus` */
    val link: ImageVector = PeakIcons.icon(
        "link",
        "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
    )

    /** `arrow.up.right` (il chip «Muscoli») */
    val arrowUpRight: ImageVector = PeakIcons.icon("arrow_up_right", "M9,5v2h6.59L4,18.59 5.41,20 17,8.41V15h2V5H9z")

    /** `hand.draw`: un dito che trascina */
    val drag: ImageVector = PeakIcons.icon(
        "drag",
        "M9,11.24V7.5C9,6.12 10.12,5 11.5,5S14,6.12 14,7.5v3.74c1.21,-0.81 2,-2.18 2,-3.74C16,5.01 13.99,3 11.5,3S7,5.01 7,7.5c0,1.56 0.79,2.93 2,3.74zM18.84,15.87l-4.54,-2.26c-0.17,-0.07 -0.35,-0.11 -0.54,-0.11H13v-6c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,6.67 10,7.5v10.74l-3.43,-0.72c-0.08,-0.01 -0.15,-0.03 -0.24,-0.03 -0.31,0 -0.59,0.13 -0.79,0.33l-0.79,0.8 4.94,4.94c0.27,0.27 0.65,0.44 1.06,0.44h6.79c0.75,0 1.33,-0.55 1.44,-1.28l0.75,-5.27c0.01,-0.07 0.02,-0.14 0.02,-0.2 0,-0.62 -0.38,-1.16 -0.91,-1.38z",
    )

    /** `chevron.down` per «Altro» */
    val chevronDown: ImageVector = PeakIcons.icon("chevron_down", "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z")
}
