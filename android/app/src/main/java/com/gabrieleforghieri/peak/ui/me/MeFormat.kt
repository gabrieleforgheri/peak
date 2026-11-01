package com.gabrieleforghieri.peak.ui.me

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MedalID
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.util.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// I testi di date e valori di Me: `Fmt.date`, `shortDate`, `relativeDay` di Formatters.swift (le date seguono la lingua) e `medalValue`.

private fun format(ms: Long, skeleton: String, locale: Locale, zone: ZoneId): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Instant.ofEpochMilli(ms).atZone(zone))

/** «1 nov 2026». */
fun dateText(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()) = format(ms, "dMMMy", locale, zone)

/** «1 nov». */
fun shortDateText(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()) = format(ms, "dMMM", locale, zone)

fun shortDateText(date: LocalDate, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()) =
    shortDateText(date.atStartOfDay(zone).toInstant().toEpochMilli(), locale, zone)

/** «dom 1 nov». */
fun weekdayDateText(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()) = format(ms, "EEEdMMM", locale, zone)

/** «Oggi», «Ieri», altrimenti il giorno breve. Le parole sono italiane: a chi mostra, `tr`. */
fun relativeDayKey(ms: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String? {
    val today = day(now, zone)
    return when (day(ms, zone)) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> null
    }
}

@Composable
fun relativeDay(ms: Long): String = relativeDayKey(ms)?.let { tr(it) } ?: weekdayDateText(ms)

/** Il valore di una medaglia come si legge: volume in tonnellate, forza nell'unità scelta, il resto intero. */
fun medalValue(kind: MedalID, v: Double, unit: WeightUnit): String = when (kind) {
    MedalID.volume -> Fmt.volume(v, unit)
    MedalID.strength -> Fmt.weight(v, unit)
    else -> v.roundToInt().toString()
}

/** «da conquistare», «Bronzo», «Argento», «Oro». */
@Composable
fun tierName(tier: Int): String = tr(TIER_NAMES[tier])

private val TIER_NAMES = listOf("da conquistare", "Bronzo", "Argento", "Oro")

/** Il metallo di un livello (0 = ancora niente). */
fun metalTint(tier: Int): Color = listOf(PeakTheme.textTertiary, PeakTheme.bronze, PeakTheme.silver, PeakTheme.gold)[tier]
