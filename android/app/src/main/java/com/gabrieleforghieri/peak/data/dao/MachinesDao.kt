package com.gabrieleforghieri.peak.data.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Le query dell'area Macchinari (`Features/Maxes/`): macchinari, massimali, catalogo. Proprietario: l'area machines.
 *
 * L'area ci aggiunge le query che le servono (`@Query`, `@Transaction`, `Flow`) senza toccare `Daos.kt`, `PeakDatabase.kt` né lo
 * schema: le tabelle e le entità sono quelle di `Entities.kt` e non cambiano nella fase 3. Si prende da `PeakDatabase.areaMachines()`;
 * una prova di query si scrive come `RoomDaoTest` (`MachinesDaoTest`).
 */
@Dao
interface MachinesDao {
    /** Archivia un macchinario: sparisce dalle liste, lo storico resta (`exercise.isArchived = true`). */
    @Query("UPDATE exercise SET isArchived = 1 WHERE uuid = :uuid")
    suspend fun archive(uuid: String)

    /** L'indice con cui mettere in fondo un macchinario nuovo (`allExercises.map(\.sortIndex).max() + 1`). */
    @Query("SELECT COALESCE(MAX(sortIndex), 0) + 1 FROM exercise")
    suspend fun nextSortIndex(): Int
}
