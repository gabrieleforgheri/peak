package com.gabrieleforghieri.peak.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareSheetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun theFileToShareIsStagedInTheShareFolderOfTheCache() {
        val file = ShareSheet.stage(context, "Peak-2026-11-01.json", "{}".toByteArray())
        assertEquals("share", file.parentFile?.name)
        assertEquals(context.cacheDir, file.parentFile?.parentFile)
        assertEquals("{}", file.readText())
    }

    @Test fun withoutTheProviderInTheManifestSharingIsNotAvailable() {
        // finché chi unisce le aree non dichiara il FileProvider, la pagina Backup ripiega sul «salva con nome»
        assertEquals("${context.packageName}.fileprovider", ShareSheet.authority(context))
        assertFalse(ShareSheet.isAvailable(context))
    }

    @Test fun datesUseTheLocaleOfTheCaller() {
        val may = 1_778_000_000_000L
        assertTrue(shortDate(may, java.util.Locale.ITALY).contains("2026"))
        assertTrue(shortDateTime(may, java.util.Locale.ITALY).contains(":"))
    }
}
