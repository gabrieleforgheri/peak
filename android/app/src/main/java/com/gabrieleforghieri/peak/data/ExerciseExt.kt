package com.gabrieleforghieri.peak.data

import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.CatalogEntry
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.MuscleId
import com.gabrieleforghieri.peak.util.Lang

// Le proprietà derivate di `Peak/Model/Exercise.swift` che le viste usano: sono funzioni di `ExerciseEntity` e non toccano il database.
// Comuni a tutte le aree (proprietario: la base della fase 3): una correzione qui vale ovunque.

val ExerciseEntity.group: MuscleGroup get() = MuscleGroup.of(muscleGroup) ?: MuscleGroup.petto

val ExerciseEntity.catalogEntry: CatalogEntry? get() = catalogID.takeIf { it.isNotEmpty() }?.let(Catalog::entry)

/**
 * Il nome da mostrare: se è collegato al catalogo e il nome salvato è ancora quello italiano o inglese della voce, quello nella
 * lingua dell'app; se l'hai rinominato resta il tuo. Per la logica (abbinamenti, salvataggio, sincronizzazione) vale `name`.
 */
fun ExerciseEntity.displayName(italian: Boolean = Lang.isItalian()): String {
    val entry = catalogEntry ?: return name
    val n = Catalog.norm(name)
    return if (n == Catalog.norm(entry.nameIt) || n == Catalog.norm(entry.nameEn)) entry.name(italian) else name
}

/** Ricerca per più parole in qualsiasi ordine, senza accenti: nome salvato, nome mostrato e, se collegato, nome italiano, inglese e sinonimi. */
fun ExerciseEntity.matches(search: String, italian: Boolean = Lang.isItalian()): Boolean {
    val words = Catalog.norm(search).split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val hay = listOf(Catalog.norm(name), Catalog.norm(displayName(italian))) + (catalogEntry?.let(Catalog::names) ?: emptyList())
    return words.all { w -> hay.any { it.contains(w) } }
}

/** L'icona da mostrare: quella dell'attrezzo del catalogo; per un macchinario personalizzato quella scelta a mano, altrimenti nessuna. */
val ExerciseEntity.machineGlyph: MachineGlyph? get() = catalogEntry?.machineGlyph ?: MachineGlyph.of(glyph)

/** `true` se il pittogramma è stato scelto a mano. */
val ExerciseEntity.hasCustomGlyph: Boolean get() = MachineGlyph.of(glyph) != null

/** I muscoli toccati a mano su un macchinario personalizzato. */
val ExerciseEntity.muscleIds: List<MuscleId> get() = muscles.split(',').mapNotNull { MuscleId.of(it) }

/** I muscoli di questo macchinario: di quello del catalogo se è collegato, altrimenti quelli che hai toccato. */
val ExerciseEntity.figureMuscles: List<MuscleId> get() = catalogEntry?.muscleIDs ?: muscleIds

/** La vista forzata della figura, "" = automatica. */
val ExerciseEntity.figureView: String get() = catalogEntry?.view ?: view
