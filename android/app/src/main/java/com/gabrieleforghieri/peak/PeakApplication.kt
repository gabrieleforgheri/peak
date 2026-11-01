package com.gabrieleforghieri.peak

import android.app.Application
import android.provider.Settings
import com.gabrieleforghieri.peak.data.FileSyncStateFile
import com.gabrieleforghieri.peak.data.ImageStore
import com.gabrieleforghieri.peak.data.KeystoreCredentialStore
import com.gabrieleforghieri.peak.data.MediaStoreBackupFiles
import com.gabrieleforghieri.peak.data.PeakDatabase
import com.gabrieleforghieri.peak.data.PeakRepository
import com.gabrieleforghieri.peak.data.PrefsPendingLogin
import com.gabrieleforghieri.peak.data.RoomSyncStorage
import com.gabrieleforghieri.peak.sync.BackupService
import com.gabrieleforghieri.peak.sync.ConnectFlow
import com.gabrieleforghieri.peak.sync.HttpPeakApi
import com.gabrieleforghieri.peak.sync.PeakHttp
import com.gabrieleforghieri.peak.sync.SyncEngine
import com.gabrieleforghieri.peak.sync.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import com.gabrieleforghieri.peak.data.RoomRecordStorage
import com.gabrieleforghieri.peak.data.SettingsRepository
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.RecordService
import com.gabrieleforghieri.peak.util.Haptics
import com.gabrieleforghieri.peak.util.SoundFX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * L'app: tiene in un punto solo il database, il catalogo, le impostazioni e il servizio dei record (l'unico percorso di scrittura delle serie).
 * Niente contenitore di dipendenze: sono pochi oggetti e vivono quanto il processo.
 */
class PeakApplication : Application() {
    val database by lazy { PeakDatabase.open(this) }
    val settings by lazy { SettingsRepository(database.settings()) }
    /** La lettura comune per le schermate (vedi `data/PeakRepository.kt`). */
    val repository by lazy { PeakRepository(database) }
    val records by lazy { RecordService(RoomRecordStorage(database)) }
    val photos by lazy { ImageStore(File(filesDir, "photos")) }
    val syncStorage by lazy { RoomSyncStorage(database, records) }
    val backup by lazy { BackupService(syncStorage, photos, MediaStoreBackupFiles(this)) }

    /** Il server è uno solo, fisso (`BuildConfig.PEAK_BASE_URL`): il token non va mai altrove. */
    val http by lazy { PeakHttp(BuildConfig.PEAK_BASE_URL) }

    /** Il thread principale: l'unico su cui gira la sincronizzazione (vedi [SyncEngine]). */
    val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sync by lazy {
        val engine = SyncEngine(
            syncStorage, photos, FileSyncStateFile(File(filesDir, "peak-sync.json")), KeystoreCredentialStore(this),
            { token -> HttpPeakApi(http, token) }, backup,
        )
        val connect = ConnectFlow(
            engine, http, PrefsPendingLogin(this),
            { Settings.Global.getString(contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL },
            { PeakDatabase.isFallback },
        )
        SyncService(engine, connect, backup, database, mainScope)
    }
    val haptics by lazy { Haptics(this) }
    val soundsOn = MutableStateFlow(true)
    val sounds by lazy { SoundFX(this) { soundsOn.value } }

    override fun onCreate() {
        super.onCreate()
        // il catalogo vero è un asset copiato da common/catalog.json a ogni build (vedi app/build.gradle.kts)
        assets.open("catalog.json").use { Catalog.install(it.readBytes().toString(Charsets.UTF_8)) }
        // l'interruttore dei suoni vale anche dopo un riavvio
        mainScope.launch { settings.settings.collect { soundsOn.value = it.soundsEnabled } }
        sync.start()
    }
}
