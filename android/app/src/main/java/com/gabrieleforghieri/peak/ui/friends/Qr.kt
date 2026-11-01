package com.gabrieleforghieri.peak.ui.friends

/**
 * Codice QR senza dipendenze: modalità byte, correzione d'errore M, versioni 1–10 (fino a 213 byte). Traduzione di `common/qr.ts`, che a
 * sua volta segue l'algoritmo di riferimento di Nayuki (QR Code generator, licenza MIT) ridotto a ciò che serve per un link. Stesso testo,
 * stesso disegno, modulo per modulo (la prova `QrTest` lo confronta con quello che scrive `qr.ts`).
 */
object Qr {
    private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26) // livello M, per versione
    private val BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)
    private const val MAX_VERSION = 10

    private fun rawModules(ver: Int): Int {
        var n = (16 * ver + 128) * ver + 64
        if (ver >= 2) {
            val a = ver / 7 + 2
            n -= (25 * a - 10) * a - 55
            if (ver >= 7) n -= 36
        }
        return n
    }

    private fun dataCodewords(ver: Int): Int = rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * BLOCKS[ver]

    private fun mul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11d)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun divisor(degree: Int): IntArray {
        val out = IntArray(degree)
        out[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in out.indices) {
                out[j] = mul(out[j], root)
                if (j + 1 < out.size) out[j] = out[j] xor out[j + 1]
            }
            root = mul(root, 2)
        }
        return out
    }

    private fun remainder(data: List<Int>, div: IntArray): IntArray {
        val out = IntArray(div.size)
        for (b in data) {
            val factor = b xor out[0]
            System.arraycopy(out, 1, out, 0, out.size - 1)
            out[out.size - 1] = 0
            for (i in div.indices) out[i] = out[i] xor mul(div[i], factor)
        }
        return out
    }

    /** Le righe del QR: `true` = modulo scuro. Il testo è codificato in UTF-8. Troppo lungo per la versione 10: [IllegalArgumentException]. */
    fun matrix(text: String): List<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        var ver = 1
        while (ver <= MAX_VERSION && 4 + (if (ver <= 9) 8 else 16) + bytes.size * 8 > dataCodewords(ver) * 8) ver++
        require(ver <= MAX_VERSION) { "testo troppo lungo per il QR" }

        // dati: modo byte, lunghezza, byte, terminatore e riempimento
        val bits = ArrayList<Int>()
        fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits.add((v ushr i) and 1) }
        put(4, 4); put(bytes.size, if (ver <= 9) 8 else 16); bytes.forEach { put(it.toInt() and 0xff, 8) }
        val cap = dataCodewords(ver) * 8
        put(0, minOf(4, cap - bits.size))
        while (bits.size % 8 != 0) bits.add(0)
        var pad = 0xec
        while (bits.size < cap) { put(pad, 8); pad = pad xor (0xec xor 0x11) }
        val data = ArrayList<Int>()
        for (i in 0 until bits.size step 8) {
            var v = 0
            for (k in 0 until 8) v = (v shl 1) or bits[i + k]
            data.add(v)
        }

        // blocchi con correzione d'errore, intercalati
        val nBlocks = BLOCKS[ver]
        val eccLen = ECC_PER_BLOCK[ver]
        val raw = rawModules(ver) / 8
        val nShort = nBlocks - raw % nBlocks
        val shortLen = raw / nBlocks
        val div = divisor(eccLen)
        val blocks = ArrayList<List<Int>>()
        var k = 0
        for (i in 0 until nBlocks) {
            val d = data.subList(k, minOf(data.size, k + shortLen - eccLen + (if (i < nShort) 0 else 1))).toMutableList()
            k += d.size
            val ecc = remainder(d, div)
            if (i < nShort) d.add(0)
            blocks.add(d + ecc.toList())
        }
        val words = ArrayList<Int>()
        for (i in blocks[0].indices) blocks.forEachIndexed { j, b -> if (i != shortLen - eccLen || j >= nShort) words.add(b[i]) }

        // disegno
        val size = ver * 4 + 17
        val mod = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) }
        fun set(x: Int, y: Int, dark: Boolean) { mod[y][x] = dark; fn[y][x] = true }
        for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                val d = maxOf(Math.abs(dx), Math.abs(dy))
                if (x in 0 until size && y in 0 until size) set(x, y, d != 2 && d != 4)
            }
        }
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
        val nAlign = if (ver == 1) 0 else ver / 7 + 2
        val pos = mutableListOf(6)
        if (nAlign != 0) {
            val step = Math.ceil((ver * 4 + 4).toDouble() / (nAlign * 2 - 2)).toInt() * 2
            var p = size - 7
            while (pos.size < nAlign) { pos.add(1, p); p -= step }
        }
        for (i in pos.indices) for (j in pos.indices) {
            if ((i == 0 && j == 0) || (i == 0 && j == pos.size - 1) || (i == pos.size - 1 && j == 0)) continue
            for (dy in -2..2) for (dx in -2..2) set(pos[i] + dx, pos[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
        }
        fun format(mask: Int) {
            val d = (0 shl 3) or mask // livello M = 0
            var rem = d
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val b = ((d shl 10) or rem) xor 0x5412
            fun bit(i: Int) = ((b ushr i) and 1) != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }
        format(0) // riserva i moduli del formato; si riscrive dopo la maschera
        if (ver >= 7) {
            var rem = ver
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1f25)
            val b = (ver shl 12) or rem
            for (i in 0 until 18) {
                val a = size - 11 + i % 3
                val c = i / 3
                val dark = ((b ushr i) and 1) != 0
                set(a, c, dark); set(c, a, dark)
            }
        }
        // codici, a zig-zag da destra
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) for (j in 0 until 2) {
                val x = right - j
                val y = if (((right + 1) and 2) == 0) size - 1 - vert else vert
                if (!fn[y][x] && i < words.size * 8) {
                    mod[y][x] = ((words[i ushr 3] ushr (7 - (i and 7))) and 1) != 0
                    i++
                }
            }
            right -= 2
        }
        fun mask(m: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val inv = when (m) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> (x * y) % 2 + (x * y) % 3 == 0
                    6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                    else -> (((x + y) % 2) + ((x * y) % 3)) % 2 == 0
                }
                if (!fn[y][x] && inv) mod[y][x] = !mod[y][x]
            }
        }
        fun penalty(): Int {
            var p = 0
            val lines = mod.map { it.toList() } + (0 until size).map { x -> mod.map { row -> row[x] } }
            val a = listOf(true, false, true, true, true, false, true, false, false, false, false)
            val b = a.reversed()
            for (l in lines) {
                var run = 1
                for (n in 0 until size) {
                    if (n > 0 && l[n] == l[n - 1]) {
                        run++
                        if (run == 5) p += 3 else if (run > 5) p++
                    } else run = 1
                }
                for (n in 0..size - 11) {
                    if (a.indices.all { l[n + it] == a[it] }) p += 40
                    if (b.indices.all { l[n + it] == b[it] }) p += 40
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                if (mod[y][x] == mod[y][x + 1] && mod[y][x] == mod[y + 1][x] && mod[y][x] == mod[y + 1][x + 1]) p += 3
            }
            val dark = mod.sumOf { r -> r.count { it } }
            return p + Math.abs(dark * 20 - size * size * 10) / (size * size) * 10 // ogni 5% di scarto dal 50% costa 10
        }
        var best = 0
        var bestScore = Int.MAX_VALUE
        for (m in 0 until 8) {
            mask(m); format(m)
            val s = penalty()
            if (s < bestScore) { best = m; bestScore = s }
            mask(m) // toglie la maschera
        }
        mask(best); format(best)
        return mod.toList()
    }
}
