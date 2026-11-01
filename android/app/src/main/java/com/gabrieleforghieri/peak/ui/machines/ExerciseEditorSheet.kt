package com.gabrieleforghieri.peak.ui.machines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.design.FramedPhoto
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MachineGlyphView
import com.gabrieleforghieri.peak.design.MuscleFigureView
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.SheetDetent
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.rememberExercisePhoto
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.PhotoFraming
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * **Firma condivisa** (iPhone: `Features/Maxes/ExerciseEditorView.swift`). Proprietario: l'area **machines**, che ne scrive il corpo
 * (creazione guidata in tre passi, modifica, foto, collegamento al catalogo); la firma non cambia.
 * Usata da: machines (lista, dettaglio), workouts (il tasto + del selettore di esercizi).
 *
 * Crea (`exerciseId == null`) o modifica un macchinario. Salva da sé nel database; `onDismiss` parte a foglio chiuso.
 */
@Composable
fun ExerciseEditorSheet(exerciseId: String?, onDismiss: () -> Unit) {
    val repository = LocalRepository.current
    // il macchinario da modificare si legge una volta all'apertura: le scelte dell'editor non si sovrascrivono a metà
    val loaded by produceState<Loaded>(Loaded.Loading, exerciseId) {
        value = if (exerciseId == null) Loaded.New else repository.exercise(exerciseId).first()?.let { Loaded.Existing(it) } ?: Loaded.New
    }
    PeakSheet(onDismiss) { close ->
        when (val l = loaded) {
            Loaded.Loading -> Unit
            Loaded.New -> ExerciseEditorContent(null, onClose = close)
            is Loaded.Existing -> ExerciseEditorContent(l.exercise, onClose = close)
        }
    }
}

private sealed interface Loaded {
    data object Loading : Loaded
    data object New : Loaded
    data class Existing(val exercise: ExerciseEntity) : Loaded
}

/**
 * Il corpo dell'editor. `exercise == null`: creazione passo per passo (1 e 2 il catalogo, 3 l'essenziale; «Altro» chiuso); altrimenti la
 * modifica, tutta su una pagina. Il salvataggio è lo stesso (`EditorForm.applyTo`).
 */
@Composable
fun ExerciseEditorContent(exercise: ExerciseEntity?, onClose: () -> Unit, modifier: Modifier = Modifier, startOnEssentials: Boolean = false, startMoreOpen: Boolean = false, initialForm: EditorForm? = null) {
    val app = LocalApp.current
    val settings = LocalAppSettings.current
    val repository = LocalRepository.current
    val italian = Lang.isItalian()
    val isNew = exercise == null
    val all by repository.exercises.collectAsState(emptyList())
    var form by remember { mutableStateOf(initialForm ?: exercise?.let { EditorForm.of(it, italian) } ?: EditorForm()) }
    var essentials by remember { mutableStateOf(startOnEssentials) }
    var pickedGroup by remember { mutableStateOf<MuscleGroup?>(null) }
    var showMore by remember { mutableStateOf(startMoreOpen) }
    var linking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tipDone by remember { mutableStateOf(false) }

    fun save() {
        val snapshot = form
        app.mainScope.launch {
            val sha = withContext(Dispatchers.IO) { snapshot.newPhoto?.let { app.photos.write(it) } } ?: snapshot.photoSha
            val index = if (isNew) app.database.areaMachines().nextSortIndex() else 0
            app.database.exercises().upsert(snapshot.applyTo(exercise, sha, index, System.currentTimeMillis(), italian))
            // ha creato un macchinario: i suggerimenti hanno fatto il loro
            if (isNew) app.settings.edit(settings) { copy(machineTipsSeen = true) }
            app.haptics.commit()
        }
        onClose()
    }

    fun delete() {
        val uuid = exercise?.uuid ?: return
        app.haptics.warning()
        onClose()
        // la cancellazione vera avviene dopo che il foglio è sparito: la schermata di dettaglio, che osserva il macchinario, torna indietro
        app.mainScope.launch { delay(350); app.database.exercises().delete(uuid) }
    }

    Box(modifier.fillMaxWidth()) {
        when {
            isNew && !essentials -> CatalogPickerBody(
                title = "Nuovo macchinario", allowsCustom = true, wizard = true, onCancel = onClose, initialGroup = pickedGroup,
                onPick = { entry, group ->
                    pickedGroup = group
                    form = form.chosen(entry, group, isNew = true, unit = settings.unit, italian = italian)
                    essentials = true
                },
            )
            isNew -> {
                BackHandler { essentials = false }
                EssentialsPage(form, { form = it }, settings.unit, showMore, { showMore = it }, onBack = { essentials = false }, onCancel = onClose, onAdd = ::save, tipsOn = !settings.machineTipsSeen && all.isEmpty() && !tipDone, onTipDone = { tipDone = true })
            }
            else -> EditPage(exercise, form, { form = it }, settings.unit, onCancel = onClose, onSave = ::save, onLink = { linking = true }, onDelete = { confirmDelete = true })
        }
        if (linking) CatalogPickerSheet(
            title = "Collega al catalogo", allowsCustom = false,
            onPick = { entry, group -> form = form.chosen(entry, group, isNew = false, unit = settings.unit, italian = italian) },
            onDismiss = { linking = false },
        )
        if (confirmDelete) DeleteSheet(onConfirm = ::delete, onDismiss = { confirmDelete = false })
    }
}

