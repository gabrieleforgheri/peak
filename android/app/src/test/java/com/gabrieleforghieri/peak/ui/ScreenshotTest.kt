package com.gabrieleforghieri.peak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.design.ExerciseCardBackground
import com.gabrieleforghieri.peak.design.ExerciseThumbnail
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.SideSegment
import com.gabrieleforghieri.peak.design.WarningBanner
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Gli screenshot di esempio: copiare questo file per provare la schermata di un'area (`screenshot(nome) { … }`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    // del catalogo: il pittogramma di un macchinario viene dalla sua voce (o da quello scelto a mano). Senza catalogID e senza glyph,
    // come su iPhone, resta il riquadro colorato vuoto: era così il primo esempio, e lo screenshot sembrava rotto.
    private val squat = ExerciseEntity("E1", name = "Squat", muscleGroup = "gambe", catalogID = "squat-barbell")

    @Test fun designComponents() = compose.screenshot("componenti") {
        var arm by remember { mutableStateOf(false) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WarningBanner(PeakIcons.warning, "Backup", "Non fai un backup da più di 30 giorni.", actionTitle = "Esporta ora", onAction = {})
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExerciseThumbnail(squat, size = 56.dp)
                SideSegment(arm, { arm = it }, MuscleGroup.braccia)
            }
            Row(Modifier.fillMaxWidth().height(120.dp)) { ExerciseCardBackground(squat) }
        }
    }

    @Test fun placeholderOfAnArea() = compose.screenshot("segnaposto-me") {
        // le schermate vere delle aree si provano così: una `composable` o il suo corpo dentro `screenshot`
        com.gabrieleforghieri.peak.design.ScreenPlaceholder("Me", "Qui va MeView.")
    }
}
