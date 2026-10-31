package com.gabrieleforghieri.peak.domain

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * I gruppi muscolari. Il raw (il nome del caso) finisce nei documenti: mai cambiarlo.
 * I nomi dei casi sono quelli di `MuscleGroup.swift` e di `MUSCLE_GROUPS` in common/docs.ts.
 */
enum class MuscleGroup(val title: String) {
    petto("Petto"), schiena("Schiena"), gambe("Gambe"), spalle("Spalle"), braccia("Braccia"), core("Core");

    val raw: String get() = name

    /** Ordine di visualizzazione nelle liste. */
    val order: Int get() = ordinal

    /** Al core non si offre la scelta del lato; una serie già segnata mostra comunque il segno braccio. */
    val hasSides: Boolean get() = this != core

    enum class SideKind { Long, One, Two }

    /**
     * Le parole del lato di una serie, in italiano (chi mostra le passa da `tr`): «Un braccio» / «1 br» / «2 br»;
     * per le gambe «Una gamba» / «1 gb» / «2 gb». Come `sideText` in common/format.ts.
     */
    fun side(kind: SideKind): String = when {
        this == gambe && kind == SideKind.Long -> "Una gamba"
        this == gambe && kind == SideKind.One -> "1 gb"
        this == gambe -> "2 gb"
        kind == SideKind.Long -> "Un braccio"
        kind == SideKind.One -> "1 br"
        else -> "2 br"
    }

    companion object {
        fun of(raw: String?): MuscleGroup? = entries.firstOrNull { it.name == raw }
    }
}

/** Unità di misura mostrata a schermo. Sul disco i pesi sono sempre in kg. */
enum class WeightUnit(val raw: String) {
    kg("kg"), lb("lb");

    val symbol: String get() = raw

    /** Da kg (storage) al valore mostrato. */
    fun fromKilograms(kg: Double): Double = if (this == WeightUnit.kg) kg else kg * POUNDS_PER_KILOGRAM

    /** Dal valore inserito dall'utente ai kg da salvare. */
    fun toKilograms(value: Double): Double = if (this == WeightUnit.kg) value else value / POUNDS_PER_KILOGRAM

    /** Passo di default degli stepper quando l'esercizio non ne ha uno suo. */
    val defaultStep: Double get() = if (this == WeightUnit.kg) 2.5 else 5.0

    /**
     * Il passo del macchinario (salvato in kg) nel passo nativo dell'unità: in lb 2,5 kg diventa 5 lb, non 5,51.
     * Stessa tabella del web (`displayStep` in common/domain.ts).
     */
    fun displayStep(forKilograms: Double): Double {
        if (this != lb) return forKilograms
        return LB_STEPS[forKilograms] ?: maxOf(2.5, Math.round(forKilograms * POUNDS_PER_KILOGRAM / 2.5) * 2.5)
    }

    /** Passo della pesata corporea. */
    val bodyWeightStep: Double get() = if (this == WeightUnit.kg) 0.1 else 0.2

    companion object {
        const val POUNDS_PER_KILOGRAM = 2.204622621848776
        const val KILOGRAMS_PER_POUND = 0.45359237
        private val LB_STEPS = mapOf(1.25 to 2.5, 2.0 to 5.0, 2.5 to 5.0, 5.0 to 10.0, 10.0 to 20.0)

        fun of(raw: String?): WeightUnit? = entries.firstOrNull { it.raw == raw }
    }
}

/** Le tinte dell'accento (i colori veri stanno in design/Theme). Il raw è quello di `ACCENTS` in common/docs.ts. */
enum class AccentPalette(val title: String) {
    lime("Lime"), ember("Brace"), ice("Ghiaccio"), violet("Viola"), coral("Corallo");

    companion object {
        fun of(raw: String?): AccentPalette? = entries.firstOrNull { it.name == raw }
    }
}

/** Un pezzo di disegno di un pittogramma, in coordinate normalizzate 0…1 (y verso il basso). */
sealed class GlyphShape {
    /** Linea sottile: telai, cavi, basamenti. */
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : GlyphShape()

    /** Linea spessa: bilancieri, pedane, schienali inclinati. */
    data class Bar(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : GlyphShape()

    /** Rettangolo pieno con angoli morbidi: sedute, pacchi pesi, imbottiture. */
    data class SolidRect(val x: Float, val y: Float, val w: Float, val h: Float) : GlyphShape()

    /** Cerchio pieno: impugnature, rulli, teste. */
    data class Dot(val cx: Float, val cy: Float, val r: Float) : GlyphShape()

    /** Cerchio vuoto: dischi, pulegge. */
    data class Ring(val cx: Float, val cy: Float, val r: Float) : GlyphShape()
}

// Le classi generate usano questi nomi come costruttori.
@Suppress("FunctionName") fun Line(x1: Float, y1: Float, x2: Float, y2: Float) = GlyphShape.Line(x1, y1, x2, y2)
@Suppress("FunctionName") fun Bar(x1: Float, y1: Float, x2: Float, y2: Float) = GlyphShape.Bar(x1, y1, x2, y2)
@Suppress("FunctionName") fun SolidRect(x: Float, y: Float, w: Float, h: Float) = GlyphShape.SolidRect(x, y, w, h)
@Suppress("FunctionName") fun Dot(cx: Float, cy: Float, r: Float) = GlyphShape.Dot(cx, cy, r)
@Suppress("FunctionName") fun Ring(cx: Float, cy: Float, r: Float) = GlyphShape.Ring(cx, cy, r)

/**
 * Date nel formato di Peak: ISO 8601 UTC a secondi interi (`2026-10-14T12:00:00Z`), le stesse dei backup e dei documenti
 * sincronizzati. In memoria sono millisecondi dall'epoca. Si tronca, non si arrotonda. Come `isoSec`/`parseIso` in common/docs.ts.
 */
object PeakDates {
    private val MIN_MS = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli()
    private val MAX_MS = Instant.parse("2100-01-01T00:00:00Z").toEpochMilli()
    private val ISO = Regex("""^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?Z$""")

    /** Tronca ai secondi interi (`floorSec`). */
    fun floorSec(ms: Long): Long = Math.floorDiv(ms, 1000L) * 1000L

    /** `YYYY-MM-DDTHH:MM:SSZ`, senza millisecondi. */
    fun string(ms: Long): String = Instant.ofEpochMilli(floorSec(ms)).toString()

    /** Con o senza millisecondi (backup vecchi); `null` se non è una data valida fra il 2000 e il 2100 (`parseIso`). */
    fun parse(text: String?): Long? {
        if (text == null || !ISO.matches(text)) return null
        val ms = try { Instant.parse(text).toEpochMilli() } catch (_: DateTimeParseException) { return null }
        if (ms < MIN_MS || ms > MAX_MS) return null
        // andata e ritorno: 2023-02-30 o T24:00:00 non esistono
        return if (Instant.ofEpochMilli(ms).toString().take(19) == text.take(19)) floorSec(ms) else null
    }
}

/** Gli uuid dei documenti: maiuscoli, come li scrive Swift (`normUuid`/`newUuid` in common/docs.ts). */
object Uuids {
    private val UUID_RE = Regex("^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$")

    fun normalize(value: String?): String? = value?.uppercase()?.takeIf { UUID_RE.matches(it) }

    fun new(): String = java.util.UUID.randomUUID().toString().uppercase()

    /** Stesso uuid del web (`DEFAULT_PROGRAM`): «La mia scheda» è la stessa su ogni dispositivo. */
    const val DEFAULT_PROGRAM = "00000000-0000-4000-8000-5045414B0001"
}
