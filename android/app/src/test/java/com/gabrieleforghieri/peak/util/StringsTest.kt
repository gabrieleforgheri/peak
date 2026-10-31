package com.gabrieleforghieri.peak.util

import com.gabrieleforghieri.peak.domain.MedalID
import com.gabrieleforghieri.peak.domain.Motivation
import com.gabrieleforghieri.peak.domain.MuscleGroup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** I testi: la chiave è l'italiano, il nome della risorsa lo calcolano lo script (gen-assets.ts) e `Strings.slug` allo stesso modo. */
class StringsTest {
    private fun names(dir: String): Set<String> =
        Regex("""<string name="(t_[^"]+)"""").findAll(File("src/main/res/$dir/strings.xml").readText()).map { it.groupValues[1] }.toSet()

    private val it = names("values")
    private val en = names("values-en")

    @Test fun hashAndSlugMatchTheScript() {
        // valori calcolati da `slug` in gen-assets.ts (node): se cambiano, cambiano i nomi di tutte le risorse
        assertEquals("t_1_allenamento_4de726f8", Strings.slug("1 allenamento"))
        assertEquals("t_vedi_tutto_4554d963", Strings.slug("Vedi tutto"))
        assertEquals("t_perche_4f5383af", Strings.slug("Perché"))
        assertEquals("t_allenamento_f54a04d7", Strings.slug("Allenamento"))
        assertEquals("t_mancano_per_lato_con_i_dischi_disponibil_fa8aa4bd", Strings.slug("Mancano %@ per lato con i dischi disponibili."))
    }

    @Test fun italianAndEnglishHaveTheSameNames() {
        assertTrue(it.size > 1000)
        assertEquals(it, en)
    }

    @Test fun theShellTextsExistInBothLanguages() {
        for (text in listOf("Allenamento", "Macchinari", "Me", "Amici", "In arrivo", "Questa scheda è in costruzione.")) {
            assertTrue("manca «$text»", Strings.slug(text) in it)
        }
    }

    /** I testi italiani che stanno nel dominio (gruppi, medaglie, frasi) devono avere la loro traduzione, come `.tr` su iPhone. */
    @Test fun domainTextsAreTranslatable() {
        val missing = mutableListOf<String>()
        fun check(text: String) { if (Strings.slug(text) !in it) missing += text }
        MuscleGroup.entries.forEach { check(it.title); MuscleGroup.SideKind.entries.forEach { k -> check(it.side(k)) } }
        MedalID.entries.forEach { check(it.title); check(it.info) }
        Motivation.italian.forEach { check(it) }
        assertTrue("senza traduzione: $missing", missing.isEmpty())
    }
}