// ---- pagine --------------------------------------------------------------------------------

/** Il passo 3 della creazione: il nome, il passo del carico e, dietro «Altro», tutto il resto. */
@Composable
private fun EssentialsPage(
    form: EditorForm, onForm: (EditorForm) -> Unit, unit: WeightUnit, showMore: Boolean, onShowMore: (Boolean) -> Unit,
    onBack: () -> Unit, onCancel: () -> Unit, onAdd: () -> Unit, tipsOn: Boolean, onTipDone: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MachinesIcons.chevronLeft, tr("Indietro"), Modifier.size(44.dp).pressable(onClick = onBack).padding(10.dp), tint = accent)
                Text(tr("Nuovo macchinario"), Modifier.weight(1f), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
                Text(tr("Annulla"), Modifier.pressable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 10.dp), style = PeakTheme.rounded(16, FontWeight.Medium), color = accent)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("Passo 3 di 3"), Modifier.padding(horizontal = 4.dp), style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                NameCard(form, onForm)
                if (form.catalogID.isEmpty()) GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Muscoli")
                        MusclesContent(form, onForm)
                    }
                }
                Column(Modifier.tourTarget("tip-step"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StepCard(form, onForm)
                    Row(Modifier.fillMaxWidth().pressable { onShowMore(!showMore) }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(tr("Altro"), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                            Text(tr("Sbarra, dischi, foto, recupero, note"), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                        }
                        Icon(MachinesIcons.chevronDown, null, Modifier.size(22.dp).rotate(if (showMore) 180f else 0f), tint = accent)
                    }
                    if (showMore) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ImageCard(form, onForm, showMuscles = form.catalogID.isNotEmpty(), existing = null)
                        AdvancedCards(form, onForm, unit, existing = null)
                    }
                }
            }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val enabled = form.canSave
                Box(
                    Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).background(accent, RoundedCornerShape(16.dp)).then(if (enabled) Modifier.pressable(onClick = onAdd) else Modifier).padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("Aggiungi"), style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.bg) }
            }
        }
        MachineTip(
            "tip-step", tipsOn, "Incremento e Altro",
            "L'incremento è il salto dei pulsanti + e −. Sotto «Altro» ci sono sbarra, dischi, foto e note: puoi lasciarli a dopo.",
            onNext = onTipDone, last = true,
        )
    }
}

/** La modifica di un macchinario che c'è già, tutta su una pagina. */
@Composable
private fun EditPage(
    exercise: ExerciseEntity?, form: EditorForm, onForm: (EditorForm) -> Unit, unit: WeightUnit,
    onCancel: () -> Unit, onSave: () -> Unit, onLink: () -> Unit, onDelete: () -> Unit,
) {
    val accent = LocalAccent.current
    val canSave = form.canSave
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Annulla"), Modifier.pressable(onClick = onCancel).padding(vertical = 8.dp), style = PeakTheme.rounded(16, FontWeight.Medium), color = accent)
            Text(tr("Modifica"), Modifier.weight(1f), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
            Text(
                tr("Salva"), Modifier.then(if (canSave) Modifier.pressable(onClick = onSave) else Modifier).padding(vertical = 8.dp),
                style = PeakTheme.rounded(16, FontWeight.Bold), color = if (canSave) accent else PeakTheme.textTertiary,
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NameCard(form, onForm)
            CatalogCard(form, onForm, onLink)
            ImageCard(form, onForm, showMuscles = true, existing = exercise)
            GroupCard(form, onForm)
            StepCard(form, onForm)
            AdvancedCards(form, onForm, unit, existing = exercise)
            if (exercise != null) GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwitchRow("Archivia", form.isArchived, { onForm(form.copy(isArchived = it)) }, subtitle = "Lo nasconde dalle liste senza perdere lo storico.")
                    Box(Modifier.fillMaxWidth().height(1.dp).background(PeakTheme.stroke))
                    Row(Modifier.fillMaxWidth().pressable(onClick = onDelete).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(MachinesIcons.trash, null, Modifier.size(20.dp), tint = PeakTheme.negative)
                        Text(tr("Elimina macchinario"), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.negative)
                    }
                }
            }
        }
    }
}

