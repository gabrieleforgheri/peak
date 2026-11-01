package com.gabrieleforghieri.peak.ui.friends

import com.gabrieleforghieri.peak.design.PeakIcons

/** Le icone dell'area Amici: gli SF Symbols di `Features/Friends/`, come disegni vettoriali (forme di Material Symbols, Apache 2.0). */
object FriendsIcons {
    /** `qrcode` */
    val qr = PeakIcons.icon(
        "qr_code",
        "M3,11h8V3H3V11zM5,5h4v4H5V5zM3,21h8v-8H3V21zM5,15h4v4H5V15zM13,3v8h8V3H13zM19,9h-4V5h4V9zM19,19h2v2h-2V19zM13,13h2v2h-2V13zM15,15h2v2h-2V15zM13,17h2v2h-2V17zM15,19h2v2h-2V19zM17,17h2v2h-2V17zM17,13h2v2h-2V13zM19,15h2v2h-2V15z",
    )

    /** `person.crop.circle.badge.checkmark` */
    val connect = PeakIcons.icon(
        "how_to_reg",
        "M9,17l3,-2.94c-0.39,-0.04 -0.68,-0.06 -1,-0.06 -2.67,0 -8,1.34 -8,4v2h9l-3,-3zM11,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM15.47,20.5L12,17l1.4,-1.41 2.07,2.08 5.13,-5.17 1.4,1.41z",
    )

    /** `person.badge.minus` */
    val removeFriend = PeakIcons.icon(
        "person_remove",
        "M14,8c0,-2.21 -1.79,-4 -4,-4S6,5.79 6,8s1.79,4 4,4S14,10.21 14,8zM17,10v2h6v-2H17zM2,18v2h16v-2c0,-2.66 -5.33,-4 -8,-4S2,15.34 2,18z",
    )

    /** `square.and.arrow.up` */
    val share = PeakIcons.icon(
        "share",
        "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z",
    )

    /** `doc.on.doc` */
    val copy = PeakIcons.icon(
        "content_copy",
        "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z",
    )

    /** `flame.fill` */
    val flame = PeakIcons.icon(
        "local_fire_department",
        "M19.48,12.35c-1.57,-4.08 -7.16,-4.3 -5.81,-10.23 0.1,-0.44 -0.37,-0.78 -0.75,-0.55C9.29,3.71 6.68,8 8.87,13.62c0.18,0.46 -0.36,0.89 -0.75,0.59 -1.81,-1.37 -2,-3.34 -1.84,-4.75 0.06,-0.52 -0.62,-0.77 -0.91,-0.34C4.69,10.16 4,11.84 4,14.37c0.38,5.6 5.11,7.32 6.81,7.54 2.43,0.31 5.06,-0.14 6.95,-1.87 2.08,-1.93 2.84,-5.01 1.72,-7.69z",
    )

    /** `star.fill` */
    val star = PeakIcons.icon("star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")

    /** `chevron.left` (il tasto indietro del profilo). */
    val back = PeakIcons.icon("chevron_left", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")
}
