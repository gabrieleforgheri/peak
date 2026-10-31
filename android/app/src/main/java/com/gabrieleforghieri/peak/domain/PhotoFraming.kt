package com.gabrieleforghieri.peak.domain

/**
 * Inquadratura della foto dentro il riquadro quadrato delle miniature (`PhotoFraming.swift`, `PhotoFraming` in common/domain.ts).
 * Una foto non è quadrata: riempiendo il riquadro, una delle due dimensioni sborda. `offset` è quanto la si è spostata lungo
 * quell'asse, da −1 a +1, con 0 al centro.
 */
object PhotoFraming {
    /** Di quanto sborda l'immagine, riempiendo un quadrato di lato `side`. */
    fun overflow(imageAspect: Double, side: Double): Double {
        if (!(imageAspect > 0) || !(side > 0)) return 0.0
        return if (imageAspect > 1) side * (imageAspect - 1) else side * (1 / imageAspect - 1)
    }

    /** Lo spostamento da applicare sull'asse che sborda. Fuori da −1…1 non ha senso: si vedrebbe il bordo vuoto. */
    fun offsetPoints(normalized: Double, imageAspect: Double, side: Double): Double =
        normalized.coerceIn(-1.0, 1.0) * overflow(imageAspect, side) / 2

    /** `true` se lo spostamento agisce in verticale (immagine più alta che larga). */
    fun isVertical(imageAspect: Double): Boolean = imageAspect <= 1

    /** Converte una trascinata nella variazione dell'offset normalizzato. */
    fun normalizedDelta(dragPoints: Double, imageAspect: Double, side: Double): Double {
        val total = overflow(imageAspect, side) / 2
        return if (total > 0) dragPoints / total else 0.0
    }
}
