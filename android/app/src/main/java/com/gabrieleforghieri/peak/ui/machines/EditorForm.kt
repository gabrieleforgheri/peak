package com.gabrieleforghieri.peak.ui.machines

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.catalogEntry
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.muscleIds
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.design.anatomyView
import com.gabrieleforghieri.peak.domain.CatalogEntry
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.MuscleId
import com.gabrieleforghieri.peak.domain.Plates
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.Uuids
import com.gabrieleforghieri.peak.domain.WeightUnit

/**
 * Tutto quello che l'editor di un macchinario tiene in mano (gli `@State` di `ExerciseEditorView.swift`), come un valore: le
 * scelte («una voce del catalogo», «salva») sono funzioni pure e si provano sul JVM. Il disegno sta in `ExerciseEditorSheet.kt`.
 */
data class EditorForm(
    val name: String = "",
    val group: MuscleGroup = MuscleGroup.petto,
    val step: Double = 2.5,
    val notes: String = "",
    /** kg */
    val barWeight: Double = 0.0,
    val weightDisplay: String = "absolute",
    val plateLoaded: Boolean = false,
    val perSide: Boolean = false,
    /** Come si fa, se l'hai scelto qui; finché è `null` vale quello del macchinario (o, se nuovo, quello di base). */
    val limbsPick: Limbs? = null,
    val restSeconds: Int = 0,
    val isArchived: Boolean = false,
    val isHidden: Boolean = false,
    /** sha256 della foto già salvata, "" = nessuna. */
    val photoSha: String = "",
    /** Una foto appena scelta (già ridotta da `ImageStore.prepare`), non ancora scritta su disco. */
    val newPhoto: ByteArray? = null,
    val photoOffset: Double = 0.0,
    /** Il pittogramma scelto a mano; finché è `null` non c'è (un collegato mostra quello del catalogo). */
    val pickedGlyph: MachineGlyph? = null,
    /** L'id della voce del catalogo; vuoto = personalizzato. */
    val catalogID: String = "",
    /** Creato a mano di proposito: non serve collegarlo al catalogo. */
    val isCustom: Boolean = false,
    /** I muscoli di un macchinario personalizzato, toccati sulla figura. Collegato, valgono quelli della voce. */
    val muscles: List<MuscleId> = emptyList(),
    val figureSide: AnatomyView = AnatomyView.front,
) {
    val linkedEntry: CatalogEntry? get() = catalogID.takeIf { it.isNotEmpty() }?.let(Catalog::entry)

    /** Collegato: l'icona del suo attrezzo; personalizzato: quella che scegli, o nessuna. */
    val glyph: MachineGlyph? get() = linkedEntry?.machineGlyph ?: pickedGlyph

    val hasPhoto: Boolean get() = newPhoto != null || photoSha.isNotEmpty()

    /** `existing` è quello del macchinario che si modifica (per un nuovo, `null`). */
    fun limbsShown(existing: Limbs?): Limbs = limbsPick ?: existing ?: Limbs.defaultLimbs(catalogID, name, group.raw)

    fun toggleMuscle(muscle: MuscleId): EditorForm = copy(muscles = if (muscle in muscles) muscles - muscle else muscles + muscle)

    /** Toglie la foto (`photoData = nil`) e rimette al centro l'inquadratura. */
    fun withoutPhoto(): EditorForm = copy(photoSha = "", newPhoto = null, photoOffset = 0.0)

    fun withPhoto(bytes: ByteArray): EditorForm = copy(newPhoto = bytes, photoOffset = 0.0)

    /**
     * Una voce scelta: nel macchinario nuovo precompila tutto (il nome si può cambiare subito con uno familiare). Se si torna indietro
     * e si sceglie altro, quello che la scelta di prima aveva messo viene sostituito. Collegando uno che c'è già (`isNew == false`)
     * cambia solo il collegamento (`chosen` di `ExerciseEditorView`).
     */
    fun chosen(entry: CatalogEntry?, picked: MuscleGroup?, isNew: Boolean, unit: WeightUnit, italian: Boolean): EditorForm {
        val wasLinked = catalogID.isNotEmpty()
        val linked = copy(catalogID = entry?.id ?: "")
        if (!isNew) return linked
        if (entry == null) {
            val custom = linked.copy(isCustom = true, group = picked ?: group)
            return if (wasLinked) custom.copy(name = "", notes = "", step = 2.5, barWeight = 0.0, plateLoaded = false) else custom
        }
        return linked.copy(
            isCustom = false, muscles = emptyList(), pickedGlyph = null,
            name = entry.name(italian), group = entry.muscleGroup, step = entry.step, notes = entry.notes,
            barWeight = Plates.defaultBar(entry.machineGlyph, entry.name(italian), unit),
            plateLoaded = Plates.guessPlateLoaded(entry.machineGlyph, entry.name(italian)),
        )
    }

    /** Il macchinario salvato con queste scelte: lo stesso `save()` per la creazione e per la modifica. `photoSha` è quello già scritto. */
    fun applyTo(existing: ExerciseEntity?, photoShaToSave: String, nextSortIndex: Int, now: Long, italian: Boolean, uuid: String = Uuids.new()): ExerciseEntity {
        val trimmed = name.trim()
        val custom = catalogID.isEmpty() && isCustom
        val musclesRaw = muscles.joinToString(",") { it.name }
        if (existing != null) {
            // il campo parte dal nome nella lingua dell'app: se non l'hai toccato, il nome salvato (italiano) resta com'è
            val saved = existing.copy(
                name = if (trimmed != existing.displayName(italian)) trimmed else existing.name,
                muscleGroup = group.raw, incrementStep = step, notes = notes,
                restSeconds = restSeconds, isArchived = isArchived, isHidden = isHidden,
                photo = photoShaToSave, photoOffset = photoOffset, glyph = pickedGlyph?.raw ?: "",
                catalogID = catalogID, isCustom = custom, muscles = musclesRaw, view = figureSide.name,
                barWeight = barWeight, weightDisplay = weightDisplay, plateLoaded = plateLoaded, perSide = perSide,
            )
            return if (group == MuscleGroup.core) saved.withLimbs(Limbs.both) else limbsPick?.let(saved::withLimbs) ?: saved
        }
        return ExerciseEntity(
            uuid = uuid, name = trimmed, muscleGroup = group.raw, notes = notes, incrementStep = step, sortIndex = nextSortIndex,
            glyph = pickedGlyph?.raw ?: "", photo = photoShaToSave, photoOffset = photoOffset, restSeconds = restSeconds, createdAt = now,
            catalogID = catalogID, isHidden = isHidden, muscles = musclesRaw, view = figureSide.name, isCustom = custom,
            barWeight = barWeight, weightDisplay = weightDisplay, plateLoaded = plateLoaded, perSide = perSide,
        ).withLimbs(if (group == MuscleGroup.core) Limbs.both else limbsShown(null))
    }

    companion object {
        /** Le scelte di un macchinario che c'è già (`load()`). */
        fun of(exercise: ExerciseEntity, italian: Boolean): EditorForm = EditorForm(
            name = exercise.displayName(italian), group = MuscleGroup.of(exercise.muscleGroup) ?: MuscleGroup.petto,
            step = exercise.incrementStep, notes = exercise.notes, barWeight = exercise.barWeight,
            weightDisplay = exercise.weightDisplay.ifEmpty { "absolute" }, plateLoaded = exercise.plateLoaded, perSide = exercise.perSide,
            restSeconds = exercise.restSeconds, isArchived = exercise.isArchived, isHidden = exercise.isHidden,
            photoSha = exercise.photo, photoOffset = exercise.photoOffset, pickedGlyph = MachineGlyph.of(exercise.glyph),
            catalogID = exercise.catalogID, isCustom = exercise.isCustom, muscles = exercise.muscleIds,
            figureSide = AnatomyView.entries.firstOrNull { it.name == exercise.view } ?: anatomyView(exercise.muscleIds),
        )
    }
}

