package com.gabrieleforghieri.peak.data

import com.gabrieleforghieri.peak.domain.AppSettings
import com.gabrieleforghieri.peak.domain.SettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Le impostazioni, osservabili. Una modifica parte da qui e riscrive solo la chiave cambiata. */
class SettingsRepository(private val dao: SettingDao) {
    val settings: Flow<AppSettings> = dao.observeAll().map { rows -> SettingsCodec.decode(rows.associate { it.key to it.value }) }

    /** Salva le chiavi che differiscono fra `old` e `new`. */
    suspend fun update(old: AppSettings, new: AppSettings, keys: Collection<String>) {
        for (key in keys) {
            val before = SettingsCodec.encode(key, old)
            val after = SettingsCodec.encode(key, new) ?: continue
            if (before != after) dao.put(SettingEntity(key, after))
        }
    }

    suspend fun put(key: String, jsonValue: String) = dao.put(SettingEntity(key, jsonValue))
}
