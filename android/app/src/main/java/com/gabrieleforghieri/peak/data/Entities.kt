package com.gabrieleforghieri.peak.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Il modello dati rispecchia i modelli SwiftData di Peak/Model/ e i documenti di common/docs.ts, così la fase 2 può
// sincronizzare e importare i backup dell'iPhone senza traduzioni strane:
//  - gli uuid sono stringhe MAIUSCOLE (come le scrive Swift e come le vuole il server);
//  - le date sono millisecondi dall'epoca; nei documenti e nei backup diventano ISO a secondi interi (PeakDates);
//  - i pesi sono sempre in kg; le righe di scheda e di piano sono un testo JSON (come `itemsRaw` / `planRaw`);
//  - i campi «Raw» di Swift qui hanno il loro nome senza il suffisso (muscleGroup, glyph…), con gli stessi valori.

/** Un macchinario o esercizio di cui si tiene traccia il massimale (`Exercise`, documento `exercise`). */
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val uuid: String,
    val name: String = "",
    /** petto, schiena, gambe, spalle, braccia, core. */
    val muscleGroup: String = "petto",
    val notes: String = "",
    /** Incremento minimo del carico su questo macchinario (kg). */
    val incrementStep: Double = 2.5,
    val sortIndex: Int = 0,
    val isArchived: Boolean = false,
    /** Pittogramma scelto a mano ("" = nessuno; i collegati mostrano quello del catalogo). */
    val glyph: String = "",
    /** sha256 della foto (documento `photo`); i byte stanno in un file a parte, mai nel database. "" = nessuna. */
    val photo: String = "",
    val photoOffset: Double = 0.0,
    /** Il vecchio segno «un braccio»: segue `limbs == one`. */
    val isUnilateral: Boolean = false,
    /** Recupero dopo ogni serie, in secondi. 0 = quello delle impostazioni. */
    val restSeconds: Int = 0,
    val createdAt: Long = 0,
    /** Id della voce del catalogo; "" = personalizzato. */
    val catalogID: String = "",
    /** Nascosto agli amici. */
    val isHidden: Boolean = false,
    /** I muscoli di un macchinario personalizzato (id separati da virgola). */
    val muscles: String = "",
    /** Vista forzata della figura: "", "front" o "back". */
    val view: String = "",
    val isCustom: Boolean = false,
    /** Peso della sbarra in kg (0 se nessuna sbarra). */
    val barWeight: Double = 0.0,
    /** "absolute" (totale con sbarra) o "relative" (solo dischi). */
    val weightDisplay: String = "absolute",
    val plateLoaded: Boolean = false,
    val perSide: Boolean = false,
    /** both, one, either; `null` = di base (catalogo o nome): non si scrive nulla finché non lo scegli. */
    val limbs: String? = null,
)

/** Una misurazione del massimale: peso × ripetizioni in una certa data (`MaxRecord`, documento `record`). */
@Entity(
    tableName = "max_record",
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["uuid"], ["exerciseUuid"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseUuid")],
)
data class MaxRecordEntity(
    @PrimaryKey val uuid: String,
    val exerciseUuid: String? = null,
    val date: Long = 0,
    val weight: Double = 0.0,
    val reps: Int = 1,
    /** `true` quando il record nasce da una serie di allenamento: non viaggia mai, si ricalcola. */
    val isEstimated: Boolean = false,
    val note: String = "",
    val singleArm: Boolean = false,
)

/** Una sessione di allenamento (`Workout`, documento `workout`). */
@Entity(tableName = "workout")
data class WorkoutEntity(
    @PrimaryKey val uuid: String,
    val date: Long = 0,
    /** `null` finché la sessione è in corso. */
    val endedAt: Long? = null,
    val note: String = "",
    val name: String = "",
    /** Gruppi scelti a inizio sessione, separati da virgola, in ordine canonico. */
    val focus: String = "",
    /** uuid della scheda di partenza, "" = nessuna. La scheda può sparire: non è un legame. */
    val template: String = "",
    /** La lista di oggi: JSON di `[TemplateItem]` (`TemplateItems`), "" = nessuna riga. */
    val plan: String = "",
)

/** Una singola serie eseguita durante un allenamento (`WorkoutSet`, documento `set`). */
@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(WorkoutEntity::class, ["uuid"], ["workoutUuid"], onDelete = ForeignKey.CASCADE),
        // le serie restano nello storico anche se il macchinario viene eliminato
        ForeignKey(ExerciseEntity::class, ["uuid"], ["exerciseUuid"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("workoutUuid"), Index("exerciseUuid")],
)
data class WorkoutSetEntity(
    @PrimaryKey val uuid: String,
    val workoutUuid: String? = null,
    val exerciseUuid: String? = null,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    @ColumnInfo(name = "setOrder") val order: Int = 0,
    val createdAt: Long = 0,
    val isPersonalRecord: Boolean = false,
    /** Documento `noRecord`: la serie non conta per i massimali. */
    val excludedFromRecords: Boolean = false,
    val singleArm: Boolean = false,
)

/** Una pesata (`BodyWeightEntry`, documento `bodyweight`). Il peso è sempre in kg. */
@Entity(tableName = "body_weight")
data class BodyWeightEntity(
    @PrimaryKey val uuid: String,
    val date: Long = 0,
    val weight: Double = 0.0,
    val fromHealth: Boolean = false,
)

/** Un giorno della scheda (`WorkoutTemplate`, documento `template`). */
@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey val uuid: String,
    val name: String = "",
    /** JSON di `[TemplateItem]`, "" = nessuna riga. */
    val items: String = "",
    val sortIndex: Int = 0,
    val createdAt: Long = 0,
    val isHidden: Boolean = false,
    /** Nome di `TemplateIcon`; "" = dedotta dal nome. */
    val icon: String = "",
    /** uuid della scheda che lo contiene; "" = fuori da ogni scheda. */
    val program: String = "",
    /** 0 = lunedì … 6 = domenica; −1 = nessuno. */
    val weekday: Int = -1,
)

/** La scheda generale: un contenitore di giorni (`WorkoutProgram`, documento `program`). */
@Entity(tableName = "workout_program")
data class WorkoutProgramEntity(
    @PrimaryKey val uuid: String,
    val name: String = "",
    val sortIndex: Int = 0,
    val createdAt: Long = 0,
    val useDays: Boolean = false,
)

/**
 * Un'impostazione: chiave (quelle di `SettingKeys`) e valore come testo JSON, la stessa forma del documento `setting`
 * (`{value: …}`). Quelle `device.*` restano di questo telefono.
 */
@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
