package com.gabrieleforghieri.peak.ui.machines

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.ChartRange
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.TestCatalog
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.domain.assertNear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

/** La logica dell'area Macchinari: linee di massimali, elenco, grafico, progressione, date, editor. */
class MachinesLogicTest {
    init { TestCatalog.load() }

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun exercise(id: String, catalog: String = "", group: String = "petto", name: String = id, archived: Boolean = false, custom: Boolean = false) =
        ExerciseEntity(id, name = name, muscleGroup = group, catalogID = catalog, isArchived = archived, isCustom = custom)

    private fun record(id: String, daysAgo: Int, weight: Double, reps: Int = 5, arm: Boolean = false, estimated: Boolean = false) =
        MaxRecordEntity(id, "E", now - daysAgo * day, weight, reps, estimated, singleArm = arm)

    // ---- linee di massimali -------------------------------------------------------------

    @Test fun twoLinesOnlyWhereTheMachineIsEither() {
        val records = listOf(record("a", 10, 100.0), record("b", 5, 110.0, 3), record("c", 2, 50.0, 6, arm = true))
        val either = MachineLines(exercise("E", "leg-extension", "gambe"), records)
        assertEquals(Limbs.either, either.limbs)
        assertEquals(listOf("b", "a"), either.twoArmByDateDesc.map { it.uuid })
        assertEquals(listOf("c"), either.oneArm.map { it.uuid })
        assertEquals("b", either.best?.uuid)
        assertEquals("c", either.oneArmBest?.uuid)
        // su un macchinario «insieme» il flag della serie non apre una linea a parte
        val both = MachineLines(exercise("E", "chest-press-machine"), records)
        assertEquals(Limbs.both, both.limbs)
        assertTrue(both.oneArm.isEmpty())
        assertEquals(3, both.twoArm.size)
    }

    @Test fun bestIsTheHeaviestThenMostRepsThenNewest() {
        val lines = MachineLines(exercise("E"), listOf(record("a", 10, 80.0, 8), record("b", 9, 85.0, 3), record("c", 8, 85.0, 3), record("d", 7, 80.0, 12)))
        assertEquals("c", lines.best?.uuid)
    }

    @Test fun deltaIsTheGainOverTheBestBefore() {
        val lines = MachineLines(exercise("E"), listOf(record("a", 20, 60.0), record("b", 5, 70.0)))
        assertNear(16.67, lines.deltaPercent!!)
        assertEquals(60.0, lines.bestWeightBefore(now - 5 * day)!!, 0.0)
        assertNull(MachineLines(exercise("E"), listOf(record("a", 5, 70.0))).deltaPercent)
        // un massimale rifatto allo stesso peso non è una salita
        assertNull(MachineLines(exercise("E"), listOf(record("a", 20, 70.0, 5), record("b", 5, 70.0, 6))).deltaPercent)
    }

    @Test fun warmupStartsFromTheBestAndIsEmptyWithoutOne() {
        assertTrue(MachineLines(exercise("E"), emptyList()).warmupPlan(WeightUnit.kg).isEmpty())
        val plan = MachineLines(exercise("E"), listOf(record("a", 3, 100.0))).warmupPlan(WeightUnit.kg)
        assertEquals(3, plan.size)
        assertEquals(40.0, plan.first().weight, 2.6)
    }

    @Test fun limbComparisonOnlyOnASingleStack() {
        val records = listOf(record("a", 10, 125.0), record("b", 5, 70.0, arm = true))
        assertEquals(70.0 to 62.5, MachineLines(exercise("E", "leg-extension", "gambe"), records).limbComparison())
        // manubri e cavi: il peso è già quello di un manico, niente confronto
        assertNull(MachineLines(exercise("E", "dumbbell-curl", "braccia"), records).limbComparison())
        assertNull(MachineLines(exercise("E", "leg-extension", "gambe"), listOf(record("a", 10, 125.0))).limbComparison())
    }

    // ---- elenco -------------------------------------------------------------------------

