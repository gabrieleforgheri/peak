package com.gabrieleforghieri.peak.ui.friends

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.PeakDatabase
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.TestCatalog
import com.gabrieleforghieri.peak.domain.Uuids
import com.gabrieleforghieri.peak.domain.uuid
import com.gabrieleforghieri.peak.ui.friends.social.LeagueRow
import com.gabrieleforghieri.peak.ui.friends.social.SharedRow
import com.gabrieleforghieri.peak.ui.friends.social.SharedTemplate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le regole di Amici (classifiche, confronto, «Copia nelle mie schede») e la scrittura vera in Room in memoria. */
@RunWith(RobolectricTestRunner::class)
class FriendsLogicTest {
    private lateinit var db: PeakDatabase

    @Before fun open() {
        TestCatalog.load()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PeakDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun row(id: Int, name: String, volume: Double, sessions: Int, streak: Int, me: Boolean = false) = LeagueRow(id, name, me, volume, sessions, streak, null)

    @Test fun leagueRanksByMetricAndPutsYouFirstOnATie() {
        val rows = listOf(row(2, "ben", 100.0, 3, 5), row(1, "io", 100.0, 4, 2, me = true), row(3, "cat", 300.0, 1, 5))
        assertEquals(listOf("cat", "io", "ben"), FriendsLogic.ranked(rows, LeagueMetric.volume).map { it.username })
        assertEquals(listOf("io", "ben", "cat"), FriendsLogic.ranked(rows, LeagueMetric.sessions).map { it.username })
        assertEquals(listOf("ben", "cat", "io"), FriendsLogic.ranked(rows, LeagueMetric.streak).map { it.username })
        assertEquals(2, FriendsLogic.myPlace(FriendsLogic.ranked(rows, LeagueMetric.volume)))
        assertEquals(0, FriendsLogic.myPlace(rows.filter { !it.isMe }))
    }

    @Test fun barFractionNeverDividesByZeroOrOverflows() {
        assertEquals(0.0, FriendsLogic.barFraction(5.0, 0.0), 0.0)
        assertEquals(0.5, FriendsLogic.barFraction(5.0, 10.0), 0.0)
        assertEquals(1.0, FriendsLogic.barFraction(15.0, 10.0), 0.0)
        assertEquals(0.0, FriendsLogic.barFraction(-1.0, 10.0), 0.0)
    }

    @Test fun percentAboveRoundsAwayFromZeroLikeSwift() {
        assertNull(FriendsLogic.percentAbove(100.0, null))
        assertNull(FriendsLogic.percentAbove(100.0, 0.0))
        assertEquals(10, FriendsLogic.percentAbove(110.0, 100.0))
        assertEquals(-10, FriendsLogic.percentAbove(90.0, 100.0))
        assertEquals(4, FriendsLogic.percentAbove(103.5, 100.0)) // la metà si allontana dallo zero, come `rounded()` di Swift
        assertEquals(-4, FriendsLogic.percentAbove(96.5, 100.0))
    }

    @Test fun targetLinesMatchTheIphone() {
        assertEquals("3×8–12", FriendsLogic.target(SharedRow("", "x", 3, 8, 12)))
        assertEquals("4×10", FriendsLogic.target(SharedRow("", "x", 4, 10, 10)))
        assertEquals("0–12", FriendsLogic.target(SharedRow("", "x", 0, 0, 12))) // come su iPhone: repsMin 0 resta scritto
        assertEquals("", FriendsLogic.target(SharedRow("", "x", 0, 0, 0)))
        assertEquals("3×", FriendsLogic.target(SharedRow("", "x", 3, 0, 0)))
    }

    @Test fun relativeDayIsTodayYesterdayOrTheShortDate() {
        val zone = ZoneId.of("Europe/Rome")
        val now = java.time.ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun at(day: Int, hour: Int) = java.time.ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Oggi", FriendsLogic.relativeDay(now - 3_600_000, "Oggi", "Ieri", now, zone))
        assertEquals("Ieri", FriendsLogic.relativeDay(at(31, 23), "Oggi", "Ieri", now, zone))
        assertEquals("sab 24 ott", FriendsLogic.relativeDay(at(24, 9), "Oggi", "Ieri", now, zone, Locale.ITALIAN).replace(",", ""))
    }

    @Test fun myBestUsesRecordsAndSetsButNotWarmupOrOneLimbLine() {
        val squat = ExerciseEntity("E1", name = "Squat", catalogID = "squat-barbell")
        val curl = ExerciseEntity("E2", name = "Curl", catalogID = "dumbbell-curl", muscleGroup = "braccia") // "either": ha la linea a un braccio
        val gone = ExerciseEntity("E3", name = "Panca", catalogID = "bench-press-barbell", isArchived = true)
        val custom = ExerciseEntity("E4", name = "Mio", catalogID = "")
        val records = listOf(
            MaxRecordEntity("R1", "E1", weight = 100.0, reps = 5), MaxRecordEntity("R2", "E2", weight = 20.0, reps = 10),
            MaxRecordEntity("R3", "E2", weight = 40.0, reps = 10, singleArm = true), MaxRecordEntity("R4", "E3", weight = 90.0, reps = 5),
        )
        val sets = listOf(
            WorkoutSetEntity("S1", "W", "E1", weight = 120.0, reps = 3), WorkoutSetEntity("S2", "W", "E1", weight = 200.0, reps = 1, isWarmup = true),
            WorkoutSetEntity("S3", "W", "E2", weight = 50.0, reps = 10, singleArm = true), WorkoutSetEntity("S4", "W", "E4", weight = 99.0, reps = 1),
        )
        val best = FriendsLogic.myBestByCatalog(listOf(squat, curl, gone, custom), records, sets)
        assertEquals(setOf("squat-barbell", "dumbbell-curl"), best.keys)
        assertEquals(132.0, best["squat-barbell"]!!, 0.01) // 120 x 3: 120 * (1 + 3/30)
        assertEquals(26.67, best["dumbbell-curl"]!!, 0.01) // la serie e il record a un braccio non contano
        assertEquals(listOf("E1", "E2"), FriendsLogic.linkedMine(listOf(squat, curl, gone, custom), records).map { it.uuid })
        assertEquals("R1", FriendsLogic.bestRecord(squat, records)!!.uuid)
    }

    private val shared = SharedTemplate(
        "T", "Giorno gambe",
        listOf(
            SharedRow("squat-barbell", "Squat", 3, 6, 8), SharedRow("leg-extension", "Leg Extension", 0, 0, 0),
            SharedRow("squat-barbell", "Squat", 4, 5, 5), SharedRow("", "Mio Macchinario", 2, 10, 12),
        ),
    )

    @Test fun copyingMatchesWhatYouHaveAndCreatesWhatIsMissing() = runBlocking {
        val squat = ExerciseEntity(uuid(1), name = "Squat", catalogID = "squat-barbell", sortIndex = 7)
        db.exercises().upsert(squat)
        db.programs().upsert(WorkoutProgramEntity("P1", name = "Mia"))
        db.templates().upsert(WorkoutTemplateEntity("T0", name = "Altro", sortIndex = 4))
        var n = 0
        val plan = FriendsLogic.planCopy(
            shared, db.exercises().observeAll().first(), db.templates().observeAll().first(), db.programs().observeAll().first(),
            activeProgram = "", italian = true, nowMs = 5, newUuid = { uuid(100 + n++) },
        )
        // squat c'è già; leg extension e quello personalizzato si creano; la seconda riga di squat non raddoppia
        assertEquals(listOf("leg-extension", ""), plan.newExercises.map { it.catalogID })
        assertEquals(listOf(8, 9), plan.newExercises.map { it.sortIndex })
        assertEquals(listOf(false, true), plan.newExercises.map { it.isCustom })
        assertEquals("Mio Macchinario", plan.newExercises[1].name)
        assertEquals("gambe", plan.newExercises[0].muscleGroup)
        assertNull(plan.newProgram)
        assertEquals("P1", plan.template.program)
        assertEquals(5, plan.template.sortIndex)
        assertEquals(uuid(102), plan.template.uuid)
        assertEquals("Giorno gambe", plan.template.name)
        assertEquals(
            listOf(TemplateItem(uuid(1), 3, 6, 8, null), TemplateItem(uuid(100), 0, 0, 0, null), TemplateItem(uuid(101), 2, 10, 12, null)),
            TemplateItems.decode(plan.template.items),
        )

        db.areaFriends().addSharedDay(plan.newExercises, plan.newProgram, plan.template)
        assertEquals(3, db.exercises().observeAll().first().size)
        val saved = db.templates().observeAll().first().single { it.name == "Giorno gambe" }
        assertEquals(plan.template, saved)
        // copiarlo di nuovo non crea altri macchinari
        val again = FriendsLogic.planCopy(shared, db.exercises().observeAll().first(), db.templates().observeAll().first(), db.programs().observeAll().first(), "", true, 6)
        assertTrue(again.newExercises.isEmpty())
        assertEquals(6, again.template.sortIndex)
    }

    @Test fun withoutAnyProgramCopyingCreatesTheDefaultOne() = runBlocking {
        val plan = FriendsLogic.planCopy(shared, emptyList(), emptyList(), emptyList(), "", true, 1)
        assertNotNull(plan.newProgram)
        assertEquals(Uuids.DEFAULT_PROGRAM, plan.newProgram!!.uuid)
        assertEquals("La mia scheda", plan.newProgram!!.name)
        assertEquals(Uuids.DEFAULT_PROGRAM, plan.template.program)
        assertEquals(0, plan.template.sortIndex)
        assertEquals(1, plan.newExercises.first().sortIndex) // come su iPhone: senza macchinari si parte da 1
        db.areaFriends().addSharedDay(plan.newExercises, plan.newProgram, plan.template)
        assertEquals(Uuids.DEFAULT_PROGRAM, db.programs().observeAll().first().single().uuid)
    }

    @Test fun anArchivedExerciseIsNotReused() {
        val archived = ExerciseEntity("E1", name = "Squat", catalogID = "squat-barbell", isArchived = true)
        val plan = FriendsLogic.planCopy(SharedTemplate("T", "x", listOf(SharedRow("squat-barbell", "Squat", 1, 1, 1))), listOf(archived), emptyList(), emptyList(), "", true, 1)
        assertEquals(1, plan.newExercises.size)
        assertEquals("squat-barbell", plan.newExercises.single().catalogID)
    }
}
