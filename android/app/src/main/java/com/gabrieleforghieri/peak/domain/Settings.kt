package com.gabrieleforghieri.peak.domain

/**
 * Le impostazioni e i loro valori di partenza. Le chiavi sincronizzate sono quelle di `SETTING_DEFAULTS` in common/docs.ts (e
 * `settingKeys` in SyncDocs.swift); le altre restano di questo dispositivo. In Room stanno in una tabella chiave → valore JSON.
 */
object SettingKeys {
    // sincronizzate con l'account
    const val UNIT = "unit"
    const val ACCENT = "accent"
    const val REST_TIMER_ENABLED = "restTimerEnabled"
    const val REST_SECONDS = "restSeconds"
    const val WARMUP_REST_PERCENT = "warmupRestPercent"
    const val PROGRESSION_ENABLED = "progressionEnabled"
    const val REP_RANGE_MIN = "repRangeMin"
    const val REP_RANGE_MAX = "repRangeMax"
    const val PLATE_CALCULATOR_ENABLED = "plateCalculatorEnabled"
    const val AVAILABLE_PLATES = "availablePlates"
    const val TUTORIAL_DONE = "tutorialDone"
    const val TOUR_SEEN = "tourSeen"
    const val MACHINE_TIPS_SEEN = "machineTipsSeen"
    const val WEEKLY_GOAL = "weeklyGoal"
    const val LANGUAGE = "language"
    const val ACTIVE_PROGRAM = "activeProgram"

    // solo di questo dispositivo
    const val HEALTH_SYNC_ENABLED = "device.healthSyncEnabled"
    const val SOUNDS_ENABLED = "device.soundsEnabled"
    const val DID_SEE_WELCOME = "device.didSeeWelcome"
    const val DID_LINK_CATALOG = "device.didLinkCatalog"
    const val DID_FREEZE_GLYPHS = "device.didFreezeGlyphs"
    const val LAST_BACKUP_DATE = "device.lastBackupDate"

    /** Le chiavi che viaggiano con l'account. */
    val synced: List<String> = listOf(
        UNIT, ACCENT, REST_TIMER_ENABLED, REST_SECONDS, WARMUP_REST_PERCENT, PROGRESSION_ENABLED, REP_RANGE_MIN, REP_RANGE_MAX,
        PLATE_CALCULATOR_ENABLED, AVAILABLE_PLATES, TUTORIAL_DONE, TOUR_SEEN, MACHINE_TIPS_SEEN, WEEKLY_GOAL, LANGUAGE, ACTIVE_PROGRAM,
    )
}

/** Il valore delle impostazioni, già con i valori di partenza (`AppSettings.swift`). */
data class AppSettings(
    val unit: WeightUnit = WeightUnit.kg,
    val accent: AccentPalette = AccentPalette.lime,
    val restTimerEnabled: Boolean = true,
    /** Recupero predefinito in secondi; ogni macchinario può avere il suo. */
    val restSeconds: Int = 120,
    /** Recupero dopo una serie di riscaldamento, in percentuale di quello normale (0 = nessun timer). */
    val warmupRestPercent: Int = 50,
    val progressionEnabled: Boolean = true,
    val repRangeMin: Int = 8,
    val repRangeMax: Int = 12,
    val plateCalculatorEnabled: Boolean = true,
    /** Dischi che la tua palestra ha davvero, in kg. */
    val availablePlates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    val tutorialDone: Boolean = false,
    val tourSeen: Int = 1,
    val machineTipsSeen: Boolean = false,
    val weeklyGoal: Int = 0,
    /** "" = quella del telefono, "it" o "en". */
    val language: String = "",
    val activeProgram: String = "",
    val healthSyncEnabled: Boolean = false,
    val soundsEnabled: Boolean = true,
    val didSeeWelcome: Boolean = false,
) {
    /** Mai vuoto: se gli estremi si incrociano vale il minimo. */
    val repRange: IntRange get() = repRangeMin..maxOf(repRangeMin, repRangeMax)

    /**
     * Secondi di recupero dopo una serie di un macchinario con recupero proprio `exerciseRest` (0 = quello delle impostazioni);
     * 0 = nessun timer. Dopo il riscaldamento è la percentuale scelta (almeno 5 s). Stessa regola di `restAfter` in common/domain.ts.
     */
    fun restDuration(exerciseRest: Int, warmup: Boolean): Int {
        val base = if (exerciseRest > 0) exerciseRest else restSeconds
        if (!warmup) return base
        return if (warmupRestPercent > 0) maxOf(5, Swift.round(base.toDouble() * warmupRestPercent / 100).toInt()) else 0
    }

    /**
     * Cambiando unità, dischi e bilanciere ancora «di serie» dell'unità vecchia passano a quelli di serie della nuova
     * (`settingsForUnitChange` in common/domain.ts). Il bilanciere è in kg.
     */
    fun withUnit(new: WeightUnit, barWeight: Double? = null): Pair<AppSettings, Double?> {
        if (new == unit) return this to barWeight
        val old = Plates.defaults(unit)
        val sameAsDefault = availablePlates.size == old.size &&
            availablePlates.sorted().zip(old.sorted()).all { (a, b) -> Math.abs(a - b) < 0.001 }
        val plates = if (sameAsDefault) Plates.defaults(new) else availablePlates
        val bar = barWeight?.let { if (Math.abs(it - Plates.defaultBar(unit)) < 0.001) Plates.defaultBar(new) else it }
        return copy(unit = new, availablePlates = plates) to bar
    }
}

/**
 * Le impostazioni da e verso il testo JSON del database, con gli stessi limiti di `cleanSetting` in common/docs.ts e di
 * `applySetting` in SyncDocs.swift: un valore fuori scala o di un altro tipo si scarta e resta quello di prima.
 */
