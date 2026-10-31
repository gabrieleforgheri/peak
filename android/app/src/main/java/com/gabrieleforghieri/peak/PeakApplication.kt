package com.gabrieleforghieri.peak

import android.app.Application
import com.gabrieleforghieri.peak.data.PeakDatabase
import com.gabrieleforghieri.peak.data.RoomRecordStorage
import com.gabrieleforghieri.peak.data.SettingsRepository
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.RecordService
import com.gabrieleforghieri.peak.util.Haptics
import com.gabrieleforghieri.peak.util.SoundFX
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * L'app: tiene in un punto solo il database, il catalogo, le impostazioni e il servizio dei record (l'unico percorso di scrittura delle serie).
 * Niente contenitore di dipendenze: sono pochi oggetti e vivono quanto il processo.
 */
class PeakApplication : Application() {
    val database by lazy { PeakDatabase.get(this) }
    val settings by lazy { SettingsRepository(database.settings()) }
    val records by lazy { RecordService(RoomRecordStorage(database)) }
    val haptics by lazy { Haptics(this) }
    val soundsOn = MutableStateFlow(true)
    val sounds by lazy { SoundFX(this) { soundsOn.value } }

    override fun onCreate() {
        super.onCreate()
        // il catalogo vero è un asset copiato da common/catalog.json a ogni build (vedi app/build.gradle.kts)
        assets.open("catalog.json").use { Catalog.install(it.readBytes().toString(Charsets.UTF_8)) }
    }
}
