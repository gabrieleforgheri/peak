package com.gabrieleforghieri.peak.data.dao

import androidx.room.Dao

/**
 * Le query dell'area Impostazioni (`Features/Settings/`): impostazioni, dati, archivio. Proprietario: l'area settings.
 *
 * Nasce vuoto. L'area ci aggiunge le query che le servono (`@Query`, `@Transaction`, `Flow`) senza toccare `Daos.kt`,
 * `PeakDatabase.kt` né lo schema: le tabelle e le entità sono quelle di `Entities.kt` e non cambiano nella fase 3.
 * Si prende da `PeakDatabase.areaSettings()`; una prova di query si scrive come `RoomDaoTest`. Attenzione: non è [SettingDao] (al singolare), che legge e scrive la tabella `setting` e resta in `Daos.kt`.
 */
@Dao
interface SettingsDao
