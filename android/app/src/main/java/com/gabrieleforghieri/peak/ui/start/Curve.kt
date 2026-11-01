package com.gabrieleforghieri.peak.ui.start

/**
 * Le curve del web (`cubic-bezier` del CSS) e i fotogrammi chiave, per portare le animazioni a tempo dei disegni del primo avvio
 * (`Curve` in `SummitDrawView.swift`). Funzioni pure del tempo: le prove ne controllano gli estremi.
 */
object Curve {
    data class Bezier(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

    val easeOut = Bezier(0.23, 1.0, 0.32, 1.0)     // --ease-out
    val spring = Bezier(0.34, 1.56, 0.64, 1.0)     // la molla con un po' di rimbalzo
    val ease = Bezier(0.25, 0.1, 0.25, 1.0)        // `ease` del CSS
    val linear = Bezier(0.0, 0.0, 1.0, 1.0)
    val easeInOut = Bezier(0.42, 0.0, 0.58, 1.0)

    fun bezier(c: Bezier, x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        if (c == linear) return x
        fun at(s: Double, a: Double, b: Double) = 3 * (1 - s) * (1 - s) * s * a + 3 * (1 - s) * s * s * b + s * s * s
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val m = (lo + hi) / 2
            if (at(m, c.x1, c.x2) < x) lo = m else hi = m
        }
        return at((lo + hi) / 2, c.y1, c.y2)
    }

    fun clamp(x: Double): Double = minOf(1.0, maxOf(0.0, x))

    /** Il valore alla posizione `p` (0…1) fra fotogrammi chiave `(posizione, valore)`; la curva vale in ogni tratto, come nel CSS. */
    fun key(p: Double, frames: List<Pair<Double, Double>>, curve: Bezier = ease): Double {
        val first = frames.firstOrNull() ?: return 0.0
        val last = frames.last()
        if (p <= first.first) return first.second
        if (p >= last.first) return last.second
        for (i in 1 until frames.size) {
            if (p <= frames[i].first) {
                val a = frames[i - 1]
                val b = frames[i]
                if (b.first == a.first) return b.second
                return a.second + (b.second - a.second) * bezier(curve, (p - a.first) / (b.first - a.first))
            }
        }
        return last.second
    }
}
