package com.gabrieleforghieri.peak.ui.friends

import android.text.format.DateFormat
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MaxRanking
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.OneRepMax
import com.gabrieleforghieri.peak.domain.Programs
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.Uuids
import com.gabrieleforghieri.peak.ui.friends.social.LeagueRow
import com.gabrieleforghieri.peak.ui.friends.social.SharedRow
import com.gabrieleforghieri.peak.ui.friends.social.SharedTemplate
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// Le regole di Amici che non dipendono dallo schermo: classifiche, confronto con i tuoi massimali, «copia nelle mie schede». Per poterle provare sul JVM.

/** La metrica della lega (`FriendsView.Metric`): `title` è anche la chiave di traduzione. */
enum class LeagueMetric(val title: String) {
    volume("Volume 30 giorni"), sessions("Allenamenti 30 giorni"), streak("Settimane di fila");

    fun value(row: LeagueRow): Double = when (this) {
        volume -> row.volume30
        sessions -> row.sessions30.toDouble()
        streak -> row.streak.toDouble()
    }
}

object FriendsLogic {
    /** La lega in classifica: chi ha di più per primo, a parità tu davanti (`ranked` di `FriendsView.league`). */
    fun ranked(rows: List<LeagueRow>, metric: LeagueMetric): List<LeagueRow> =
        rows.sortedWith(compareByDescending<LeagueRow> { metric.value(it) }.thenByDescending { if (it.isMe) 1 else 0 })

    /** Quanto è lunga la barra rispetto al primo: 0...1, mai una divisione per zero (`Podium.barFraction`). */
    fun barFraction(value: Double, top: Double): Double = if (top > 0) minOf(1.0, maxOf(0.0, value / top)) else 0.0

    /** Il tuo posto in lega (1 = in testa), 0 se non ci sei. */
    fun myPlace(ranked: List<LeagueRow>): Int = ranked.indexOfFirst { it.isMe } + 1

    /** I tuoi macchinari collegati al catalogo, non archiviati, con almeno un massimale a due braccia (`linkedMine`). */
    fun linkedMine(exercises: List<ExerciseEntity>, records: List<MaxRecordEntity>): List<ExerciseEntity> {
        val byExercise = records.groupBy { it.exerciseUuid }
        return exercises.filter { e ->
            e.catalogID.isNotEmpty() && !e.isArchived && twoArmRecords(e, byExercise[e.uuid].orEmpty()).isNotEmpty()
        }
    }

    private fun limbsOf(e: ExerciseEntity): Limbs = Limbs.limbsOf(e.limbs, e.isUnilateral, e.catalogID, e.name, e.muscleGroup)

    /** I massimali a due braccia: la linea che mostra «il» massimale (`Exercise.twoArmRecords`). */
    fun twoArmRecords(e: ExerciseEntity, records: List<MaxRecordEntity>): List<MaxRecordEntity> {
        val limbs = limbsOf(e)
        return records.filter { !Limbs.lineArm(limbs, it.singleArm) }
    }

    /** Il tuo miglior 1RM per voce del catalogo, per il confronto nel profilo di un amico (`FriendProfileView.mine`). */
    fun myBestByCatalog(exercises: List<ExerciseEntity>, records: List<MaxRecordEntity>, sets: List<WorkoutSetEntity>): Map<String, Double> {
        val recordsBy = records.groupBy { it.exerciseUuid }
        val setsBy = sets.groupBy { it.exerciseUuid }
        val out = HashMap<String, Double>()
        for (e in exercises) {
            if (e.catalogID.isEmpty() || e.isArchived) continue
            val limbs = limbsOf(e)
            var best = twoArmRecords(e, recordsBy[e.uuid].orEmpty()).maxOfOrNull { OneRepMax.estimate(it.weight, it.reps) } ?: 0.0
            best = maxOf(best, setsBy[e.uuid].orEmpty().filter { !it.isWarmup && !Limbs.lineArm(limbs, it.singleArm) }.maxOfOrNull { OneRepMax.estimate(it.weight, it.reps) } ?: 0.0)
            if (best > 0) out[e.catalogID] = maxOf(out[e.catalogID] ?: 0.0, best)
        }
        return out
    }

    /** Il tuo miglior massimale a due braccia (`Exercise.bestRecord`), per scegliere i macchinari da mettere in classifica. */
    fun bestRecord(e: ExerciseEntity, records: List<MaxRecordEntity>): MaxRecordEntity? =
        MaxRanking.best(twoArmRecords(e, records), { it.weight }, { it.reps }, { it.date })

