package com.gabrieleforghieri.peak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le impostazioni da e verso il database: gli stessi limiti di `cleanSetting` in common/docs.test.ts. */
class SettingsTest {
    private fun decode(vararg p: Pair<String, String>) = SettingsCodec.decode(mapOf(*p))

    @Test fun emptyStoreGivesTheDefaults() = assertEquals(AppSettings(), decode())

    @Test fun validValuesAreRead() {
        val s = decode(
            "unit" to "\"lb\"", "accent" to "\"violet\"", "restSeconds" to "90", "warmupRestPercent" to "0", "repRangeMin" to "6",
            "availablePlates" to "[20,10,5]", "weeklyGoal" to "4", "language" to "\"en\"", "activeProgram" to "\"00000000-0000-4000-8000-5045414b0001\"",
            "device.soundsEnabled" to "false",
        )
        assertEquals(WeightUnit.lb, s.unit)
        assertEquals(AccentPalette.violet, s.accent)
        assertEquals(90, s.restSeconds)
        assertEquals(0, s.warmupRestPercent)
        assertEquals(6, s.repRangeMin)
        assertEquals(listOf(20.0, 10.0, 5.0), s.availablePlates)
        assertEquals(4, s.weeklyGoal)
        assertEquals("en", s.language)
        assertEquals(Uuids.DEFAULT_PROGRAM, s.activeProgram)
        assertEquals(false, s.soundsEnabled)
    }

    /** Fuori scala o del tipo sbagliato: resta il valore di partenza (`value` errato nel server). */
    @Test fun invalidValuesAreIgnored() {
        val s = decode(
            "unit" to "\"stone\"", "accent" to "\"rosa\"", "restSeconds" to "5", "warmupRestPercent" to "101", "weeklyGoal" to "8",
            "restTimerEnabled" to "\"yes\"", "tourSeen" to "12.5", "language" to "\"fr\"", "activeProgram" to "\"x\"", "availablePlates" to "[0,5]",
            "repRangeMax" to "\"12\"",
        )
        assertEquals(AppSettings(), s)
        assertEquals(AppSettings(), decode("restSeconds" to "non è json"))
    }

    @Test fun encodeReadsBackTheSame() {
        val s = AppSettings(unit = WeightUnit.lb, accent = AccentPalette.coral, restSeconds = 75, availablePlates = listOf(25.0, 1.25), language = "it", activeProgram = Uuids.DEFAULT_PROGRAM)
        val stored = SettingKeys.synced.associateWith { SettingsCodec.encode(it, s)!! }
        assertEquals(s, SettingsCodec.decode(stored))
        assertNull(SettingsCodec.encode("boh", s))
    }
}
