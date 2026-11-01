package com.gabrieleforghieri.peak.ui.me

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.ui.nav.PeakEnvironment
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** `MeScreen` sul database vero (Room in Robolectric): legge, ricalcola quando i dati cambiano e scrive le pesate. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h2000dp-xhdpi")
class MeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<PeakApplication>()

    /** Il database dell'app è uno solo per tutto il processo di prova: ogni prova riparte vuota. */
    @Before fun clean() = runBlocking { withContext(Dispatchers.IO) { app.database.clearAllTables() } }

    private fun show() = compose.setContent { PeakEnvironment(app) { PeakTheme { MeScreen() } } }

    @Test fun emptyDatabaseShowsTheInvite() {
        show()
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("Ancora nessun allenamento") > 0 }
        compose.onNodeWithText("Ancora nessun allenamento").assertIsDisplayed()
        compose.onNodeWithText("Nessuna pesata registrata").assertIsDisplayed()
    }

    @Test fun sectionsAppearWhenAWorkoutIsFinished() {
        show()
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("Ancora nessun allenamento") > 0 }
        runBlocking {
            val now = System.currentTimeMillis()
            app.database.exercises().upsert(ExerciseEntity("E", name = "Panca piana"))
            app.database.workouts().upsert(WorkoutEntity("W", date = now, endedAt = now + 1000))
            app.database.sets().upsert(WorkoutSetEntity("S", "W", "E", 80.0, 8, createdAt = now))
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("PANORAMICA") > 0 }
        compose.onNodeWithText("PANORAMICA").assertIsDisplayed()
        compose.onNodeWithText("Ancora nessun allenamento").assertDoesNotExistCompat()
    }

    @Test fun addingAWeighingWritesToTheDatabase() {
        show()
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("Aggiungi pesata") > 0 }
        compose.onNodeWithText("Aggiungi pesata").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("Salva") > 0 }
        compose.onNodeWithText("Salva").performClick()
        compose.waitUntil(30_000) { runBlocking { app.database.bodyWeights().all().size } == 1 }
        val saved = runBlocking { app.database.bodyWeights().all() }.single()
        assertEquals(75.0, saved.weight, 0.0) // senza pesate prima, parte da 75 come su iPhone
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("75 kg") > 0 }
    }

    @Test fun deletingAWeighingRemovesIt() {
        runBlocking { app.database.bodyWeights().upsert(BodyWeightEntity("B", System.currentTimeMillis(), 80.0)) }
        show()
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("80 kg") > 0 }
        compose.onNodeWithText("80 kg").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTextCount("Cancella pesata") > 0 }
        compose.onNodeWithText("Cancella pesata").performClick()
        compose.waitUntil(30_000) { runBlocking { app.database.bodyWeights().all().isEmpty() } }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