/** «Eliminare il macchinario?»: come `ConfirmSheet`, con in più la spiegazione di cosa si perde. */
@Composable
private fun DeleteSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    PeakSheet(onDismiss = { onDismiss(); if (confirmed) onConfirm() }, detent = SheetDetent.Height(260.dp)) { close ->
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Eliminare il macchinario?"), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
            Text(tr("Verranno persi anche i massimali registrati. Le serie già svolte restano negli allenamenti."), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.gabrieleforghieri.peak.design.PrimaryButton(tr("Elimina tutto lo storico"), { confirmed = true; close() }, tint = PeakTheme.negative)
                com.gabrieleforghieri.peak.design.SecondaryButton(tr("Annulla"), close)
            }
        }
    }
}

// ---- le carte ------------------------------------------------------------------------------

@Composable
private fun NameCard(form: EditorForm, onForm: (EditorForm) -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Nome")
            Box {
                if (form.name.isEmpty()) Text(tr("Es. Chest Press"), style = PeakTheme.rounded(17, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                BasicTextField(
                    form.name, { onForm(form.copy(name = it)) }, Modifier.fillMaxWidth(), singleLine = true, cursorBrush = SolidColor(LocalAccent.current),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    textStyle = PeakTheme.rounded(17, FontWeight.SemiBold).copy(color = PeakTheme.textPrimary),
                )
            }
        }
    }
}

@Composable
private fun CatalogCard(form: EditorForm, onForm: (EditorForm) -> Unit, onLink: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Catalogo")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (form.catalogID.isEmpty()) {
                    Text(
                        tr(if (form.isCustom) "Macchinario tuo: resta fuori dalle classifiche." else "Non collegato: resta fuori dalle classifiche."),
                        Modifier.weight(1f), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary,
                    )
                } else {
                    Text(form.linkedEntry?.name(Lang.isItalian()) ?: form.catalogID, Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                }
                Text(tr(if (form.catalogID.isEmpty()) "Collega" else "Cambia"), Modifier.pressable(onClick = onLink).padding(vertical = 4.dp), style = PeakTheme.rounded(14, FontWeight.SemiBold), color = accent)
            }
            if (form.catalogID.isEmpty()) SwitchRow("È un macchinario mio", form.isCustom, { onForm(form.copy(isCustom = it)) })
        }
    }
}