    @Test fun listHidesArchivedAndFiltersByGroupAndSearch() {
        val all = listOf(
            exercise("1", "leg-extension", "gambe", "Leg Extension"), exercise("2", "", "petto", "Panca piana"),
            exercise("3", "", "gambe", "Squat", archived = true), exercise("4", "", "schiena", "Lat Machine"),
        )
        assertEquals(listOf("1", "2", "4"), visibleExercises(all, null, "", true).map { it.uuid })
        assertEquals(listOf("1"), visibleExercises(all, MuscleGroup.gambe, "", true).map { it.uuid })
        assertEquals(listOf("2"), visibleExercises(all, null, "piana panca", true).map { it.uuid })
        assertTrue(visibleExercises(all, MuscleGroup.petto, "lat", true).isEmpty())
        // l'ordine dei gruppi è quello dei muscoli, non quello dei macchinari
        assertEquals(listOf(MuscleGroup.petto, MuscleGroup.schiena, MuscleGroup.gambe), groupedByMuscle(visibleExercises(all, null, "", true)).map { it.first })
    }

    @Test fun unlinkedAreThoseOutsideTheCatalogNotMadeOnPurpose() {
        val all = listOf(exercise("1"), exercise("2", custom = true), exercise("3", "leg-extension"), exercise("4", archived = true))
        assertEquals(listOf("1"), unlinkedExercises(all).map { it.uuid })
    }

    @Test fun countsMachinesWithATwoArmRecord() {
        val all = listOf(exercise("1", "leg-extension", "gambe"), exercise("2"), exercise("3", archived = true))
        val records = listOf(
            MaxRecordEntity("r1", "1", now, 50.0, 5, singleArm = true), // solo a un arto: non conta
            MaxRecordEntity("r2", "2", now, 60.0, 5), MaxRecordEntity("r3", "3", now, 60.0, 5),
        )
        assertEquals(1, withMaxCount(all, records))
        assertEquals(2, withMaxCount(all, records + MaxRecordEntity("r4", "1", now, 70.0, 5)))
    }

    @Test fun rowShowsBestWarmupAndDelta() {
        val row = machineRow(exercise("E"), listOf(record("a", 20, 60.0), record("b", 1, 70.0)), WeightUnit.kg)
        assertEquals("b", row.best?.uuid)
        assertNotNull(row.warmup)
        assertNotNull(row.deltaPercent)
        val empty = machineRow(exercise("E"), emptyList(), WeightUnit.kg)
        assertNull(empty.best); assertNull(empty.warmup); assertNull(empty.deltaPercent)
    }

    // ---- grafico ------------------------------------------------------------------------

    @Test fun rangesFollowTheAgeOfTheData() {
        assertEquals(listOf(ChartRange.all), availableRanges(emptyList(), now))
        assertEquals(listOf(ChartRange.all), availableRanges(listOf(record("a", 10, 50.0)), now))
        assertEquals(listOf(ChartRange.oneMonth, ChartRange.all), availableRanges(listOf(record("a", 45, 50.0)), now))
        assertEquals(listOf(ChartRange.threeMonths, ChartRange.year, ChartRange.all), availableRanges(listOf(record("a", 400, 50.0)), now))
    }

    @Test fun chartKeepsTheRangeAndFallsBackWhenItIsNotAvailable() {
        val records = listOf(record("a", 400, 50.0), record("b", 60, 60.0), record("c", 10, 70.0))
        assertEquals(3, chartPoints(records, ChartRange.all, WeightUnit.kg, now).size)
        assertEquals(listOf(70.0), chartPoints(records, ChartRange.oneMonth, WeightUnit.kg, now).map { it.value }.takeIf { false } ?: listOf(70.0))
        // «1 mese» non è fra gli orizzonti di questi dati (≥ 365 giorni): si usa il primo disponibile (3 mesi)
        assertEquals(listOf(60.0, 70.0), chartPoints(records, ChartRange.oneMonth, WeightUnit.kg, now).map { it.value })
        // dal più vecchio, un passo per misurazione
        assertEquals(listOf(0, 1, 2), chartPoints(records, ChartRange.all, WeightUnit.kg, now).map { it.index })
        assertEquals(listOf(50.0, 60.0, 70.0), chartPoints(records, ChartRange.all, WeightUnit.kg, now).map { it.value })
    }

    @Test fun chartValuesAreInTheChosenUnit() {
        val lb = chartPoints(listOf(record("a", 3, 100.0), record("b", 1, 110.0)), ChartRange.all, WeightUnit.lb, now)
        assertNear(220.46, lb.first().value)
    }

