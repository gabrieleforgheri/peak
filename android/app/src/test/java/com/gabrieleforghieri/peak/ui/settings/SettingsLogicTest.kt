package com.gabrieleforghieri.peak.ui.settings

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.domain.AppSettings
import com.gabrieleforghieri.peak.domain.Plates
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.sync.SyncEngine
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {
    @Test fun categoriesAreTheSixOfTheIphoneWithoutWatch() {
        assertEquals(listOf("general", "training", "account", "health", "backup", "data"), SettingsCategory.entries.map { it.name })
        assertEquals(listOf("Generale", "Allenamento", "Account yG", "App Salute", "Backup", "Dati"), SettingsCategory.entries.map { it.title })
    }

    @Test fun aRouteCategoryOpensItsPageAndAnythingElseOpensTheList() {
        assertEquals(SettingsCategory.account, SettingsCategory.of("account"))
        assertNull(SettingsCategory.of("watch"))
        assertNull(SettingsCategory.of(null))
    }

    @Test fun tappingAPlateTurnsItOffThenOn() {
        val plates = AppSettings().availablePlates
        val off = togglePlate(plates, 20.0)
        assertFalse(hasPlate(off, 20.0))
        assertEquals(plates.size - 1, off.size)
        val on = togglePlate(off, 20.0)
        assertTrue(hasPlate(on, 20.0))
        assertEquals(plates.size, on.size)
    }

    @Test fun plateMatchingToleratesPoundsConvertedToKilograms() {
        val pounds = Plates.poundCatalog
        val chosen = pounds.take(3)
        // 45 lb in kg arriva da un altro calcolo: una differenza minima non crea un secondo disco
        val again = togglePlate(chosen, chosen[0] + 0.0004)
        assertEquals(2, again.size)
        assertEquals(4, togglePlate(chosen, pounds[3]).size)
    }

    @Test fun unitChangeMovesDefaultPlatesToTheNewUnit() {
        // il cambio dell'unità della pagina Generale: dischi «di serie» passano a quelli americani, e tornano
        val lb = AppSettings().withUnit(WeightUnit.lb).first
        assertEquals(Plates.poundCatalog, lb.availablePlates)
        assertEquals(Plates.defaults(WeightUnit.kg), lb.withUnit(WeightUnit.kg).first.availablePlates)
        val custom = AppSettings(availablePlates = listOf(20.0, 10.0)).withUnit(WeightUnit.lb).first
        assertEquals(listOf(20.0, 10.0), custom.availablePlates)
    }

    @Test fun accountStatusReadsLikeTheIphone() {
        assertEquals("collegato", accountStatusTitle(SyncEngine.State.Connected))
        assertEquals("scaduto", accountStatusTitle(SyncEngine.State.Expired))
        assertEquals("non collegato", accountStatusTitle(SyncEngine.State.Disconnected))
        assertEquals("non collegato", accountStatusTitle(SyncEngine.State.Onboarding))
    }

    @Test fun anEmptyAccountNameFallsBack() {
        assertEquals("Gabriele", accountDisplayName("Gabriele", "il tuo account yG"))
        assertEquals("il tuo account yG", accountDisplayName("", "il tuo account yG"))
        assertEquals("il tuo account yG", accountDisplayName(null, "il tuo account yG"))
    }

    @Test fun theBackupFileIsNamedAfterTheDay() {
        assertEquals("Peak-2026-11-01.json", backupFileName(LocalDate.of(2026, 11, 1)))
    }

    @Test fun onlyArchivedExercisesAreListed() {
        val list = listOf(ExerciseEntity("A"), ExerciseEntity("B", isArchived = true), ExerciseEntity("C", isArchived = true))
        assertEquals(listOf("B", "C"), archived(list).map { it.uuid })
    }

    @Test fun theLanguageCanBeChosenFromAndroid13() {
        assertFalse(canApplyLanguage(32))
        assertTrue(canApplyLanguage(33))
    }
}
