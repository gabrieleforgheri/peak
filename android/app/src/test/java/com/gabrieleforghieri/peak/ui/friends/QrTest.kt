package com.gabrieleforghieri.peak.ui.friends

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le prove di `common/qr.test.ts` più il confronto con quello che scrive davvero `qr.ts` e la lettura con un decodificatore indipendente (zxing). */
class QrTest {
    private fun vectors(): List<Pair<String, List<String>>> {
        val text = javaClass.getResourceAsStream("/qr-vectors.json")!!.readBytes().decodeToString()
        return (Json.parseToJsonElement(text) as JsonArray).map { v ->
            val o = v as JsonObject
            o["text"]!!.jsonPrimitive.content to (o["rows"] as JsonArray).map { it.jsonPrimitive.content }
        }
    }

    @Test fun sizesPerVersionAndTheThreeFinderSquares() {
        assertEquals(21, Qr.matrix("ciao").size) // versione 1
        val link = "https://peak.yrb4g.com/invito/" + "A".repeat(43)
        val m = Qr.matrix(link)
        assertEquals(37, m.size) // versione 5: 73 byte stanno in 84
        for ((x, y) in listOf(0 to 0, m.size - 7 to 0, 0 to m.size - 7)) {
            for (i in 0 until 7) for (j in 0 until 7) {
                val ring = maxOf(Math.abs(i - 3), Math.abs(j - 3))
                assertEquals("riquadro in $x,$y", ring != 2, m[y + j][x + i])
            }
        }
        assertTrue("modulo scuro fisso", m[m.size - 8][8])
    }

    @Test fun sameTextSameDrawingTooLongIsAnError() {
        assertTrue(Qr.matrix("x").zip(Qr.matrix("x")).all { (a, b) -> a.contentEquals(b) })
        assertFalse(Qr.matrix("x").zip(Qr.matrix("y")).all { (a, b) -> a.contentEquals(b) })
        assertThrows(IllegalArgumentException::class.java) { Qr.matrix("a".repeat(300)) }
        assertThrows(IllegalArgumentException::class.java) { Qr.matrix("e".repeat(214)) }
        Qr.matrix("e".repeat(213)) // l'ultimo che sta nella versione 10
    }

    @Test fun identicalToTheOneCommonQrTsDraws() {
        for ((text, rows) in vectors()) {
            val m = Qr.matrix(text)
            assertEquals("dimensione di «${text.take(20)}»", rows.size, m.size)
            for (y in rows.indices) {
                val got = m[y].joinToString("") { if (it) "1" else "0" }
                assertEquals("riga $y di «${text.take(20)}»", rows[y], got)
            }
        }
    }

    @Test fun anIndependentReaderFindsTheTextBack() {
        val texts = vectors().map { it.first } + "https://peak.yrb4g.com/invito/" + "B".repeat(43)
        for (text in texts) {
            val m = Qr.matrix(text)
            val quiet = 4
            val scale = 6
            val side = (m.size + quiet * 2) * scale
            val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
            for (y in m.indices) for (x in m.indices) if (m[y][x]) {
                for (dy in 0 until scale) for (dx in 0 until scale) pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0xFF000000.toInt()
            }
            val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
            assertEquals(text, QRCodeReader().decode(bitmap).text)
        }
        assertNotEquals(Qr.matrix(texts[0]).size, Qr.matrix(texts[3]).size)
    }
}
