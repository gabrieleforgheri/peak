package com.gabrieleforghieri.peak.ui.start

import com.gabrieleforghieri.peak.design.PeakIcons

/** Le icone solo dell'area Avvio (su iPhone sono SF Symbols): vettoriali 24 × 24, si tingono con `tint`. */
object StartIcons {
    val chevronLeft = PeakIcons.icon("chevron_left", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")
    val chevronRight = PeakIcons.chevronRight

    /** `mountain.2.fill` */
    val mountain = PeakIcons.icon("mountain", "M14,6l-3.75,5 2.85,3.8 -1.6,1.2C9.81,13.75 7,10 7,10l-6,8h22L14,6z")

    /** `flame.fill` */
    val flame = PeakIcons.icon("flame", "M12,2C12,2 5.5,8.2 5.5,14a6.5,6.5 0,0 0,13 0c0,-2.3 -1.2,-4 -2.4,-5.2 -0.2,1.8 -1,3 -2.2,3.4 0.4,-4 -0.6,-7.6 -2.4,-10.2z")

    /** `link` */
    val link = PeakIcons.icon(
        "link",
        "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
    )

    /** `crown.fill` */
    val crown = PeakIcons.icon("crown", "M3,18h18v2H3zM3,7l4.5,4L12,4l4.5,7L21,7l-2,9H5z")

    /** `star.fill` */
    val star = PeakIcons.icon("star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")

    /** `tray` */
    val tray = PeakIcons.icon(
        "tray",
        "M19,3H4.99c-1.11,0 -1.98,0.89 -1.98,2L3,19c0,1.1 0.88,2 1.99,2H19c1.1,0 2,-0.9 2,-2V5c0,-1.11 -0.9,-2 -2,-2zM19,15h-4c0,1.66 -1.35,3 -3,3s-3,-1.34 -3,-3H4.99V5H19v10z",
    )

    /** `arrow.triangle.merge` */
    val merge = PeakIcons.icon("merge", "M17,20.41L18.41,19 15,15.59 13.59,17 17,20.41zM7.5,8H11v5.59L5.59,19 7,20.41l6,-6V8h3.5L12,3.5 7.5,8z")
}
