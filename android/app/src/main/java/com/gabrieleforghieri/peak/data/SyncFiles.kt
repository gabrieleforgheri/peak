package com.gabrieleforghieri.peak.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.gabrieleforghieri.peak.sync.BackupFiles
import com.gabrieleforghieri.peak.sync.CredentialStore
import com.gabrieleforghieri.peak.sync.PeakCredentials
import com.gabrieleforghieri.peak.sync.PendingLogin
import com.gabrieleforghieri.peak.sync.PhotoStore
import com.gabrieleforghieri.peak.sync.SyncStateFile
import com.gabrieleforghieri.peak.sync.sha256Hex
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.serialization.json.Json

// Le parti della sincronizzazione che dipendono da Android: file, Keystore, MediaStore.

/** Il collegamento (token, sub, nome) cifrato con una chiave del Keystore; sul disco mai in chiaro. Il portachiavi dell'iPhone, qui. */
class KeystoreCredentialStore(context: Context) : CredentialStore {
    private val prefs = context.getSharedPreferences("peak-credentials", Context.MODE_PRIVATE)
    private val box = SecretBox("peak.credentials")

    override fun load(): PeakCredentials? {
        val sealed = prefs.getString(KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val plain = box.open(sealed) ?: return null
        return try { Json.decodeFromString(PeakCredentials.serializer(), plain.decodeToString()) } catch (_: Exception) { null }
    }

    override fun save(credentials: PeakCredentials) {
        val sealed = box.seal(Json.encodeToString(PeakCredentials.serializer(), credentials).encodeToByteArray())
        prefs.edit().putString(KEY, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
    }

    override fun clear() { prefs.edit().remove(KEY).commit() }

    private companion object { const val KEY = "credentials" }
}

/** Il verifier PKCE fra la Custom Tab e il ritorno, cifrato come il token. */
class PrefsPendingLogin(context: Context) : PendingLogin {
    private val prefs = context.getSharedPreferences("peak-login", Context.MODE_PRIVATE)
    private val box = SecretBox("peak.login")

    override fun save(verifier: String) {
        prefs.edit().putString(KEY, Base64.encodeToString(box.seal(verifier.encodeToByteArray()), Base64.NO_WRAP)).commit()
    }

    override fun take(): String? {
        val sealed = prefs.getString(KEY, null)
        prefs.edit().remove(KEY).commit()
        return sealed?.let { box.open(Base64.decode(it, Base64.NO_WRAP)) }?.decodeToString()
    }

    private companion object { const val KEY = "verifier" }
}

/** `peak-sync.json` nella cartella privata dell'app: hash dei documenti, cursore, foto già salite. Non contiene segreti. */
class FileSyncStateFile(private val file: File) : SyncStateFile {
    override fun load(): String? = file.takeIf { it.exists() }?.readText()

    override fun save(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    override fun clear() { file.delete() }
}

/**
 * Le foto dei macchinari: i byte JPEG su disco in `photos/<sha256>.jpg`, nel database solo lo sha256. Come `ImageStore.swift` per la
 * preparazione ([prepare]: lato lungo a 640 px, JPEG al 75 %, ~40-80 KB).
 */
class ImageStore(private val dir: File) : PhotoStore {
    init { dir.mkdirs() }

    private fun file(sha: String) = File(dir, "$sha.jpg")

    override fun has(sha: String) = sha.length == 64 && file(sha).exists()

    override fun read(sha: String): ByteArray? = if (sha.length == 64) file(sha).takeIf { it.exists() }?.readBytes() else null

    override fun write(bytes: ByteArray): String {
        val sha = sha256Hex(bytes)
        val target = file(sha)
        if (!target.exists()) {
            val tmp = File(dir, "$sha.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(target)
        }
        return sha
    }

    companion object {
        const val MAX_DIMENSION = 640
        const val QUALITY = 75

        /** Ridimensiona e comprime una foto (anche con l'orientamento EXIF già applicato dal chiamante). `null` se l'immagine non è utilizzabile. */
        fun prepare(source: Bitmap): ByteArray? {
            if (source.width <= 0 || source.height <= 0) return null
            val scale = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(source.width, source.height))
            val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, Math.round(source.width * scale), Math.round(source.height * scale), true) else source
            val out = ByteArrayOutputStream()
            return if (bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) out.toByteArray() else null
        }

        fun prepare(bytes: ByteArray): ByteArray? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::prepare)
    }
}

/**
 * Le copie leggibili dall'utente: la cartella Download/Peak (MediaStore, senza permessi da Android 10); prima, la cartella Documenti dell'app.
 * Un file con lo stesso nome si riscrive.
 */
class MediaStoreBackupFiles(private val context: Context) : BackupFiles {
    override fun write(name: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            File(dir.also { it.mkdirs() }, name).writeBytes(bytes)
            return
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relative = Environment.DIRECTORY_DOWNLOADS + "/Peak/"
        val existing = resolver.query(
            collection, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf(name, relative), null,
        )?.use { c -> if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null }
        val uri = existing ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
            },
        ) ?: error("Non riesco a scrivere $name nella cartella Download.")
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Non riesco a scrivere $name nella cartella Download.")
    }
}
