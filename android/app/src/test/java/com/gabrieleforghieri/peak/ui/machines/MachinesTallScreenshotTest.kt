package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.ui.test.junit4.createComposeRule
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.ui.screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Le schermate lunghe (dettaglio ed editor) su un telefono alto 1900 dp, per vederle intere senza scorrere. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h1900dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class MachinesTallScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val s = MachinesSamples()

    // ---- il dettaglio -------------------------------------------------------------------

    @Test fun detailEither() = compose.screenshot("machines-dettaglio-a-scelta") {
        ExerciseDetailContent(s.legExtension, s.legRecords, s.legSets, s.workouts, onBack = {}, now = s.now)
    }

    @Test fun detailWithBarAndRelativeLoad() = compose.screenshot("machines-dettaglio-sbarra-relativo") {
        ExerciseDetailContent(s.benchPress, s.benchRecords, s.benchSets, s.workouts, onBack = {}, now = s.now)
    }

    @Test fun detailWithoutRecords() = compose.screenshot("machines-dettaglio-vuoto") {
        ExerciseDetailContent(s.customPress, emptyList(), emptyList(), s.workouts, onBack = {}, now = s.now)
    }

    @Test fun detailWithSingleRecord() = compose.screenshot("machines-dettaglio-un-massimale") {
        ExerciseDetailContent(s.dumbbellCurl, s.curlRecords, emptyList(), s.workouts, onBack = {}, now = s.now)
    }

    private fun fromCatalog(id: String) = EditorForm().chosen(checkNotNull(Catalog.entry(id)), null, isNew = true, unit = com.gabrieleforghieri.peak.domain.WeightUnit.kg, italian = true)

    @Test fun wizardStepThreeFromCatalog() = compose.screenshot("machines-editor-passo-3") {
        ExerciseEditorContent(null, onClose = {}, startOnEssentials = true, initialForm = fromCatalog("lat-pulldown"))
    }

    @Test fun wizardStepThreeCustom() = compose.screenshot("machines-editor-passo-3-personalizzato") {
        ExerciseEditorContent(null, onClose = {}, startOnEssentials = true, initialForm = EditorForm(group = MuscleGroup.spalle, isCustom = true, muscles = listOf(com.gabrieleforghieri.peak.domain.MuscleId.deltoids)))
    }

    @Test fun wizardStepThreeWithMoreOpen() = compose.screenshot("machines-editor-passo-3-altro") {
        ExerciseEditorContent(null, onClose = {}, startOnEssentials = true, startMoreOpen = true, initialForm = fromCatalog("bench-press-barbell"))
    }

    @Test fun editorTop() = compose.screenshot("machines-editor-modifica") {
        ExerciseEditorContent(s.benchPress, onClose = {})
    }

    @Test fun editorOfACustomMachine() = compose.screenshot("machines-editor-modifica-personalizzato") {
        ExerciseEditorContent(s.customPress, onClose = {})
    }

}
