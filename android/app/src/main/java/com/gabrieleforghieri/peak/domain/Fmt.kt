package com.gabrieleforghieri.peak.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Testi dei numeri, come `Fmt` in Formatters.swift e `format.ts`. I numeri seguono la lingua (virgola in italiano, punto in
 * inglese). Le parole fisse («oggi», «ieri», «serie») sono italiane: chi mostra le passa da `tr`.
 */
object Fmt {

    private fun number(value: Double, minFraction: Int, maxFraction: Int, locale: Locale): String {
        val f = DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale))
        f.minimumFractionDigits = minFraction
        f.maximumFractionDigits = maxFraction
        f.isGroupingUsed = false
        f.roundingMode = java.math.RoundingMode.HALF_EVEN
        return f.format(value)
    }

    /** Peso formattato nell'unità scelta, es. "82,5 kg". Fino a due decimali: un disco da 1,25 kg deve leggersi 1,25. */
    fun weight(kilograms: Double, unit: WeightUnit, locale: Locale = Locale.getDefault(), includeSymbol: Boolean = true): String {
        val n = number(unit.fromKilograms(kilograms), 0, 2, locale)
        return if (includeSymbol) "$n ${unit.symbol}" else n
    }

    /** Volume totale: sopra la tonnellata passa a "12,4 t" per non riempire la riga di cifre. */
    fun volume(kilograms: Double, unit: WeightUnit, locale: Locale = Locale.getDefault()): String {
        val value = unit.fromKilograms(kilograms)
        if (value >= 1000) return "${number(value / 1000, 0, 1, locale)} ${if (unit == WeightUnit.kg) "t" else "klb"}"
        return "${number(value, 0, 0, locale)} ${unit.symbol}"
    }

    /** Durata in minuti e secondi, es. "1:30". */
    fun clock(seconds: Int): String {
        val s = maxOf(0, seconds)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** Variazione percentuale con segno, es. "+4,2%". Fra −0,05 e 0,05 è «0%», mai «-0%». */
    fun delta(percent: Double, locale: Locale = Locale.getDefault()): String {
        val rounded = Math.round(percent * 10) / 10.0
        val sign = if (rounded > 0) "+" else ""
        return sign + number(if (rounded == 0.0) 0.0 else percent, 0, 1, locale) + "%"
    }

    /** Durata in minuti, es. "45 min" o "1 h 5 min". */
    fun duration(seconds: Double): String {
        val minutes = maxOf(0, Swift.round(seconds).toInt() / 60)
        if (minutes < 60) return "$minutes min"
        return "${minutes / 60} h ${minutes % 60} min"
    }

    /** "80 kg × 5" — la riga base di una serie; a un lato "80 kg × 5 · 1 br" («1 gb» per le gambe, secondo `group`). `side` è la parola già tradotta. */
    fun setLine(weight: Double, reps: Int, unit: WeightUnit, singleArm: Boolean = false, group: MuscleGroup? = null, side: (String) -> String = { it }, locale: Locale = Locale.getDefault()): String =
        "${weight(weight, unit, locale)} × $reps" + if (singleArm) " · ${side((group ?: MuscleGroup.petto).side(MuscleGroup.SideKind.One))}" else ""

    data class NotebookSet(val weight: Double, val reps: Int, val singleArm: Boolean = false)

    /**
     * Riga da taccuino: serie consecutive con lo stesso peso (e lo stesso numero di bracci) raggruppate, ripetizioni unite da "·",
     * gruppi separati da " · ", es. "71,5 kg 8·6 · 54 kg 8". Un gruppo a un lato comincia con «1 br ». Come `notebookLine` in common/format.ts.
     */
    fun notebookLine(sets: List<NotebookSet>, unit: WeightUnit, group: MuscleGroup? = null, side: (String) -> String = { it }, locale: Locale = Locale.getDefault()): String {
        class Group(val weight: Double, val singleArm: Boolean, val reps: MutableList<Int>)
        val groups = mutableListOf<Group>()
        for (s in sets) {
            val last = groups.lastOrNull()
            if (last != null && last.weight == s.weight && last.singleArm == s.singleArm) last.reps += s.reps
            else groups += Group(s.weight, s.singleArm, mutableListOf(s.reps))
        }
        return groups.joinToString(" · ") {
            (if (it.singleArm) "${side((group ?: MuscleGroup.petto).side(MuscleGroup.SideKind.One))} " else "") +
                "${weight(it.weight, unit, locale)} ${it.reps.joinToString("·")}"
        }
    }

    /** «2/4 serie · 8–10 rip · 60 kg»: solo i campi compilati; senza peso scritto, l'ultimo usato. Le parole sono italiane: passa da `tr`. */
    fun targetLine(item: TemplateItem, done: Int, lastKilograms: Double?, unit: WeightUnit, locale: Locale = Locale.getDefault()): String {
        val parts = mutableListOf<String>()
        if (item.sets > 0) parts += "$done/${item.sets} serie"
        if (item.repsMax > 0) parts += if (item.repsMin == item.repsMax) "${item.repsMax} rip" else "${item.repsMin}–${item.repsMax} rip"
        if (item.weight != null) parts += weight(item.weight, unit, locale)
        else if (lastKilograms != null) parts += "ultimo ${weight(lastKilograms, unit, locale)}"
        return parts.joinToString(" · ")
    }
}