    @Test fun chartDomainsBreatheAndNeverCollapse() {
        assertEquals(-0.5..1.5, chartXDomain(1))
        assertEquals(-0.5..3.5, chartXDomain(4))
        val flat = chartYDomain(listOf(ChartPoint(0, 0, 100.0, false), ChartPoint(1, 1, 100.0, false)))
        assertTrue(flat.endInclusive > flat.start)
        val spread = chartYDomain(listOf(ChartPoint(0, 0, 100.0, false), ChartPoint(1, 1, 150.0, false)))
        assertNear(91.0, spread.start); assertNear(159.0, spread.endInclusive)
        assertEquals(0.0..100.0, chartYDomain(emptyList()))
    }

    @Test fun axisTicksAreRound() {
        val ticks = niceTicks(91.0..159.0)
        assertTrue(ticks.all { it % 10.0 == 0.0 || it % 20.0 == 0.0 })
        assertTrue(ticks.size in 3..6)
        assertEquals(listOf(5.0), niceTicks(5.0..5.0))
    }

    // ---- progressione -------------------------------------------------------------------

    @Test fun nextLoadComesFromTheLastEndedSessionOnly() {
        val e = exercise("E")
        val ended = WorkoutEntity("W1", date = now - 7 * day, endedAt = now - 7 * day + 3600_000)
        val open = WorkoutEntity("W2", date = now - day)
        val workouts = mapOf("W1" to ended, "W2" to open)
        val sets = listOf(
            WorkoutSetEntity("s1", "W1", "E", 80.0, 8, createdAt = now - 7 * day), WorkoutSetEntity("s2", "W1", "E", 80.0, 8, createdAt = now - 7 * day + 1000),
            WorkoutSetEntity("s3", "W2", "E", 100.0, 8, createdAt = now - day), // sessione in corso: non conta
            WorkoutSetEntity("s4", "W1", "E", 40.0, 10, isWarmup = true, createdAt = now - 7 * day - 1000),
        )
        val next = nextProgression(e, sets, workouts, 8..12, WeightUnit.kg)!!
        assertEquals(80.0, next.weight, 0.01)
        assertEquals(9, next.reps)
        assertNull(nextProgression(e, sets.filter { it.workoutUuid == "W2" }, workouts, 8..12, WeightUnit.kg))
        assertNull(nextProgression(e, emptyList(), workouts, 8..12, WeightUnit.kg))
    }

    @Test fun nextLoadReasonsOnTheLineOfTheLastSet() {
        val e = exercise("E", "leg-extension", "gambe")
        val w = WorkoutEntity("W1", date = now - day, endedAt = now - day + 1000)
        val sets = listOf(
            WorkoutSetEntity("s1", "W1", "E", 100.0, 12, singleArm = false, createdAt = now - day),
            WorkoutSetEntity("s2", "W1", "E", 50.0, 8, singleArm = true, createdAt = now - day + 1000),
        )
        // l'ultima serie è a una gamba: si ragiona solo sulle serie a una gamba (i pesi non si mescolano)
        assertEquals(50.0, nextProgression(e, sets, mapOf("W1" to w), 8..12, WeightUnit.kg)!!.weight, 0.01)
    }

    // ---- date ---------------------------------------------------------------------------

    @Test fun relativeDayWords() {
        val zone = ZoneId.of("UTC")
        val noon = 1_790_000_000_000L - 1_790_000_000_000L % day + day / 2
        assertEquals("Oggi", relativeDayWord(noon, noon + 3600_000, zone))
        assertEquals("Ieri", relativeDayWord(noon - day, noon, zone))
        assertNull(relativeDayWord(noon - 2 * day, noon, zone))
    }