    /** Di quanto l'amico è sopra (positivo) o sotto di te, in percento intero; `null` se non c'è un tuo valore (`FriendProfileView.maxRow`). */
    fun percentAbove(friend: Double, mine: Double?): Int? {
        if (mine == null || mine <= 0) return null
        val p = (friend - mine) / mine * 100
        return (if (p < 0) -Math.floor(-p + 0.5) else Math.floor(p + 0.5)).toInt() // `rounded()` di Swift: la metà si allontana dallo zero
    }

    /** «3×8–12»: serie e ripetizioni di una riga di scheda (`FriendProfileView.target`). */
    fun target(r: SharedRow): String {
        val reps = if (r.repsMax == 0) "" else if (r.repsMin == r.repsMax) "${r.repsMax}" else "${r.repsMin}–${r.repsMax}"
        return (if (r.sets > 0) "${r.sets}×" else "") + reps
    }

    /** «Oggi», «Ieri», altrimenti giorno della settimana, giorno e mese abbreviati (`Fmt.relativeDay`). `today` e `yesterday` sono già tradotti. */
    fun relativeDay(
        ms: Long, today: String, yesterday: String, nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault(),
    ): String {
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val now = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        if (day == now) return today
        if (day == now.minusDays(1)) return yesterday
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        return java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(day)
    }

    /** Cosa creare copiando un giorno di un amico fra i tuoi (`FriendProfileView.copy`). */
    class CopyPlan(val newExercises: List<ExerciseEntity>, val template: WorkoutTemplateEntity, val newProgram: WorkoutProgramEntity?)

    /**
     * Ogni riga si abbina a un tuo esercizio con la stessa voce del catalogo (o, se personalizzato, lo stesso nome); quelli che mancano si
     * creano. Senza pesi. Il giorno va nella scheda in uso, o in «La mia scheda» (che si crea) se non ne hai.
     */
    fun planCopy(
        shared: SharedTemplate, exercises: List<ExerciseEntity>, templates: List<WorkoutTemplateEntity>, programs: List<WorkoutProgramEntity>,
        activeProgram: String, italian: Boolean, nowMs: Long, newUuid: () -> String = Uuids::new,
    ): CopyPlan {
        val mine = exercises.filter { !it.isArchived }.toMutableList()
        var nextIndex = (exercises.maxOfOrNull { it.sortIndex } ?: 0) + 1
        val created = mutableListOf<ExerciseEntity>()
        val items = mutableListOf<TemplateItem>()
        for (row in shared.rows) {
            var exercise = if (row.catalog.isEmpty()) {
                mine.firstOrNull { it.catalogID.isEmpty() && Catalog.norm(it.name) == Catalog.norm(row.name) }
            } else {
                mine.firstOrNull { it.catalogID == row.catalog }
            }
            if (exercise == null) {
                val entry = Catalog.entry(row.catalog)
                val name = entry?.name(italian) ?: row.name
                val group = entry?.muscleGroup ?: MuscleGroup.petto
                val catalogID = entry?.id ?: ""
                val limbs = Limbs.limbsOf(null, false, catalogID, name, group.raw)
                val made = ExerciseEntity(
                    uuid = newUuid(), name = name, muscleGroup = group.raw, notes = entry?.notes ?: "", incrementStep = entry?.step ?: 2.5,
                    sortIndex = nextIndex, createdAt = nowMs, catalogID = catalogID, isCustom = entry == null,
                    limbs = limbs.name, isUnilateral = limbs == Limbs.one,
                )
                created += made
                mine += made
                nextIndex += 1
                exercise = made
            }
            if (items.none { it.exercise == exercise.uuid }) items += TemplateItem(exercise.uuid, row.sets, row.repsMin, row.repsMax, null)
        }
        val active = Programs.active(programs, { it.uuid }, activeProgram)
        val program = active ?: WorkoutProgramEntity(uuid = Uuids.DEFAULT_PROGRAM, name = "La mia scheda", createdAt = nowMs)
        val top = (templates.maxOfOrNull { it.sortIndex } ?: -1) + 1
        val template = WorkoutTemplateEntity(
            uuid = newUuid(), name = shared.name, items = TemplateItems.encode(items), sortIndex = top, createdAt = nowMs, program = program.uuid,
        )
        return CopyPlan(created, template, if (active == null) program else null)
    }
}
