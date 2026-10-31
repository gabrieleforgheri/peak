package com.gabrieleforghieri.peak.domain

import kotlin.math.abs
import kotlin.math.floor

/** Quali dischi mettere per lato per arrivare a un carico. */
object Plates {

    /** Tutti i dischi selezionabili nelle impostazioni, dal più pesante. */
    val catalog: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)

    private const val KG_PER_LB = WeightUnit.KILOGRAMS_PER_POUND

    /** I dischi americani, in kg (si salva sempre in kg): 45 / 35 / 25 / 10 / 5 / 2,5 lb. */
    val poundCatalog: List<Double> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5).map { it * KG_PER_LB }

    /** I tagli che le impostazioni mostrano nell'unità scelta. */
    fun catalog(unit: WeightUnit): List<Double> = if (unit == WeightUnit.kg) catalog else poundCatalog

    /** Dischi di serie dell'unità. */
    fun defaults(unit: WeightUnit): List<Double> =
        if (unit == WeightUnit.kg) listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25) else poundCatalog

    /** Bilanciere di serie: 20 kg, 45 lb. */
    fun defaultBar(unit: WeightUnit): Double = if (unit == WeightUnit.kg) 20.0 else 45 * KG_PER_LB

    /**
     * Una sbarra di serie (in kg) come la vuole l'unità: in lb si porta al multiplo di 5 lb più vicino (20 kg → 45 lb, non 44,09).
     * Come `barForUnit` in common/domain.ts.
     */
    fun bar(kilograms: Double, unit: WeightUnit): Double {
        if (unit != WeightUnit.lb || !(kilograms > 0)) return kilograms
        return Swift.round(kilograms / KG_PER_LB / 5) * 5 * KG_PER_LB
    }

    /** Peso predefinito della sbarra in base a pittogramma o nome, nell'unità scelta. */
    fun defaultBar(glyph: MachineGlyph?, name: String, unit: WeightUnit): Double = bar(defaultBar(glyph, name), unit)

    /**
     * Lo stesso in kg.
     *
     * DISACCORDO con common/domain.ts (`defaultBarWeightFor`), tradotto qui com'è in Swift: il web dà 20 kg anche ai
     * pittogrammi `landmine` e `preacherBench`, 15 a `smithMachine` anche se il nome non dice «smith»/«multipower», e non ha
     * la riga «plate loaded → 0» di Swift. Vedi il resoconto della fase 1 e `PlatesTest.defaultBarDisagreementWithTheWebIsKeptAsInSwift`.
     */
    fun defaultBar(glyph: MachineGlyph?, name: String): Double {
        val n = name.lowercase()
        if (n.contains("manubri") || n.contains("dumbbell")) return 0.0
        // «Curl Machine»: una macchina non ha il bilanciere, qualunque sia il pittogramma (il Multipower ha il suo)
        if ((n.contains("machine") || n.contains("macchina")) && glyph != MachineGlyph.smithMachine && !n.contains("smith") && !n.contains("multipower")) return 0.0
        if (glyph == MachineGlyph.plateLoaded || n.contains("plate loaded")) return 0.0
        if (glyph == MachineGlyph.ezBar || n.contains("ez")) return 10.0
        if (glyph == MachineGlyph.trapBar || n.contains("trap bar") || n.contains("quadra")) return 25.0
        if (n.contains("multipower") || n.contains("smith")) return 15.0
        if (glyph == MachineGlyph.barbell || glyph == MachineGlyph.benchFlat || glyph == MachineGlyph.benchIncline ||
            glyph == MachineGlyph.squatRack || glyph == MachineGlyph.hipThrust || n.contains("bilanciere") || n.contains("barbell")
        ) return 20.0
        return 0.0
    }

    /** «plate» all'inizio di una parola nel nome (plated, plate loaded), non «template». */
    private val PLATE_NAMED = Regex("\\bplate")

    private fun isPlateNamed(lowercasedName: String) = PLATE_NAMED.containsMatchIn(lowercasedName)

    /** Euristica iniziale del flag «Carico a dischi» (nuovo esercizio o scelta dal catalogo). */
    fun guessPlateLoaded(glyph: MachineGlyph?, name: String): Boolean =
        glyph == MachineGlyph.plateLoaded || isPlateNamed(name.lowercase())

    private val BAR_GLYPHS = setOf(
        MachineGlyph.barbell, MachineGlyph.benchFlat, MachineGlyph.benchIncline, MachineGlyph.squatRack,
        MachineGlyph.hipThrust, MachineGlyph.ezBar, MachineGlyph.trapBar,
    )

    /**
     * Il calcolatore compare dove c'è un bilanciere (anche panche, rack, hip thrust, EZ, trap bar) e per le plate loaded
     * (senza). Non con i manubri. `null` = niente calcolatore. Stessa regola del web (`plateMode` in common/domain.ts).
     */
    fun barBase(glyph: MachineGlyph, name: String, barWeight: Double, plateLoaded: Boolean = false): Double? {
        val lower = name.lowercase()
        if (barWeight > 0) return barWeight
        // L'euristica sul nome è in OR con il flag, quindi l'interruttore non può spegnere il calcolatore di un macchinario con «plate» nel nome.
        if (plateLoaded || glyph == MachineGlyph.plateLoaded || isPlateNamed(lower)) return 0.0
        return if (glyph in BAR_GLYPHS) (if (lower.contains("manubri")) null else barWeight) else null
    }

    data class Load(
        /** Dischi su un lato, dal più pesante (il primo infilato) al più leggero. */
        val perSide: List<Double>,
        /** Peso per lato che con i dischi disponibili non si riesce a caricare. */
        val leftover: Double,
    )

    /**
     * Avido dal disco più pesante; se lascia un resto (set di dischi su misura: 100 con [25, 20] darebbe 25 e 15 mancanti) si cerca
     * fra le combinazioni con al massimo 6 dischi per taglia quella col resto minore e meno dischi, con lo stesso ordine e lo
     * stesso limite di passi di `loadPlates` in common/domain.ts. `bar` è il peso già presente senza dischi (0 per le plate loaded).
     */
    fun load(total: Double, bar: Double, plates: List<Double>): Load {
        val start = maxOf(0.0, (total - bar) / 2)
        val sorted = mutableListOf<Double>()
        for (plate in plates.sortedDescending()) if (plate > 0 && sorted.lastOrNull() != plate) sorted += plate
        var remaining = start
        var perSide = mutableListOf<Double>()
        for (plate in sorted) {
            while (remaining + 0.0001 >= plate) {
                perSide += plate
                remaining -= plate
            }
        }
        if (remaining >= 0.0001) {
            var bestLeft = remaining
            var bestPlates: List<Double> = perSide.toList()
            var budget = 200_000
            val chosen = mutableListOf<Double>()

            fun walk(i: Int, rem: Double) {
                budget -= 1
                if (budget < 0) return
                if (bestLeft < 0.0001 && chosen.size >= bestPlates.size) return
                if (i == sorted.size) {
                    if (rem < bestLeft - 0.0001 || (abs(rem - bestLeft) <= 0.0001 && chosen.size < bestPlates.size)) {
                        bestLeft = rem
                        bestPlates = chosen.toList()
                    }
                    return
                }
                val p = sorted[i]
                var most = 0
                while (most < 6 && rem + 0.0001 >= p * (most + 1)) most += 1
                for (k in most downTo 0) {
                    repeat(k) { chosen += p }
                    walk(i + 1, rem - k * p)
                    repeat(k) { chosen.removeAt(chosen.lastIndex) }
                }
            }
            walk(0, start)
            remaining = bestLeft
            perSide = bestPlates.toMutableList()
        }
        return Load(perSide, if (remaining < 0.0001) 0.0 else Swift.round(remaining * 100) / 100)
    }
}

/** Gli arrotondamenti di Swift (`.rounded()`: metà lontano da zero) e quelli di JS, che qui devono dare gli stessi numeri. */
internal object Swift {
    /** `Double.rounded()` di Swift: la metà va via da zero. */
    fun round(x: Double): Double {
        if (x < 0) return -round(-x)
        val f = floor(x)
        return if (x - f >= 0.5) f + 1 else f
    }

    /** Arrotonda a due decimali come `(x * 100).rounded() / 100`. */
    fun round2(x: Double): Double = round(x * 100) / 100
}
