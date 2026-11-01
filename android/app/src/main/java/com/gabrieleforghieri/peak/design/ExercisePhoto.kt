package com.gabrieleforghieri.peak.design

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.gabrieleforghieri.peak.sync.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dove stanno i byte delle foto (`PeakApplication.photos`). `null` in una prova di schermata: nessuna foto, si vede il pittogramma. */
val LocalPhotoStore = staticCompositionLocalOf<PhotoStore?> { null }

private val cache = LruCache<String, ImageBitmap>(24)

/**
 * La foto di un macchinario dal suo sha256 (`ExerciseEntity.photo`), letta e decodificata fuori dal thread principale e tenuta in
 * una piccola cache. `null` finché non è pronta, o se non c'è ("" o file mancante).
 */
@Composable
fun rememberExercisePhoto(sha: String): ImageBitmap? {
    val store = LocalPhotoStore.current
    val photo by produceState(initialValue = if (sha.isEmpty()) null else cache.get(sha), sha, store) {
        value = when {
            sha.isEmpty() || store == null -> null
            else -> cache.get(sha) ?: withContext(Dispatchers.IO) {
                store.read(sha)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }?.also { cache.put(sha, it) }
        }
    }
    return photo
}
