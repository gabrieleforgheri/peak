package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.start.TourState
import com.gabrieleforghieri.peak.ui.start.TourSheet
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.tr

/**
 * «Cosa alleni oggi?» (`FocusPickerSheet.swift`): le schede (tocco = si parte con la loro lista), oppure nome della sessione e gruppi.
 * I gruppi filtrano il selettore degli esercizi; nessun gruppo scelto = nessun filtro. `onStartTemplate` c'è solo all'avvio; in modifica
 * di una sessione è `null`. `confirmTitle` è una chiave di traduzione. Quello che si sceglie parte a foglio chiuso.
 */
@Composable
fun FocusPickerSheet(
    confirmTitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    initial: Set<MuscleGroup> = emptySet(),
    onStartTemplate: ((WorkoutTemplateEntity) -> Unit)? = null,
    onConfirm: (String, Set<MuscleGroup>) -> Unit,
) {
    val repository = LocalRepository.current
    val templates by repository.templates.collectAsState(emptyList())
    val programs by repository.programs.collectAsState(emptyList())
    val active = activeProgram(programs, LocalAppSettings.current.activeProgram)
    val days = daysOf(active, templates)
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }

    // il tutorial: «Scegli un giorno» sta qui dentro (senza giorni si scelgono i gruppi e si preme «Inizia»)
    TourSheet(TourState.Sheet.days, whenActive = onStartTemplate != null)
    LaunchedEffect(days.isNotEmpty(), onStartTemplate != null) { if (onStartTemplate != null) TourState.hasDays = days.isNotEmpty() }

    PeakSheet(onDismiss = { onDismiss(); after?.invoke() }, modifier = modifier) { close ->
        FocusPickerContent(
            days = if (onStartTemplate != null) days else emptyList(), confirmTitle = confirmTitle, initialName = initialName, initial = initial,
            onCancel = close,
            onStartTemplate = { template -> after = { onStartTemplate?.invoke(template) }; close() },
            onConfirm = { name, focus -> after = { onConfirm(name, focus) }; close() },
        )
    }
}

/** Il contenuto del foglio, senza il foglio (si prova negli screenshot). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusPickerContent(
    days: List<WorkoutTemplateEntity>,
    confirmTitle: String,
    initialName: String,
    initial: Set<MuscleGroup>,
    onCancel: () -> Unit,
    onStartTemplate: (WorkoutTemplateEntity) -> Unit,
    onConfirm: (String, Set<MuscleGroup>) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var selection by remember { mutableStateOf(initial) }
    val view = LocalView.current
    Column(Modifier.imePadding()) {
        SheetBar("Cosa alleni oggi?", onCancel)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (days.isNotEmpty()) {
                GlassCard(Modifier.tourTarget("days")) {
                    SectionHeader("I tuoi giorni")
                    for (template in days) {
                        Row(
                            Modifier.fillMaxWidth().pressable { onStartTemplate(template) }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { TemplateIconView(template.icon, template.name, size = 18) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(template.name, style = PeakTheme.rounded(16, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                                Text(exerciseCountLabel(template.itemList.size), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                            }
                            Icon(PeakIcons.chevronRight, null, Modifier.size(14.dp), tint = PeakTheme.textTertiary)
                        }
                    }
                }
                Text(tr("Oppure scegli i gruppi"), Modifier.fillMaxWidth().padding(horizontal = 4.dp), style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textTertiary)
            }

            GlassCard {
                SectionHeader("Nome")
                Box(Modifier.padding(top = 12.dp)) { PeakTextField(name, { name = it }, "Es. Push 1, Pull 2, Gambe") }
            }

            GlassCard {
                SectionHeader("Gruppi muscolari")
                FlowRow(
                    Modifier.padding(top = 12.dp), maxItemsInEachRow = 3,
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (group in MuscleGroup.entries) {
                        val on = group in selection
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.weight(1f)
                                .background(if (on) group.tint.copy(alpha = 0.22f) else PeakTheme.card, shape)
                                .border(1.dp, if (on) group.tint.copy(alpha = 0.6f) else PeakTheme.stroke, shape)
                                .pressable {
                                    selection = if (on) selection - group else selection + group
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PeakIcons.group(group), null, Modifier.size(12.dp), tint = if (on) group.tint else PeakTheme.textSecondary)
                            Text(tr(group.title), style = PeakTheme.rounded(13, FontWeight.SemiBold), color = if (on) group.tint else PeakTheme.textSecondary, maxLines = 1)
                        }
                    }
                }
                Text(
                    tr(if (selection.isEmpty()) "Nessun gruppo scelto: vedrai tutti i macchinari." else "Quando aggiungi un esercizio vedrai solo questi gruppi."),
                    Modifier.padding(top = 12.dp), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                )
            }

            PrimaryLabelButton(WorkoutsIcons.bolt, tr(confirmTitle), { onConfirm(name.trim(), selection) }, Modifier.tourTarget("go"), tint = LocalAccent.current)
        }
    }
}
