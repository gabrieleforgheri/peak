package com.gabrieleforghieri.peak.domain

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Una voce del catalogo degli esercizi (`common/catalog.json`, la stessa del server e del web).
 * L'`id` non cambia mai: è ciò che fa combaciare gli esercizi di persone diverse.
 */
@Serializable
data class CatalogEntry(
    val id: String,
    @SerialName("it") val nameIt: String,
    @SerialName("en") val nameEn: String,
    val alias: List<String>,
    val group: String,
    /** Incremento tipico del carico, in kg. */
    val step: Double,
    val glyph: String,
    val notes: String,
    /** I muscoli principali (id di [MuscleId]) e la vista forzata, "" = automatica. Assenti nelle copie vecchie del catalogo. */
    val muscles: List<String> = emptyList(),
    val view: String = "",
) {
    val muscleIDs: List<MuscleId> get() = muscles.mapNotNull { MuscleId.of(it) }

    /** Il nome nella lingua dell'app. */
    fun name(italian: Boolean): String = if (italian) nameIt else nameEn

    /** L'altro nome (serve a riconoscere la voce). */
    fun otherName(italian: Boolean): String = if (italian) nameEn else nameIt

    val muscleGroup: MuscleGroup get() = MuscleGroup.of(group) ?: MuscleGroup.petto
    val machineGlyph: MachineGlyph get() = MachineGlyph.of(glyph) ?: MachineGlyph.suggested(nameIt, muscleGroup)
}

/** Il catalogo: una copia sta nell'app (funziona anche offline e senza account); se il server ne ha una più nuova quella vince. */
object Catalog {

    private val json = Json { ignoreUnknownKeys = true }