@Composable
private fun GroupCard(form: EditorForm, onForm: (EditorForm) -> Unit) {
    val haptics = LocalApp.current.haptics
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Gruppo muscolare")
            for (pair in MuscleGroup.entries.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (g in pair) {
                        val on = form.group == g
                        Row(
                            Modifier.weight(1f).background(if (on) g.tint.copy(alpha = 0.22f) else PeakTheme.card, RoundedCornerShape(12.dp))
                                .border(1.dp, if (on) g.tint.copy(alpha = 0.6f) else PeakTheme.stroke, RoundedCornerShape(12.dp))
                                .pressable { onForm(form.copy(group = g)); haptics.tap() }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PeakIcons.group(g), null, Modifier.size(13.dp), tint = if (on) g.tint else PeakTheme.textSecondary)
                            Text(tr(g.title), style = PeakTheme.rounded(13, FontWeight.SemiBold), color = if (on) g.tint else PeakTheme.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Il salto minimo del carico: i pulsanti − e + e il numero, che si tocca e si scrive. */
@Composable
private fun StepCard(form: EditorForm, onForm: (EditorForm) -> Unit) {
    val haptics = LocalApp.current.haptics
    var text by remember(form.step) { mutableStateOf(Fmt.weight(form.step, WeightUnit.kg, includeSymbol = false)) }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Incremento del carico")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(PeakIcons.minus) { onForm(form.copy(step = maxOf(0.25, form.step - 0.25))); haptics.tap() }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicTextField(
                        text, { new ->
                            text = new
                            new.replace(',', '.').toDoubleOrNull()?.let { onForm(form.copy(step = clampedStep(form.step, it))) }
                        },
                        Modifier.widthIn(min = 56.dp, max = 80.dp), singleLine = true, cursorBrush = SolidColor(LocalAccent.current),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = PeakTheme.rounded(20, FontWeight.ExtraBold).copy(color = PeakTheme.textPrimary, textAlign = TextAlign.End),
                    )
                    Text(tr("kg"), Modifier.padding(bottom = 3.dp), style = PeakTheme.rounded(13, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                }
                RoundButton(PeakIcons.plus) { onForm(form.copy(step = minOf(50.0, form.step + 0.25))); haptics.tap() }
            }
            Text(tr("È il salto minimo tra un carico e l'altro su questo macchinario: definisce i pulsanti + e −."), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).background(PeakTheme.card, CircleShape).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = PeakTheme.textPrimary)
    }
}

/** Sbarra, dischi, per lato, visualizzazione, come si fa, recupero, note, nascondi agli amici. `existing`: il macchinario che si modifica. */
@Composable
private fun AdvancedCards(form: EditorForm, onForm: (EditorForm) -> Unit, unit: WeightUnit, existing: ExerciseEntity?) {
    val settings = LocalAppSettings.current
    val haptics = LocalApp.current.haptics
    val accent = LocalAccent.current
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Peso della sbarra")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (form.barWeight > 0) Fmt.weight(form.barWeight, unit) else tr("Nessuna (0)"),
                    Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary,
                )
                RoundButton(PeakIcons.minus) { onForm(form.copy(barWeight = stepBar(form.barWeight, -1, unit))) }
                RoundButton(PeakIcons.plus) { onForm(form.copy(barWeight = stepBar(form.barWeight, 1, unit))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, kg) in barPresets(unit)) {
                    ChoiceChip(label, kotlin.math.abs(form.barWeight - kg) < 0.1, { onForm(form.copy(barWeight = kg)); haptics.tap() }, Modifier.weight(1f), tint = accent, verbatim = false)
                }
            }
            Text(
                tr(if (form.barWeight > 0) "Inclusa nel calcolo del carico totale e dei dischi." else "Nessuna sbarra: il carico inserito è solo dei dischi o del macchinario."),
                style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
            )
        }
    }
    if (form.barWeight <= 0) GlassCard {
        SwitchRow("Carico a dischi", form.plateLoaded, { onForm(form.copy(plateLoaded = it)) }, subtitle = "Calcolatore dischi senza sbarra (es. macchinari plate loaded).")
    }
    GlassCard {
        SwitchRow("Peso inserito per lato", form.perSide, { onForm(form.copy(perSide = it)) }, subtitle = "Inserisci il peso di un lato: il totale è il doppio (più la sbarra).")
    }
    if (form.barWeight > 0 && !form.perSide) GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Visualizzazione del carico")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Assoluto (totale)", form.weightDisplay == "absolute", { onForm(form.copy(weightDisplay = "absolute")); haptics.tap() }, Modifier.weight(1f))
                ChoiceChip("Relativo (dischi)", form.weightDisplay == "relative", { onForm(form.copy(weightDisplay = "relative")); haptics.tap() }, Modifier.weight(1f))
            }
            Text(
                tr(if (form.weightDisplay == "relative") "Nelle serie inserisci e visualizzi solo i dischi. Per classifiche e massimali vale sempre il totale con la sbarra." else "Mostra il carico totale sollevato, compreso il peso della sbarra."),
                style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
            )
        }
    }
    if (form.group != MuscleGroup.core) GlassCard {
        val shown = form.limbsShown(existing?.machineLimbs)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Come si fa")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Insieme", shown == Limbs.both, { onForm(form.copy(limbsPick = Limbs.both)); haptics.tap() }, Modifier.weight(1f))
                ChoiceChip(if (form.group == MuscleGroup.gambe) "Una gamba" else "Un braccio", shown == Limbs.one, { onForm(form.copy(limbsPick = Limbs.one)); haptics.tap() }, Modifier.weight(1f))
                ChoiceChip("A scelta", shown == Limbs.either, { onForm(form.copy(limbsPick = Limbs.either)); haptics.tap() }, Modifier.weight(1f))
            }
            Text(tr(limbsHint(shown, form.group)), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
    }
    if (settings.restTimerEnabled) GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Recupero")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (form.restSeconds == 0) tr("Come le impostazioni (%@)", Fmt.clock(settings.restSeconds)) else Fmt.clock(form.restSeconds),
                    Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary,
                )
                RoundButton(PeakIcons.minus) { onForm(form.copy(restSeconds = maxOf(0, form.restSeconds - 15))) }
                RoundButton(PeakIcons.plus) { onForm(form.copy(restSeconds = minOf(600, form.restSeconds + 15))) }
            }
            Text(tr("Tempo di pausa dopo ogni serie su questo macchinario."), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
    }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Note")
            Box {
                if (form.notes.isEmpty()) Text(tr("Es. sedile al foro 4, impugnatura larga"), style = PeakTheme.rounded(14, FontWeight.Normal), color = PeakTheme.textTertiary)
                BasicTextField(
                    form.notes, { onForm(form.copy(notes = it)) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, cursorBrush = SolidColor(accent),
                    textStyle = PeakTheme.rounded(14, FontWeight.Normal).copy(color = PeakTheme.textPrimary),
                )
            }
        }
    }
    GlassCard {
        SwitchRow("Nascondi agli amici", form.isHidden, { onForm(form.copy(isHidden = it)) }, subtitle = "Non compare nel tuo profilo né nelle classifiche.")
    }
}

