package com.gabrieleforghieri.peak.data.dao

import androidx.room.Dao

/**
 * Le query dell'area Allenamenti (`Features/Workouts/`): allenamenti, giorni, schede, serie. Proprietario: l'area workouts.
 *
 * Nasce vuoto. L'area ci aggiunge le query che le servono (`@Query`, `@Transaction`, `Flow`) senza toccare `Daos.kt`,
 * `PeakDatabase.kt` né lo schema: le tabelle e le entità sono quelle di `Entities.kt` e non cambiano nella fase 3.
 * Si prende da `PeakDatabase.areaWorkouts()`; una prova di query si scrive come `RoomDaoTest`.
 */
@Dao
interface WorkoutsDao