    /** Minuscole, senza accenti né punteggiatura, spazi singoli: "Leg Press 45°" → "leg press 45". Come `norm` in common/catalog.ts. */
    fun norm(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    @Volatile var entries: List<CatalogEntry> = emptyList()
        private set
    private val names = ConcurrentHashMap<String, List<String>>()

    /** Il catalogo da un testo JSON, o `null` se non è un catalogo (meno di 100 voci). */
    fun decode(text: String): List<CatalogEntry>? =
        try { json.decodeFromString<List<CatalogEntry>>(text).takeIf { it.size >= 100 } } catch (_: Exception) { null }

    /** Installa le voci; `false` se il testo non è un catalogo valido. */
    fun install(text: String): Boolean {
        val list = decode(text) ?: return false
        names.clear()
        entries = list
        return true
    }

    fun names(entry: CatalogEntry): List<String> =
        names.getOrPut(entry.id) { (listOf(entry.nameIt, entry.nameEn) + entry.alias).map(::norm) }

    fun entry(id: String): CatalogEntry? = entries.firstOrNull { it.id == id }

    /** La voce che ha esattamente questo nome (italiano, inglese o sinonimo), se c'è. */
    fun match(name: String): CatalogEntry? {
        val n = norm(name)
        if (n.isEmpty()) return null
        return entries.firstOrNull { norm(it.nameIt) == n } ?: entries.firstOrNull { n in names(it) }
    }

    /**
     * Più parole in qualsiasi ordine: ognuna deve comparire in un nome o sinonimo. Prima chi inizia con la ricerca, poi chi ha una
     * parola che inizia così, poi il resto; a parità vale l'ordine del catalogo. Con `group` si cerca solo lì, filtrando PRIMA del
     * limite; ricerca vuota + gruppo = tutto il gruppo, senza limite.
     */
    fun search(query: String, group: MuscleGroup? = null, limit: Int = 40): List<CatalogEntry> {
        val q = norm(query)
        val pool = if (group != null) entries.filter { it.muscleGroup == group } else entries
        if (q.isEmpty()) return if (group == null) pool.take(limit) else pool
        val words = q.split(" ")
        class Scored(val entry: CatalogEntry, val score: Int, val index: Int)
        val scored = mutableListOf<Scored>()
        pool.forEachIndexed { index, entry ->
            val hay = names(entry)
            if (!words.all { w -> hay.any { it.contains(w) } }) return@forEachIndexed
            val score = when {
                hay.any { it.startsWith(q) } -> 0
                hay.any { h -> h.split(" ").any { it.startsWith(words[0]) } } -> 1
                else -> 2
            }
            scored += Scored(entry, score, index)
        }
        return scored.sortedWith(compareBy({ it.score }, { it.index })).take(limit).map { it.entry }
    }
}

// ---- pittogramma dedotto dal nome ----------------------------------------------------------

/**
 * Coppie (frammento di nome, pittogramma) valutate in ordine: le voci più specifiche stanno prima, così «Rematore Bilanciere»
 * non finisce sul pittogramma generico del rematore.
 */
private val GLYPH_KEYWORDS: List<Pair<String, MachineGlyph>> = listOf(
    // Gambe
    "leg press" to MachineGlyph.legPress, "pressa" to MachineGlyph.legPress, "hack squat" to MachineGlyph.legPress,
    "leg extension" to MachineGlyph.legLever, "leg curl" to MachineGlyph.legLever,
    "calf" to MachineGlyph.calfPlatform,
    "adductor" to MachineGlyph.machine, "abductor" to MachineGlyph.machine, "glute machine" to MachineGlyph.machine,
    "multipower" to MachineGlyph.smithMachine,
    "squat" to MachineGlyph.squatRack, "hip thrust" to MachineGlyph.barbell, "step up" to MachineGlyph.dumbbell,
    "affondi" to MachineGlyph.dumbbell, "bulgarian" to MachineGlyph.dumbbell,
    // Schiena
    "lat machine" to MachineGlyph.latPulldown,
    "pulley" to MachineGlyph.seatedRow,
    "rematore bilanciere" to MachineGlyph.barbell, "rematore con manubrio" to MachineGlyph.dumbbell,
    "rematore ai cavi" to MachineGlyph.cableStation, "rematore t-bar" to MachineGlyph.barbell,
    "rematore" to MachineGlyph.seatedRow,
    "trazioni" to MachineGlyph.pullUpBar, "dip" to MachineGlyph.pullUpBar,
    "stacco" to MachineGlyph.barbell, "good morning" to MachineGlyph.barbell,
    "iperestensioni" to MachineGlyph.hyperBench,
    "pullover machine" to MachineGlyph.machine,
    // Petto e spalle
    "plate loaded" to MachineGlyph.plateLoaded,
    "pectoral" to MachineGlyph.pecDeck, "butterfly" to MachineGlyph.pecDeck,
    "croci ai cavi" to MachineGlyph.cableCross,
    "panca piana" to MachineGlyph.benchFlat, "panca stretta" to MachineGlyph.benchFlat,
    "panca inclinata" to MachineGlyph.benchIncline, "panca declinata" to MachineGlyph.benchIncline,
    "panca scott" to MachineGlyph.benchIncline,
    "panca addominali" to MachineGlyph.abBench,
    "chest press" to MachineGlyph.seatedPush, "shoulder press" to MachineGlyph.seatedPush,
    "lento avanti" to MachineGlyph.barbell,
    // Core
    "sedia romana" to MachineGlyph.romanChair, "leg raise" to MachineGlyph.romanChair,
    "crunch machine" to MachineGlyph.machine, "rotary" to MachineGlyph.machine,
    "plank" to MachineGlyph.bodyweight, "piegamenti" to MachineGlyph.bodyweight,
    "russian" to MachineGlyph.bodyweight, "ab wheel" to MachineGlyph.bodyweight,
    "crunch a terra" to MachineGlyph.bodyweight,
    // Generici, per ultimi
    "cavi" to MachineGlyph.cableStation, "cable" to MachineGlyph.cableStation,
    "pushdown" to MachineGlyph.cableStation, "face pull" to MachineGlyph.cableStation,
    "kickback" to MachineGlyph.cableStation,
    "machine" to MachineGlyph.machine,
    "manubri" to MachineGlyph.dumbbell, "manubrio" to MachineGlyph.dumbbell,
    "bilanciere" to MachineGlyph.barbell, "sbarra" to MachineGlyph.pullUpBar,
    "alzate" to MachineGlyph.dumbbell, "scrollate" to MachineGlyph.dumbbell, "curl" to MachineGlyph.barbell,
    "press" to MachineGlyph.seatedPush,
)

/**
 * Pittogramma dedotto dal nome dell'esercizio; se non riconosce nulla ripiega su un default sensato per il gruppo muscolare.
 * Come `suggestedGlyph` in common/glyph.ts.
 */
fun MachineGlyph.Companion.suggested(name: String, group: MuscleGroup): MachineGlyph {
    val needle = name.lowercase()
    GLYPH_KEYWORDS.firstOrNull { needle.contains(it.first) }?.let { return it.second }
    return when (group) {
        MuscleGroup.petto, MuscleGroup.spalle -> MachineGlyph.seatedPush
        MuscleGroup.schiena -> MachineGlyph.seatedRow
        MuscleGroup.gambe -> MachineGlyph.legLever
        MuscleGroup.braccia -> MachineGlyph.dumbbell
        MuscleGroup.core -> MachineGlyph.bodyweight
    }
}
