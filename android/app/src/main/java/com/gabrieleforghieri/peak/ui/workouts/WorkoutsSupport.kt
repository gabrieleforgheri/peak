package com.gabrieleforghieri.peak.ui.workouts

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.util.SoundFX
import com.gabrieleforghieri.peak.util.tr
import com.gabrieleforghieri.peak.util.tr as contextTr
import androidx.compose.ui.platform.LocalContext
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Il recupero in corso, unico per tutta l'app (`Store/RestTimer.swift`). Solo dentro l'app: la notifica a schermo spento è della fase 4.
 * `end` è l'istante di fine (ms dall'epoca), `null` se non c'è un recupero.
 */
object RestTimer {
    private val _end = MutableStateFlow<Long?>(null)
    val end: StateFlow<Long?> get() = _end

    fun start(seconds: Int, now: Long = System.currentTimeMillis()) { _end.value = now + seconds * 1000L }

    /** Sposta la fine di `seconds`; se finisce nel passato, il recupero si ferma. */
    fun shift(seconds: Int, now: Long = System.currentTimeMillis()) {
        val current = _end.value ?: return
        val shifted = current + seconds * 1000L
        _end.value = if (shifted <= now) null else shifted
    }

    fun stop() { _end.value = null }
}

/**
 * Quanto è già stato festeggiato, in `SharedPreferences` (di questo telefono): il riepilogo lo scrive quando fa la festa e l'eroe di
 * Allenamento festeggia solo quello che è più alto, ad esempio un allenamento registrato dal web e arrivato con la sincronizzazione
 * (`Celebrated` in `Design/Celebration.swift`).
 */
class Celebrated(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("peak-celebrated", Context.MODE_PRIVATE)

    /** Le settimane di fila già festeggiate; `null` = mai salvato (prima volta in assoluto: si salva senza festa). */
    var streak: Int?
        get() = if (prefs.contains(STREAK)) prefs.getInt(STREAK, 0) else null
        set(value) { prefs.edit().also { if (value == null) it.remove(STREAK) else it.putInt(STREAK, value) }.apply() }

    /** Gli allenamenti già festeggiati nella settimana che inizia a `start`: 0 se l'ultimo valore è di una settimana passata, `null` se non c'è mai stato niente. */
    fun week(start: Long): Int? {
        val raw = prefs.getString(WEEK, null) ?: return null
        val parts = raw.split(':')
        if (parts.size != 2) return null
        val stamp = parts[0].toLongOrNull() ?: return null
        val count = parts[1].toIntOrNull() ?: return null
        return if (stamp == start) count else 0
    }

    fun store(week: Int, start: Long) { prefs.edit().putString(WEEK, "$start:$week").apply() }

    private companion object {
        const val STREAK = "celebrated.streak"
        const val WEEK = "celebrated.week"
    }
}

/** Le date nel formato di `Fmt` (`Formatters.swift`), nella lingua del telefono. */
object WorkoutDates {
    fun dateTime(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("d MMM, HH:mm", locale).format(Instant.ofEpochMilli(ms).atZone(zone))

    fun shortDate(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("d MMM", locale).format(Instant.ofEpochMilli(ms).atZone(zone))

    /** «Oggi», «Ieri», altrimenti «lun 3 nov». `today`/`yesterday` sono le parole già tradotte. */
    fun relativeDay(ms: Long, today: String, yesterday: String, now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val current = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (day) {
            current -> today
            current.minusDays(1) -> yesterday
            else -> DateTimeFormatter.ofPattern("EEE d MMM", locale).format(day)
        }
    }

    /** L'inizio del giorno (ms) di un istante: per raggruppare le serie per giorno di calendario. */
    fun startOfDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** 0 = lunedì (`weekday` del riepilogo). */
    fun weekday(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().dayOfWeek.value - 1
}

@Composable
fun relativeDay(ms: Long): String = WorkoutDates.relativeDay(ms, tr("Oggi"), tr("Ieri"))

/** Il titolo di un allenamento: il nome, altrimenti il giorno. */
@Composable
fun workoutTitle(name: String, date: Long): String = name.ifEmpty { relativeDay(date) }

/**
 * Suoni e vibrazioni dell'area, in un oggetto solo: nelle schermate arriva da [rememberFeedback], nelle prove è [None] (niente audio,
 * niente vibratore). `SoundFX` e `Haptics` sono dell'app.
 */
class WorkoutFeedback(
    val sound: (SoundFX.Effect) -> Unit = {},
    val day: (Int) -> Unit = {},
    val tap: () -> Unit = {},
    val commit: () -> Unit = {},
    val success: () -> Unit = {},
    val warning: () -> Unit = {},
    val personalRecord: () -> Unit = {},
) {
    companion object { val None = WorkoutFeedback() }
}

@Composable
fun rememberFeedback(): WorkoutFeedback {
    if (LocalInspectionMode.current) return WorkoutFeedback.None
    val app = LocalApp.current
    return remember(app) {
        WorkoutFeedback(
            sound = { app.sounds.play(it) }, day = { app.sounds.playDay(it) }, tap = { app.haptics.tap() }, commit = { app.haptics.commit() },
            success = { app.haptics.success() }, warning = { app.haptics.warning() }, personalRecord = { app.haptics.personalRecord() },
        )
    }
}

/** `tr` per le funzioni che non sono composable (`Fmt.setLine` vuole le parole del lato già tradotte). */
@Composable
fun rememberTr(): (String) -> String {
    val context = LocalContext.current
    return remember(context) { { text: String -> context.contextTr(text) } }
}

/** «80 kg × 5», con «· 1 br» a un lato (`Fmt.setLine`), nella lingua del telefono. */
@Composable
fun setLineText(weight: Double, reps: Int, unit: WeightUnit, singleArm: Boolean = false, group: MuscleGroup? = null): String =
    Fmt.setLine(weight, reps, unit, singleArm, group, side = rememberTr())