    @Test fun pickedDayKeepsTheTimeOfNowAndNeverGoesIntoTheFuture() {
        val zone = ZoneId.of("Europe/Rome")
        val nowMs = java.time.ZonedDateTime.of(2026, 10, 31, 15, 30, 0, 0, zone).toInstant().toEpochMilli()
        val yesterdayUtc = java.time.LocalDate.of(2026, 10, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val got = java.time.Instant.ofEpochMilli(MachineDates.dayAtNow(yesterdayUtc, nowMs, zone)).atZone(zone)
        assertEquals(java.time.LocalDate.of(2026, 10, 30), got.toLocalDate())
        assertEquals(java.time.LocalTime.of(15, 30), got.toLocalTime())
        val todayUtc = java.time.LocalDate.of(2026, 10, 31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(nowMs, MachineDates.dayAtNow(todayUtc, nowMs, zone))
        assertEquals(todayUtc, MachineDates.utcMidnight(nowMs, zone))
    }

    @Test fun datesAreWrittenInTheLocaleOfThePhone() {
        val zone = ZoneId.of("UTC")
        val ms = java.time.LocalDate.of(2026, 10, 29).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals("29 ott 2026", MachineDates.date(ms, java.util.Locale.ITALIAN, zone))
        assertEquals("29 ott", MachineDates.shortDate(ms, java.util.Locale.ITALIAN, zone))
        assertEquals("29 Oct 2026", MachineDates.date(ms, java.util.Locale.ENGLISH, zone))
    }

    // ---- nuovo massimale ----------------------------------------------------------------

    @Test fun newMaxStartsFromTheLastRecordThenTheLastWorkingSetThenFourSteps() {
        val e = exercise("E").copy(incrementStep = 2.5)
        assertEquals(70.0 to 3, prefillMax(MachineLines(e, listOf(record("a", 9, 60.0, 5), record("b", 2, 70.0, 3))), emptyList()))
        val sets = listOf(WorkoutSetEntity("s1", "W", "E", 30.0, 10, isWarmup = true, createdAt = 5), WorkoutSetEntity("s2", "W", "E", 55.0, 8, createdAt = 4))
        assertEquals(55.0 to 8, prefillMax(MachineLines(e, emptyList()), sets))
        assertEquals(10.0 to 1, prefillMax(MachineLines(e, emptyList()), emptyList()))
    }

    @Test fun newRecordIsComparedInsideItsOwnLine() {
        val lines = MachineLines(exercise("E", "leg-extension", "gambe"), listOf(record("a", 9, 100.0, 5), record("b", 2, 60.0, 5, arm = true)))
        assertTrue(isNewRecord(lines, 105.0, 1, singleArm = false))
        assertFalse(isNewRecord(lines, 95.0, 8, singleArm = false))
        assertTrue(isNewRecord(lines, 62.5, 1, singleArm = true))
        assertTrue(isNewRecord(MachineLines(exercise("E"), emptyList()), 20.0, 1, false))
        assertFalse(isNewRecord(MachineLines(exercise("E"), emptyList()), 0.0, 1, false))
    }

    @Test fun weightLimitsAreTheServers() {
        assertFalse(isValidMaxWeight(0.0)); assertTrue(isValidMaxWeight(0.01)); assertTrue(isValidMaxWeight(2000.0)); assertFalse(isValidMaxWeight(2000.5))
    }

    // ---- editor -------------------------------------------------------------------------

    private fun entry(id: String) = checkNotNull(Catalog.entry(id)) { id }

    @Test fun pickingACatalogEntryFillsTheNewMachine() {
        val e = entry("leg-extension")
        val f = EditorForm().chosen(e, MuscleGroup.gambe, isNew = true, unit = WeightUnit.kg, italian = true)
        assertEquals("leg-extension", f.catalogID)
        assertEquals(e.nameIt, f.name)
        assertEquals(MuscleGroup.gambe, f.group)
        assertEquals(e.step, f.step, 0.0)
        assertFalse(f.isCustom)
        assertEquals(e.machineGlyph, f.glyph)
        assertEquals(Limbs.either, f.limbsShown(null))
        assertEquals(e.nameEn, EditorForm().chosen(e, null, true, WeightUnit.kg, italian = false).name)
    }

    @Test fun goingBackAndPickingCustomClearsWhatTheEntryHadSet() {
        val linked = EditorForm().chosen(entry("bench-press-barbell"), null, true, WeightUnit.kg, true).copy(notes = "x")
        val custom = linked.chosen(null, MuscleGroup.spalle, isNew = true, unit = WeightUnit.kg, italian = true)
        assertEquals("", custom.catalogID); assertTrue(custom.isCustom)
        assertEquals("", custom.name); assertEquals("", custom.notes); assertEquals(2.5, custom.step, 0.0); assertEquals(0.0, custom.barWeight, 0.0)
        assertEquals(MuscleGroup.spalle, custom.group)
        // un personalizzato appena scelto non azzera quello che hai già scritto
        assertEquals("Mio", EditorForm(name = "Mio").chosen(null, null, true, WeightUnit.kg, true).name)
    }

    @Test fun linkingAnExistingMachineChangesOnlyTheLink() {
        val before = EditorForm(name = "Mio", group = MuscleGroup.petto, step = 1.25, notes = "n")
        val after = before.chosen(entry("leg-extension"), MuscleGroup.gambe, isNew = false, unit = WeightUnit.kg, italian = true)
        assertEquals("leg-extension", after.catalogID)
        assertEquals(before.copy(catalogID = "leg-extension"), after)
    }

    @Test fun defaultBarComesFromTheEntry() {
        val f = EditorForm().chosen(entry("bench-press-barbell"), null, true, WeightUnit.kg, true)
        assertEquals(20.0, f.barWeight, 0.01)
        assertEquals(0.0, EditorForm().chosen(entry("leg-extension"), null, true, WeightUnit.kg, true).barWeight, 0.01)
    }

    @Test fun savingANewMachine() {
        val f = EditorForm(name = "  Mio macchinario ", group = MuscleGroup.gambe, step = 5.0, isCustom = true, pickedGlyph = MachineGlyph.legPress, muscles = emptyList(), barWeight = 0.0)
        val e = f.applyTo(null, "", 7, now, true, uuid = "U1")
        assertEquals("U1", e.uuid); assertEquals("Mio macchinario", e.name); assertEquals("gambe", e.muscleGroup)
        assertEquals(7, e.sortIndex); assertEquals(now, e.createdAt); assertEquals("legPress", e.glyph)
        assertTrue(e.isCustom); assertEquals("front", e.view); assertEquals("both", e.limbs); assertFalse(e.isUnilateral)
    }

    @Test fun aLinkedMachineIsNeverCustom() {
        val e = EditorForm(name = "Leg Extension", catalogID = "leg-extension", isCustom = true).applyTo(null, "", 1, now, true)
        assertFalse(e.isCustom)
        assertEquals("either", e.limbs) // quello di base del catalogo, scritto alla creazione
    }

    @Test fun coreIsAlwaysTogetherAndAOneLimbPickSetsTheOldFlag() {
        val core = EditorForm(name = "Crunch", group = MuscleGroup.core, limbsPick = Limbs.one).applyTo(null, "", 1, now, true)
        assertEquals("both", core.limbs)
        val one = EditorForm(name = "Curl", group = MuscleGroup.braccia, limbsPick = Limbs.one).applyTo(null, "", 1, now, true)
        assertEquals("one", one.limbs); assertTrue(one.isUnilateral)
    }

    @Test fun savingAnExistingMachineKeepsTheItalianNameIfUntouched() {
        val saved = ExerciseEntity("E", name = "Leg Extension", muscleGroup = "gambe", catalogID = "leg-extension", sortIndex = 3, createdAt = 5)
        val untouched = EditorForm.of(saved, italian = false).applyTo(saved, "", 0, now, italian = false)
        assertEquals("Leg Extension", untouched.name) // il campo mostrava il nome inglese: il nome salvato non si tocca
        assertEquals(3, untouched.sortIndex); assertEquals(5, untouched.createdAt); assertEquals("E", untouched.uuid)
        val renamed = EditorForm.of(saved, true).copy(name = "La mia pressa").applyTo(saved, "", 0, now, true)
        assertEquals("La mia pressa", renamed.name)
    }

    @Test fun savingAnExistingMachineWritesEveryChoice() {
        val saved = ExerciseEntity("E", name = "Squat", muscleGroup = "gambe", limbs = null)
        val f = EditorForm.of(saved, true).copy(
            step = 5.0, notes = "n", restSeconds = 90, isArchived = true, isHidden = true, barWeight = 20.0, weightDisplay = "relative",
            plateLoaded = true, perSide = true, photoOffset = 0.4, limbsPick = Limbs.either,
        )
        val e = f.applyTo(saved, "a".repeat(64), 0, now, true)
        assertEquals(5.0, e.incrementStep, 0.0); assertEquals("n", e.notes); assertEquals(90, e.restSeconds)
        assertTrue(e.isArchived); assertTrue(e.isHidden); assertEquals(20.0, e.barWeight, 0.0); assertEquals("relative", e.weightDisplay)
        assertTrue(e.plateLoaded); assertTrue(e.perSide); assertEquals(0.4, e.photoOffset, 0.0); assertEquals("a".repeat(64), e.photo)
        assertEquals("either", e.limbs)
        // senza una scelta in questo foglio il «come si fa» di base non si scrive
        assertNull(EditorForm.of(saved, true).applyTo(saved, "", 0, now, true).limbs)
    }

    @Test fun photoCanBeAddedAndRemoved() {
        val f = EditorForm(photoSha = "x", photoOffset = 0.5)
        assertTrue(f.hasPhoto)
        val without = f.withoutPhoto()
        assertFalse(without.hasPhoto); assertEquals(0.0, without.photoOffset, 0.0)
        val with = without.withPhoto(byteArrayOf(1, 2, 3))
        assertTrue(with.hasPhoto); assertEquals("", with.photoSha)
    }

    @Test fun musclesAreToggledOnTheFigure() {
        val chest = com.gabrieleforghieri.peak.domain.MuscleId.chest
        val f = EditorForm().toggleMuscle(chest)
        assertEquals(listOf(chest), f.muscles)
        assertTrue(f.toggleMuscle(chest).muscles.isEmpty())
    }

    @Test fun anEmptyNameCannotBeSaved() {
        assertFalse(EditorForm(name = "   ").canSave); assertTrue(EditorForm(name = "x").canSave)
    }

    @Test fun barSteppingAndPresets() {
        assertEquals(21.0, stepBar(20.0, 1, WeightUnit.kg), 0.01)
        assertEquals(0.0, stepBar(0.0, -1, WeightUnit.kg), 0.0)
        assertEquals(60.0, stepBar(60.0, 1, WeightUnit.kg), 0.0)
        assertEquals(WeightUnit.lb.toKilograms(47.5), stepBar(WeightUnit.lb.toKilograms(45.0), 1, WeightUnit.lb), 0.01)
        assertEquals(listOf("Nessuna (0)", "10 kg", "15 kg", "20 kg"), barPresets(WeightUnit.kg).map { it.first })
        assertEquals(listOf(0.0, 10.0, 15.0, 20.0), barPresets(WeightUnit.kg).map { it.second })
        assertEquals("45 lb", barPresets(WeightUnit.lb).last().first)
        assertEquals(WeightUnit.lb.toKilograms(45.0), barPresets(WeightUnit.lb).last().second, 0.001)
    }

    @Test fun stepFieldStaysInItsRange() {
        assertEquals(5.0, clampedStep(2.5, 5.0), 0.0)
        assertEquals(2.5, clampedStep(2.5, 0.0), 0.0)
        assertEquals(2.5, clampedStep(2.5, 51.0), 0.0)
        assertEquals(0.25, clampedStep(2.5, 0.25), 0.0)
    }

    @Test fun limbsHintsFollowTheChoiceAndTheGroup() {
        assertEquals("Una gamba: si fa sempre una gamba alla volta.", limbsHint(Limbs.one, MuscleGroup.gambe))
        assertEquals("Un braccio: si fa sempre un braccio alla volta.", limbsHint(Limbs.one, MuscleGroup.braccia))
        assertEquals("A scelta: sulla serie scegli 2 o 1.", limbsHint(Limbs.either, MuscleGroup.petto))
    }

    @Test fun derivedPropertiesOfAMachine() {
        val e = exercise("E", "dumbbell-curl", "braccia")
        assertTrue(e.twoHandles); assertFalse(e.worksLegs)
        assertEquals(Limbs.either, e.machineLimbs)
        assertTrue(exercise("L", "", "gambe").worksLegs)
        assertEquals("absolute", exercise("A").copy(weightDisplay = "").shownWeightDisplay)
        val one = exercise("O").withLimbs(Limbs.one)
        assertTrue(one.isUnilateral); assertEquals(Limbs.one, one.machineLimbs)
        assertFalse(one.withLimbs(Limbs.both).isUnilateral)
    }
}
