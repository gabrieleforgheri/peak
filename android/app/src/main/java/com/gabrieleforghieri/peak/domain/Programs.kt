package com.gabrieleforghieri.peak.domain

import java.time.Instant
import java.time.ZoneId

/** Un giorno della scheda, per i conti di [Programs]. */
data class DayRef(val uuid: String, val name: String, val program: String, val weekday: Int)

/** Una scheda, per i conti di [Programs]. */
data class ProgramRef(val uuid: String, val useDays: Boolean)

/**
 * L'icona di un giorno della scheda. Stessi nomi del web (`TEMPLATE_ICONS` in common/docs.ts): `muscle:<gruppo>`, `glyph:<pittogramma>`
 * e `body:<x>` sono quelli del web. Come `suggestTemplateIcon` in common/template-icon.ts: stesse parole e stesso ordine.
 */
object TemplateIcon {
    /** Quelle che si possono scegliere (oltre ai gruppi muscolari). */
    val named: List<String> = listOf(
        "dumbbell", "flame", "bolt", "mountain", "trophy", "star", "timer", "repeat", "compass", "scale",
        "target", "heart", "weight", "medal", "bike", "fist", "pulse", "rocket", "crown", "person",
    )

    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""\b(leg|legs|gamb\w*|squat\w*|quad\w*|glut\w*|polpacc\w*|calf|calves|lower)\b""") to "muscle:gambe",
        Regex("""\b(pull|schien\w*|back|row|dors\w*|lat)\b""") to "muscle:schiena",
        Regex("""\b(push|petto|chest|pett\w*|bench|panca)\b""") to "muscle:petto",
        Regex("""\b(spall\w*|shoulder\w*|delt\w*)\b""") to "muscle:spalle",
        Regex("""\b(bracc\w*|arm|arms|bicip\w*|tricip\w*|curl)\b""") to "muscle:braccia",
        Regex("""\b(core|abs|addom\w*)\b""") to "muscle:core",
        Regex("""\b(cardio|run|corsa|hiit)\b""") to "heart",
        Regex("""\b(forza|strength|power)\b""") to "bolt",
    )

    /** "" se il nome non dice niente: allora si mostra il manubrio. */
    fun suggest(name: String): String {
        val n = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return rules.firstOrNull { it.first.containsMatchIn(n) }?.second ?: ""
    }

    /** L'icona da mostrare: quella scelta, altrimenti quella dedotta dal nome. */
    fun resolved(icon: String, name: String): String = if (icon.isEmpty()) suggest(name) else icon

    fun group(icon: String): MuscleGroup? = if (icon.startsWith("muscle:")) MuscleGroup.of(icon.removePrefix("muscle:")) else null
}

/** La scheda in uso, i suoi giorni e quello di oggi. Stessi conti di `activeProgram`/`daysOf`/`todayDay` del web (derive.ts). */
object Programs {
    /** La scheda in uso: quella dell'impostazione `activeProgram`, altrimenti la prima. */
    fun <T> active(programs: List<T>, uuid: (T) -> String, activeProgram: String): T? =
        programs.firstOrNull { uuid(it) == activeProgram.uppercase() } ?: programs.firstOrNull()

    /** I giorni di una scheda, nell'ordine in cui li hai messi. Senza scheda: quelli che non stanno in nessuna. */
    fun daysOf(program: ProgramRef?, templates: List<DayRef>): List<DayRef> = templates.filter { it.program == (program?.uuid ?: "") }

    /** Il giorno fisso di oggi, solo se la scheda usa i giorni della settimana; altrimenti niente (si sceglie a mano). */
    fun today(program: ProgramRef?, templates: List<DayRef>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): DayRef? {
        if (program?.useDays != true) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).dayOfWeek.value - 1   // 0 = lunedì
        return daysOf(program, templates).firstOrNull { it.weekday == today }
    }

    /** Nomi dei giorni della settimana, 0 = lunedì. */
    val weekdays = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")
}