/** Si può salvare con un nome (`.disabled(name.trimmed.isEmpty)`). */
val EditorForm.canSave: Boolean get() = name.trim().isNotEmpty()

/** Le sbarre rapide (kg o lb) e il loro valore in kg (`stepChip` della sbarra). */
fun barPresets(unit: WeightUnit): List<Pair<String, Double>> =
    (if (unit == WeightUnit.kg) listOf(0.0, 10.0, 15.0, 20.0) else listOf(0.0, 25.0, 35.0, 45.0)).map { p ->
        (if (p == 0.0) "Nessuna (0)" else "${p.toInt()} ${unit.symbol}") to unit.toKilograms(p)
    }

/** La riga che spiega «Come si fa» (`limbsHint`). */
fun limbsHint(limbs: Limbs, group: MuscleGroup): String = when (limbs) {
    Limbs.both -> "Insieme: i due arti lavorano sempre insieme."
    Limbs.one -> if (group == MuscleGroup.gambe) "Una gamba: si fa sempre una gamba alla volta." else "Un braccio: si fa sempre un braccio alla volta."
    Limbs.either -> "A scelta: sulla serie scegli 2 o 1."
}

/** Il passo nell'intervallo del campo (0,25–50 kg): fuori, il valore non cambia (`TextField` del passo). */
fun clampedStep(current: Double, typed: Double): Double = if (typed in 0.25..50.0) typed else current

/** Il peso della sbarra dopo un tocco su «+» o «−»: a passi da 1 kg (2,5 lb), da 0 a 60 kg (135 lb); sempre in kg. */
fun stepBar(barKg: Double, direction: Int, unit: WeightUnit): Double {
    val step = if (unit == WeightUnit.kg) 1.0 else 2.5
    val max = if (unit == WeightUnit.kg) 60.0 else 135.0
    return maxOf(0.0, unit.toKilograms((unit.fromKilograms(barKg) + direction * step).coerceIn(0.0, max)))
}