// ---- immagine e muscoli --------------------------------------------------------------------

/** Foto, muscoli e pittogramma. `showMuscles`: la figura sta qui dentro, tranne che nella creazione di un personalizzato (lì sta già nel passo 3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageCard(form: EditorForm, onForm: (EditorForm) -> Unit, showMuscles: Boolean, existing: ExerciseEntity?) {
    val accent = LocalAccent.current
    val haptics = LocalApp.current.haptics
    val pickPhoto = rememberPhotoPicker { bytes -> onForm(form.withPhoto(bytes)); haptics.commit() }
    val photo = rememberFormPhoto(form)
    val glyph = form.glyph
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader("Immagine")
            if (photo != null) LargePreview(photo, form.photoOffset, { onForm(form.copy(photoOffset = it)) })
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (photo == null) SmallPreview(form)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // al posto di «Scatta foto» e «Scegli dal rullino»: il selettore di sistema offre anche la fotocamera dove c'è
                    ActionLabel(MachinesIcons.photos, "Scegli dalla galleria", accent, pickPhoto)
                    if (photo != null) ActionLabel(MachinesIcons.trash, "Rimuovi foto", PeakTheme.negative) { onForm(form.withoutPhoto()); haptics.tap() }
                }
            }
            Text(
                tr(
                    if (photo != null) "Trascina la foto per scegliere cosa si vede nel riquadro."
                    else if (form.catalogID.isEmpty()) "Senza foto si vede il pittogramma che scegli qui sotto."
                    else "Senza foto si vede il pittogramma dell'attrezzo del catalogo.",
                ),
                style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(PeakTheme.stroke))
            if (showMuscles) {
                SectionHeader("Muscoli")
                MusclesContent(form, onForm)
            }
            SectionHeader("Pittogramma", trailing = glyph?.title ?: "Nessuno")
            if (form.catalogID.isEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.height(44.dp).background(if (glyph == null) form.group.tint.copy(alpha = 0.18f) else PeakTheme.card, RoundedCornerShape(12.dp))
                            .pressable { onForm(form.copy(pickedGlyph = null)); haptics.tap() }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(tr("Nessuno"), style = PeakTheme.rounded(13, FontWeight.Bold), color = if (glyph == null) form.group.tint else PeakTheme.textTertiary) }
                    for (candidate in MachineGlyph.entries) {
                        val on = glyph == candidate
                        Box(
                            Modifier.size(44.dp).background(if (on) form.group.tint.copy(alpha = 0.18f) else PeakTheme.card, RoundedCornerShape(12.dp))
                                .border(1.dp, if (on) form.group.tint.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .pressable { onForm(form.copy(pickedGlyph = candidate)); haptics.tap() },
                            contentAlignment = Alignment.Center,
                        ) { MachineGlyphView(candidate, tint = if (on) form.group.tint else PeakTheme.textTertiary, size = 26.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.pressable(onClick = onClick).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(tr(text), style = PeakTheme.rounded(14, FontWeight.SemiBold), color = color)
    }
}

/** La foto di adesso: quella appena scelta, o quella già salvata. */
@Composable
private fun rememberFormPhoto(form: EditorForm): ImageBitmap? {
    val saved = rememberExercisePhoto(form.photoSha)
    val fresh = remember(form.newPhoto) { form.newPhoto?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    return if (form.newPhoto != null) fresh else saved
}

/** Il riquadro piccolo senza foto: il pittogramma sul colore del gruppo. */
@Composable
private fun SmallPreview(form: EditorForm) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.size(78.dp).clip(shape).background(form.group.tint.copy(alpha = 0.14f)).border(1.dp, PeakTheme.stroke, shape), contentAlignment = Alignment.Center) {
        form.glyph?.let { MachineGlyphView(it, tint = form.group.tint, size = 44.dp) }
    }
}

