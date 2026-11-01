package com.gabrieleforghieri.peak.ui.settings

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.sync.SyncEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// La logica delle Impostazioni che non dipende da Android: si prova sul JVM (`SettingsLogicTest`).

/** Le sottopagine, nell'ordine di `SettingsCategory` (Watch non c'è). `name` è quello che porta la rotta `Routes.Settings(category)`. */
enum class SettingsCategory(val title: String) {
    general("Generale"),
    training("Allenamento"),
    account("Account yG"),
    health("App Salute"),
    backup("Backup"),
    `data`("Dati");

    companion object {
        fun of(name: String?): SettingsCategory? = entries.firstOrNull { it.name == name }
    }
}

/** Un disco acceso si spegne, uno spento si accende (`AppSettings.availablePlates` confronta con 0,001 kg di tolleranza). */
fun togglePlate(plates: List<Double>, plate: Double): List<Double> =
    if (plates.any { Math.abs(it - plate) < 0.001 }) plates.filter { Math.abs(it - plate) >= 0.001 } else plates + plate

fun hasPlate(plates: List<Double>, plate: Double): Boolean = plates.any { Math.abs(it - plate) < 0.001 }

/** `SettingsView`: il testo di stato in testa alla scheda «Account yG» (la chiave italiana). */
fun accountStatusTitle(state: SyncEngine.State): String = when (state) {
    SyncEngine.State.Connected -> "collegato"
    SyncEngine.State.Expired -> "scaduto"
    else -> "non collegato"
}

/** Il nome mostrato per l'account collegato: quello di yG, o «il tuo account yG» se vuoto. */
fun accountDisplayName(name: String?, fallback: String): String = name?.takeIf { it.isNotEmpty() } ?: fallback

/** «Peak-2026-11-01.json»: il nome del file che si condivide (`BackupService.makeShareFile`). */
fun backupFileName(date: java.time.LocalDate = java.time.LocalDate.now()): String = "Peak-$date.json"

/** Giorno e ora brevi, come `Fmt.dateTime` (`13 ott, 14:05`): il formato dipende dalla lingua. */
fun shortDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat(patternFor(locale, "dMMMHm"), locale).format(Date(millis))

/** Giorno, mese abbreviato e anno, come `Fmt.date` (`13 ott 2026`). */
fun shortDate(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat(patternFor(locale, "dMMMy"), locale).format(Date(millis))

private fun patternFor(locale: Locale, skeleton: String): String =
    android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)

/** I macchinari archiviati, nell'ordine della lista (`ArchivedExercisesView.archived`). */
fun archived(exercises: List<ExerciseEntity>): List<ExerciseEntity> = exercises.filter { it.isArchived }

/**
 * La lingua scelta nelle impostazioni diventa quella dell'app: `""` = quella del telefono. Da Android 13 la cambia il sistema subito,
 * prima non c'è modo (resta quella del telefono): `canApply` lo dice, e la pagina mostra un avviso.
 */
fun canApplyLanguage(sdk: Int): Boolean = sdk >= 33
