package com.gabrieleforghieri.peak.util

import java.util.Locale

/** La lingua dell'app (`Utils/Lang.swift`): per ora segue il telefono; l'inglese vale per ogni lingua che non sia l'italiano. */
object Lang {
    fun isItalian(locale: Locale = Locale.getDefault()): Boolean = locale.language == "it"
}