/** Anteprima grande, trascinabile: è qui che si sceglie l'inquadratura. */
@Composable
private fun LargePreview(image: ImageBitmap, offset: Double, onOffset: (Double) -> Unit) {
    val side = 200.dp
    val aspect = if (image.height > 0) image.width.toDouble() / image.height else 1.0
    val vertical = PhotoFraming.isVertical(aspect)
    val canMove = PhotoFraming.overflow(aspect, side.value.toDouble()) > 1
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FramedPhoto(
            image, offset,
            Modifier.size(side).clip(shape).border(1.dp, PeakTheme.stroke, shape).pointerInput(canMove, vertical, aspect) {
                if (!canMove) return@pointerInput
                var current = offset
                detectDragGestures(onDragStart = { current = offset }) { change, amount ->
                    change.consume()
                    val points = (if (vertical) amount.y else amount.x) / density
                    current = (current + PhotoFraming.normalizedDelta(points.toDouble(), aspect, side.value.toDouble())).coerceIn(-1.0, 1.0)
                    onOffset(current)
                }
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canMove) {
                Icon(MachinesIcons.drag, null, Modifier.size(14.dp), tint = PeakTheme.textTertiary)
                Text(tr(if (vertical) "Trascina in alto o in basso" else "Trascina a destra o a sinistra"), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
            } else {
                Text(tr("Foto già quadrata: si vede tutta."), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
            }
            Box(Modifier.weight(1f))
            if (offset != 0.0) Text(tr("Centra"), Modifier.pressable { onOffset(0.0) }.padding(vertical = 4.dp), style = PeakTheme.rounded(12, FontWeight.SemiBold), color = LocalAccent.current)
        }
    }
}

/** La figura dei muscoli: quelli dell'attrezzo se è collegato, altrimenti si toccano a mano. */
@Composable
private fun MusclesContent(form: EditorForm, onForm: (EditorForm) -> Unit) {
    val entry = form.linkedEntry
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.Start) {
        if (entry != null) {
            Text(tr("Sono quelli dell'attrezzo del catalogo."), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary)
            MuscleFigureView(entry.muscleIDs, Modifier.widthIn(max = 220.dp).fillMaxWidth(), view = AnatomyView.entries.firstOrNull { it.name == entry.view }, zoom = false)
        } else {
            Text(tr("Tocca i muscoli che lavorano sulla figura."), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary)
            MuscleFigureView(form.muscles, Modifier.widthIn(max = 220.dp).fillMaxWidth(), view = form.figureSide, zoom = false, onTap = { onForm(form.toggleMuscle(it)) })
            Text(
                tr(if (form.figureSide == AnatomyView.front) "Vedi di schiena" else "Vedi di fronte"),
                Modifier.background(PeakTheme.cardStrong, RoundedCornerShape(10.dp)).pressable {
                    onForm(form.copy(figureSide = if (form.figureSide == AnatomyView.front) AnatomyView.back else AnatomyView.front))
                }.padding(horizontal = 14.dp, vertical = 8.dp),
                style = PeakTheme.rounded(14, FontWeight.SemiBold), color = LocalAccent.current,
            )
        }
    }
}
