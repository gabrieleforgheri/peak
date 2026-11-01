package com.gabrieleforghieri.peak.ui.start

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.CatalogEntry
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.Onboarding

/** Un giorno della scheda pronta, per la schermata finale. */
data class MadeDay(val group: MuscleGroup, val name: String, val count: Int)

/**
 * Lo stato del primo avvio (le `@State` di `OnboardingView.swift`): quale schermo, cosa si è scelto. Sta fuori dalla schermata
 * perché le regole (quali macchinari partono scelti, dove finisce un macchinario cercato) si provano sul JVM e perché uno screenshot
 * può partire da un passo qualunque.
 */
class OnboardingState(step: Int = 0) {
    val last = 4

    var step by mutableIntStateOf(step)

    /** 1 = avanti, −1 = indietro: da che parte scorre lo schermo. */
    var dir by mutableIntStateOf(1)

    /** La montagna si disegna una volta sola; tornando al benvenuto è già finita. */
    var heroSeen by mutableStateOf(false)

    /** 0…3: il titolo, la riga e il pulsante entrano dopo la salita (2,5 s), uno dopo l'altro. */
    var late by mutableIntStateOf(0)
    var busy by mutableStateOf(false)

    var goal by mutableIntStateOf(0)
    var groups by mutableStateOf<Set<MuscleGroup>>(emptySet())

    /** I macchinari scelti fra i suggeriti (id del catalogo) e quelli aggiunti dalla ricerca. */
    var on by mutableStateOf<Set<String>>(emptySet())
    var extras by mutableStateOf<List<CatalogEntry>>(emptyList())
    var seeded by mutableStateOf<Set<MuscleGroup>>(emptySet())
    var searching by mutableStateOf(false)

    /** Cosa ha creato l'ultimo passo (per toglierlo se si torna indietro) e i giorni da mostrare. */
    var plan by mutableStateOf<Onboarding.Plan?>(null)
    var made by mutableStateOf<List<MadeDay>>(emptyList())
    var composed by mutableStateOf(false)
    var connecting by mutableStateOf(false)

    val picked: List<MuscleGroup> get() = MuscleGroup.entries.filter(groups::contains)

    fun chosen(g: MuscleGroup): List<CatalogEntry> = Onboarding.suggested[g].orEmpty().filter(on::contains).mapNotNull(Catalog::entry)
    fun extrasOf(g: MuscleGroup): List<CatalogEntry> = extras.filter { it.muscleGroup == g }
    val total: Int get() = picked.sumOf { chosen(it).size + extrasOf(it).size }

    fun toggleGroup(g: MuscleGroup) { groups = if (g in groups) groups - g else groups + g }
    fun toggleMachine(id: String) { on = if (id in on) on - id else on + id }
    fun removeExtra(id: String) { extras = extras.filter { it.id != id } }

    /** Ogni gruppo, la prima volta, parte con i suoi primi tre già scelti (`toMachines`). */
    fun seedMachines() {
        for (g in picked) {
            if (g in seeded) continue
            seeded = seeded + g
            on = on + Onboarding.suggested[g].orEmpty().take(Onboarding.PRESELECTED)
        }
    }

    /** Un macchinario preso dalla ricerca finisce nel suo gruppo; se il gruppo non era scelto, si aggiunge (e parte vuoto). */
    fun add(entry: CatalogEntry) {
        val g = entry.muscleGroup
        if (g !in groups) { groups = groups + g; seeded = seeded + g }
        if (entry.id in Onboarding.suggested[g].orEmpty()) on = on + entry.id
        else if (extras.none { it.id == entry.id }) extras = extras + entry
    }

    /** Un giorno per gruppo scelto, con i macchinari scelti; i gruppi senza macchinari non fanno un giorno (`toReady`). */
    fun days(translate: (String) -> String): List<Onboarding.Day> =
        picked.map { g -> Onboarding.Day(g, translate(g.title), chosen(g) + extrasOf(g)) }.filter { it.machines.isNotEmpty() }
}
