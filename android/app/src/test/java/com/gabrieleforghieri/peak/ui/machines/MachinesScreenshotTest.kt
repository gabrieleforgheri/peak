package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.design.ExerciseCardBackground
import com.gabrieleforghieri.peak.design.ExerciseThumbnail
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.ui.screenshot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gli screenshot dell'area Macchinari, in italiano, un telefono alto 914 dp. Si registrano con `./gradlew recordRoborazziDebug`
 * (`android/docs/screens/machines-*.png`). I fogli (`PeakSheet`) non entrano nello screenshot: si prova il loro contenuto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-" + RobolectricDeviceQualifiers.Pixel7)
class MachinesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val s = MachinesSamples()

    // ---- l'elenco -----------------------------------------------------------------------

    @Test fun listWithData() = compose.screenshot("machines-lista-dati") {
        MachinesListContent(s.all, s.records, showConnectionBanner = true, expired = false)
    }

    @Test fun listConnectionExpired() = compose.screenshot("machines-lista-collegamento-scaduto") {
        MachinesListContent(s.all.filter { it.catalogID.isNotEmpty() }, s.records, showConnectionBanner = true, expired = true)
    }

    @Test fun listFilteredByGroup() = compose.screenshot("machines-lista-filtro-gambe") {
        MachinesListContent(s.all, s.records, showConnectionBanner = false, expired = false, initialGroup = MuscleGroup.gambe)
    }

    @Test fun listSearchWithoutResults() = compose.screenshot("machines-lista-nessun-risultato") {
        MachinesListContent(s.all, s.records, showConnectionBanner = false, expired = false, initialSearch = "xyz")
    }

    @Test fun listSearchFindsByAlias() = compose.screenshot("machines-lista-ricerca") {
        MachinesListContent(s.all, s.records, showConnectionBanner = false, expired = false, initialSearch = "lat")
    }

    @Test fun listEmpty() = compose.screenshot("machines-lista-vuota") {
        MachinesListContent(emptyList(), emptyList(), showConnectionBanner = true, expired = false)
    }

    @Test fun listOfMachinesWithoutRecords() = compose.screenshot("machines-lista-senza-massimali") {
        MachinesListContent(listOf(s.legExtension, s.customPress), emptyList(), showConnectionBanner = false, expired = false)
    }

    // ---- «Aggiorna massimale» -----------------------------------------------------------

    @Test fun editMax() = compose.screenshot("machines-massimale") {
        EditMaxContent(s.legExtension, MachineLines(s.legExtension, s.legRecords), s.legSets, onCancel = {}, onSaved = {}, now = s.now)
    }

    @Test fun editMaxWithoutHistory() = compose.screenshot("machines-massimale-senza-storico") {
        EditMaxContent(s.customPress, MachineLines(s.customPress, emptyList()), emptyList(), onCancel = {}, onSaved = {}, now = s.now)
    }

    // ---- il catalogo --------------------------------------------------------------------

    @Test fun catalogGroups() = compose.screenshot("machines-catalogo-gruppi") {
        CatalogPickerBody("Scegli il macchinario", allowsCustom = true, wizard = false, onPick = { _, _ -> }, onCancel = {})
    }

    @Test fun catalogGroupResults() = compose.screenshot("machines-catalogo-gruppo-gambe") {
        CatalogPickerBody("Collega al catalogo", allowsCustom = false, wizard = false, onPick = { _, _ -> }, onCancel = {}, initialGroup = MuscleGroup.gambe)
    }

    @Test fun catalogSearch() = compose.screenshot("machines-catalogo-ricerca") {
        CatalogPickerBody("Scegli il macchinario", allowsCustom = true, wizard = false, onPick = { _, _ -> }, onCancel = {}, initialQuery = "pressa")
    }

    @Test fun catalogWizardStepOne() = compose.screenshot("machines-catalogo-passo-1") {
        CatalogPickerBody("Nuovo macchinario", allowsCustom = true, wizard = true, onPick = { _, _ -> }, onCancel = {})
    }

    @Test fun catalogWizardStepTwo() = compose.screenshot("machines-catalogo-passo-2") {
        CatalogPickerBody("Nuovo macchinario", allowsCustom = true, wizard = true, onPick = { _, _ -> }, onCancel = {}, initialGroup = MuscleGroup.schiena)
    }

    @Test fun catalogNoResults() = compose.screenshot("machines-catalogo-nessun-risultato") {
        CatalogPickerBody("Scegli il macchinario", allowsCustom = true, wizard = false, onPick = { _, _ -> }, onCancel = {}, initialQuery = "qwxz")
    }

    // ---- l'editor -----------------------------------------------------------------------

    // ---- la miniatura (il difetto noto) -------------------------------------------------

    private val noGlyph = ExerciseEntity("T0", name = "Squat", muscleGroup = "gambe", catalogID = "")

    @Test fun thumbnails() = compose.screenshot("machines-miniatura") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // da sinistra: dal catalogo, con il pittogramma scelto a mano, senza nulla (resta il riquadro colorato, come su iPhone)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExerciseThumbnail(s.legExtension, size = 56.dp)
                ExerciseThumbnail(s.customPress, size = 56.dp)
                ExerciseThumbnail(s.latMachine, size = 56.dp)
                ExerciseThumbnail(noGlyph, size = 56.dp)
            }
            Row(Modifier.fillMaxWidth().height(120.dp)) { ExerciseCardBackground(s.legExtension) }
        }
    }
}
