package com.gabrieleforghieri.peak.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

/**
 * I testi dell'interfaccia. Come su iPhone la chiave è il testo italiano (`"Allenamento".tr`): l'italiano sta in `values/strings.xml`,
 * l'inglese in `values-en/strings.xml`, entrambi generati da `android/scripts/gen-assets.ts` da `Localizable.strings` e `en.json`.
 * Il nome della risorsa è `t_<parole>_<hash>`, calcolato qui con la stessa regola dello script ([slug]).
 *
 * I testi con numeri o nomi dentro usano i segnaposto di iOS nella chiave (`%@` per testo, `%lld` per interi) e si passano gli
 * argomenti in ordine: `tr("%lld allenamenti", 3)`. Un testo che non c'è resta com'è (l'italiano).
 */
object Strings {
    /** FNV-1a a 32 bit sui byte UTF-8, in esadecimale a 8 cifre. */
    fun fnv(text: String): String {
        var h = 0x811c9dc5.toInt()
        for (b in text.toByteArray(Charsets.UTF_8)) h = (h xor (b.toInt() and 0xff)) * 0x01000193
        return (h.toLong() and 0xffffffffL).toString(16).padStart(8, '0')
    }

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Il nome della risorsa per un testo italiano. Identico a `slug` in gen-assets.ts. */
    fun slug(text: String): String {
        val words = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(MARKS, "").lowercase()
            .replace(NON_ALNUM, "_").trim('_').take(40).trimEnd('_')
        return "t_${words}_${fnv(text)}"
    }

    private val ids = ConcurrentHashMap<String, Int>()

    fun tr(context: Context, italian: String, vararg args: Any): String {
        if (italian.isEmpty()) return italian
        val id = ids.getOrPut(italian) { context.resources.getIdentifier(slug(italian), "string", context.packageName) }
        if (id == 0) return if (args.isEmpty()) italian else formatPlain(italian, args)
        return if (args.isEmpty() && !italian.contains('%')) context.getString(id) else context.getString(id, *args)
    }

    /** Senza traduzione: i segnaposto di iOS si riempiono comunque. */
    private fun formatPlain(text: String, args: Array<out Any>): String {
        var i = 0
        return Regex("%@|%lld|%lf|%%").replace(text) { m -> if (m.value == "%%") "%" else args.getOrNull(i++)?.toString() ?: m.value }
    }
}

/** `"Allenamento".tr` di iPhone. */
@Composable
fun tr(italian: String, vararg args: Any): String = Strings.tr(LocalContext.current, italian, *args)

fun Context.tr(italian: String, vararg args: Any): String = Strings.tr(this, italian, *args)