object SettingsCodec {
    private val json = kotlinx.serialization.json.Json

    private fun prim(text: String?) = text?.let { try { json.parseToJsonElement(it) as? kotlinx.serialization.json.JsonPrimitive } catch (_: Exception) { null } }
    private fun int(text: String?, lo: Int, hi: Int) = prim(text)?.takeIf { !it.isString }?.content?.let { it.toIntOrNull() }?.takeIf { it in lo..hi }
    private fun bool(text: String?) = prim(text)?.takeIf { !it.isString }?.content?.let { if (it == "true") true else if (it == "false") false else null }
    private fun str(text: String?) = prim(text)?.takeIf { it.isString }?.content

    /** Le impostazioni dai valori salvati (chiave → JSON). Le chiavi che mancano o non valgono prendono il valore di partenza. */
    fun decode(values: Map<String, String>): AppSettings {
        val d = AppSettings()
        val plates = values[SettingKeys.AVAILABLE_PLATES]?.let {
            try {
                (json.parseToJsonElement(it) as? kotlinx.serialization.json.JsonArray)
                    ?.map { e -> (e as kotlinx.serialization.json.JsonPrimitive).content.toDouble() }
                    ?.takeIf { l -> l.size <= 20 && l.all { p -> p in 0.01..100.0 } }
            } catch (_: Exception) { null }
        }
        return d.copy(
            unit = WeightUnit.of(str(values[SettingKeys.UNIT])) ?: d.unit,
            accent = AccentPalette.of(str(values[SettingKeys.ACCENT])) ?: d.accent,
            restTimerEnabled = bool(values[SettingKeys.REST_TIMER_ENABLED]) ?: d.restTimerEnabled,
            restSeconds = int(values[SettingKeys.REST_SECONDS], 15, 600) ?: d.restSeconds,
            warmupRestPercent = int(values[SettingKeys.WARMUP_REST_PERCENT], 0, 100) ?: d.warmupRestPercent,
            progressionEnabled = bool(values[SettingKeys.PROGRESSION_ENABLED]) ?: d.progressionEnabled,
            repRangeMin = int(values[SettingKeys.REP_RANGE_MIN], 1, 30) ?: d.repRangeMin,
            repRangeMax = int(values[SettingKeys.REP_RANGE_MAX], 1, 30) ?: d.repRangeMax,
            plateCalculatorEnabled = bool(values[SettingKeys.PLATE_CALCULATOR_ENABLED]) ?: d.plateCalculatorEnabled,
            availablePlates = plates ?: d.availablePlates,
            tutorialDone = bool(values[SettingKeys.TUTORIAL_DONE]) ?: d.tutorialDone,
            tourSeen = int(values[SettingKeys.TOUR_SEEN], 1, 10) ?: d.tourSeen,
            machineTipsSeen = bool(values[SettingKeys.MACHINE_TIPS_SEEN]) ?: d.machineTipsSeen,
            weeklyGoal = int(values[SettingKeys.WEEKLY_GOAL], 0, 7) ?: d.weeklyGoal,
            language = str(values[SettingKeys.LANGUAGE])?.takeIf { it == "" || it == "it" || it == "en" } ?: d.language,
            activeProgram = str(values[SettingKeys.ACTIVE_PROGRAM])?.let { if (it.isEmpty()) it else Uuids.normalize(it) } ?: d.activeProgram,
            healthSyncEnabled = bool(values[SettingKeys.HEALTH_SYNC_ENABLED]) ?: d.healthSyncEnabled,
            soundsEnabled = bool(values[SettingKeys.SOUNDS_ENABLED]) ?: d.soundsEnabled,
            didSeeWelcome = bool(values[SettingKeys.DID_SEE_WELCOME]) ?: d.didSeeWelcome,
        )
    }

    /** Il valore di una chiave, come testo JSON (la forma di `{value: …}` del documento `setting`). */
    fun encode(key: String, s: AppSettings): String? = when (key) {
        SettingKeys.UNIT -> "\"${s.unit.raw}\""
        SettingKeys.ACCENT -> "\"${s.accent.name}\""
        SettingKeys.REST_TIMER_ENABLED -> s.restTimerEnabled.toString()
        SettingKeys.REST_SECONDS -> s.restSeconds.toString()
        SettingKeys.WARMUP_REST_PERCENT -> s.warmupRestPercent.toString()
        SettingKeys.PROGRESSION_ENABLED -> s.progressionEnabled.toString()
        SettingKeys.REP_RANGE_MIN -> s.repRangeMin.toString()
        SettingKeys.REP_RANGE_MAX -> s.repRangeMax.toString()
        SettingKeys.PLATE_CALCULATOR_ENABLED -> s.plateCalculatorEnabled.toString()
        SettingKeys.AVAILABLE_PLATES -> s.availablePlates.joinToString(",", "[", "]")
        SettingKeys.TUTORIAL_DONE -> s.tutorialDone.toString()
        SettingKeys.TOUR_SEEN -> s.tourSeen.toString()
        SettingKeys.MACHINE_TIPS_SEEN -> s.machineTipsSeen.toString()
        SettingKeys.WEEKLY_GOAL -> s.weeklyGoal.toString()
        SettingKeys.LANGUAGE -> "\"${s.language}\""
        SettingKeys.ACTIVE_PROGRAM -> "\"${s.activeProgram}\""
        SettingKeys.HEALTH_SYNC_ENABLED -> s.healthSyncEnabled.toString()
        SettingKeys.SOUNDS_ENABLED -> s.soundsEnabled.toString()
        SettingKeys.DID_SEE_WELCOME -> s.didSeeWelcome.toString()
        else -> null
    }
}
