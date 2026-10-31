package com.gabrieleforghieri.peak.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.gabrieleforghieri.peak.R

/**
 * Effetti sonori (i file di `Peak/Sounds`, copiati in `res/raw/fx_*.m4a` da `gen-assets.ts`), tenuti in un punto solo come
 * [Haptics]. Si mischiano alla musica che stai ascoltando senza fermarla (`USAGE_ASSISTANCE_SONIFICATION`) e tacciono con il
 * telefono in silenzioso. Se manca un file o l'audio non parte, non succede nulla. L'interruttore è del telefono, acceso di base.
 */
class SoundFX(private val context: Context, private val enabled: () -> Boolean = { true }) {

    enum class Effect(val res: Int) {
        set(R.raw.fx_set), record(R.raw.fx_record), finish(R.raw.fx_finish), streak(R.raw.fx_streak),
        goal(R.raw.fx_goal), medal(R.raw.fx_medal), remove(R.raw.fx_remove),
    }

    private val days = intArrayOf(R.raw.fx_day1, R.raw.fx_day2, R.raw.fx_day3, R.raw.fx_day4, R.raw.fx_day5, R.raw.fx_day6, R.raw.fx_day7)

    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val loaded = HashMap<Int, Int>()
    private val ready = HashSet<Int>()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(ready) { ready += id } }
        for (res in Effect.entries.map { it.res } + days.toList()) loaded[res] = pool.load(context, res, 1)
    }

    private fun start(res: Int) {
        if (!enabled()) return
        val id = loaded[res] ?: return
        if (synchronized(ready) { id in ready }) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun play(effect: Effect) = start(effect.res)

    /** Il giorno `index` (0-6) che si riempie sulla settimana: sette note di una pentatonica, in fila fanno una melodia che sale. */
    fun playDay(index: Int) = start(days[index.coerceIn(0, 6)])

    fun release() = pool.release()
}
