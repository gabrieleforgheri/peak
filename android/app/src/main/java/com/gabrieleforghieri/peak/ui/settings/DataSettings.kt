package com.gabrieleforghieri.peak.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.glassSurface
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Impostazioni → Dati: macchinari archiviati e cancellazione di tutto (`DataSettingsView`). */
@Composable
fun DataSettingsPage(onOpenArchived: () -> Unit) {
    val app = LocalApp.current
    val exercises by LocalRepository.current.exercises.collectAsState(initial = emptyList())
    val status by app.sync.engine.status.collectAsState()
    val connected = status.state == com.gabrieleforghieri.peak.sync.SyncEngine.State.Connected || status.state == com.gabrieleforghieri.peak.sync.SyncEngine.State.Expired
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun eraseAll() = scope.launch {
        try {
            // sull'allenamento aperto la cancellazione cadrebbe sotto gli occhi della sessione
            if (app.sync.engine.hasActiveWorkout()) {
                app.haptics.warning()
                error = app.tr("Termina l'allenamento in corso prima di cancellare i dati.")
                return@launch
            }
            // collegato, la cancellazione deve arrivare al server: la guardia contro le cancellazioni di massa non la ferma
            app.sync.engine.permitMassDeletion()
            app.database.areaSettings().eraseAll()
            app.haptics.warning()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "" }
    }

    DataSettingsContent(exercises.size, archived(exercises).size, onOpenArchived, onErase = { confirming = true })

    if (confirming) {
        ChoiceSheet(
            "Cancellare tutti i dati?",
            if (connected) "Macchinari, massimali, allenamenti e pesate verranno eliminati da questo telefono, da Peak sul server e da ogni dispositivo collegato. Esporta un backup prima di procedere."
            else "Macchinari, massimali, allenamenti e pesate verranno eliminati. Esporta un backup prima di procedere.",
            listOf(SheetAction("Cancella tutto", destructive = true) { eraseAll() }),
            onDismiss = { confirming = false },
        )
    }
    error?.let { AlertSheet("Errore", it, onDismiss = { error = null }) }
}

/** La scheda dei dati: quanti macchinari, l'archivio e il tasto che cancella. */
@Composable
fun DataSettingsContent(total: Int, archivedCount: Int, onOpenArchived: () -> Unit, onErase: () -> Unit) {
    SettingsPage {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Dati", trailing = tr("%lld macchinari", total))

                Row(Modifier.fillMaxWidth().pressable(onClick = onOpenArchived).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SettingsIcons.archive, null, Modifier.size(18.dp), tint = PeakTheme.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Macchinari archiviati"), Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                    Text("$archivedCount", style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary)
                    Spacer(Modifier.width(6.dp))
                    Icon(PeakIcons.chevronRight, null, Modifier.size(16.dp), tint = PeakTheme.textTertiary)
                }

                SettingsDivider()

                SettingsTextAction(tr("Cancella tutti i dati"), SettingsIcons.trash, PeakTheme.negative, onErase)
            }
        }
    }
}

/** `ArchivedExercisesView`: l'elenco degli archiviati con «Ripristina», o lo stato vuoto. */
@Composable
fun ArchivedExercisesPage() {
    val app = LocalApp.current
    val exercises by LocalRepository.current.exercises.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    ArchivedExercisesContent(archived(exercises)) { exercise ->
        scope.launch { app.database.areaSettings().unarchive(exercise.uuid); app.haptics.commit() }
    }
}

@Composable
fun ArchivedExercisesContent(archived: List<ExerciseEntity>, onRestore: (ExerciseEntity) -> Unit) {
    if (archived.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(SettingsIcons.archive, null, Modifier.size(44.dp), tint = PeakTheme.textTertiary)
            Spacer(Modifier.size(12.dp))
            Text(tr("Nessun macchinario archiviato"), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.size(6.dp))
            Text(
                tr("Gli archiviati spariscono dalle liste ma conservano tutto lo storico."),
                style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center,
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (exercise in archived) {
            val group = MuscleGroup.of(exercise.muscleGroup) ?: MuscleGroup.petto
            Row(
                Modifier.fillMaxWidth().glassSurface(PeakTheme.radiusSmall).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).background(group.tint.copy(alpha = 0.14f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Icon(PeakIcons.group(group), null, Modifier.size(16.dp), tint = group.tint)
                }
                Text(exercise.displayName(), Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary, maxLines = 2)
                Text(
                    tr("Ripristina"), Modifier.pressable { onRestore(exercise) }.padding(vertical = 6.dp),
                    style = PeakTheme.rounded(13, FontWeight.SemiBold), color = LocalAccent.current,
                )
            }
        }
    }
}
