package com.gabrieleforghieri.peak.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Il foglio di condivisione di sistema (`ShareSheet.swift`: `UIActivityViewController`). Qui è `Intent.ACTION_SEND` con un
 * `FileProvider`: il file sta in `cache/share/` e l'app che lo riceve ne ha il permesso di lettura.
 *
 * Serve il `<provider>` nel manifest (autorità `<applicationId>.fileprovider`, risorsa `@xml/file_paths`), che le aree non possono
 * dichiarare: finché manca, [isAvailable] è falso e chi condivide ripiega sul «salva con nome» di sistema.
 */
object ShareSheet {
    private const val DIR = "share"

    fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** Il provider è nel manifest? */
    fun isAvailable(context: Context): Boolean = context.packageManager.resolveContentProvider(authority(context), 0) != null

    /** Scrive il contenuto in un file a nome parlante nella cartella da condividere (la cache: il sistema la pulisce). */
    fun stage(context: Context, name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        return File(dir, name).apply { writeBytes(bytes) }
    }

    /** Apre il foglio di condivisione. Lancia se il provider manca o non c'è nessuna app che accetti il file. */
    fun share(context: Context, file: File, mime: String = "application/json", title: String? = null) {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
