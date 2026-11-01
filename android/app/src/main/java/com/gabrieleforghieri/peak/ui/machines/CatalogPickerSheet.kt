package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SearchField
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.CatalogEntry
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr

/**
 * **Firma condivisa** (iPhone: `Features/Maxes/CatalogPickerSheet.swift`). Proprietario: l'area **machines**, che ne riscrive il corpo
 * (passi 1 e 2 della creazione guidata, suggerimenti della prima volta, ricerca fine); chi la usa non cambia mai questa firma.
 * Usata da: machines (nuovo macchinario, «Collega al catalogo»), start (la ricerca nel catalogo del benvenuto), friends.
 *
 * Il foglio per scegliere una voce del catalogo: prima il gruppo muscolare, poi la ricerca dentro il gruppo.
 * `onPick(voce, gruppo)`: la voce scelta (o `null` = «personalizzato», se `allowsCustom`) e il gruppo da cui si veniva, se ce n'era uno.
 * `title` è una chiave di traduzione. Il foglio si chiude da solo scegliendo; `onDismiss` parte comunque a foglio chiuso.
 */
@Composable
fun CatalogPickerSheet(
    onPick: (CatalogEntry?, MuscleGroup?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Scegli il macchinario",
    allowsCustom: Boolean = true,
) {
    PeakSheet(onDismiss) { close ->
        CatalogPickerBody(title, allowsCustom, wizard = false, onPick = { entry, group -> onPick(entry, group); close() }, onCancel = close)
    }
}

/** Il corpo del foglio, senza il foglio: per chi lo incastra in un'altra schermata (la creazione guidata di un macchinario). */
@Composable
fun CatalogPickerContent(
    title: String,
    allowsCustom: Boolean,
    onPick: (CatalogEntry?, MuscleGroup?) -> Unit,
    modifier: Modifier = Modifier,
) {
    CatalogPickerBody(title, allowsCustom, wizard = false, onPick = onPick, onCancel = null, modifier = modifier)
}

/**
 * Il selettore. `wizard`: passi 1 e 2 della creazione di un macchinario, dentro il foglio dell'editor (che ha già il suo titolo e il suo
 * «Annulla»): mostra «Passo N di 3» e i suggerimenti della prima volta, e non si chiude da solo scegliendo.
 */
@Composable
internal fun CatalogPickerBody(
    title: String,
    allowsCustom: Boolean,
    wizard: Boolean,
    onPick: (CatalogEntry?, MuscleGroup?) -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
    initialGroup: MuscleGroup? = null,
    initialQuery: String = "",
) {
    val exercises by LocalRepository.current.exercises.collectAsState(emptyList())
    val settings = LocalAppSettings.current
    val accent = LocalAccent.current
    val owned = remember(exercises) { exercises.filter { !it.isArchived }.map { it.catalogID }.toSet() }
    var query by remember { mutableStateOf(initialQuery) }
    // prima il gruppo muscolare, poi la ricerca dentro il gruppo
    var group by remember { mutableStateOf(initialGroup) }
    var tipsDone by remember { mutableStateOf(setOf<Int>()) }
    val results = remember(query, group) { Catalog.search(query, group) }
    val italian = Lang.isItalian()
    val showGroups = group == null && query.isEmpty()

    // i suggerimenti della prima volta: uno per passo, finché non hai ancora nessun macchinario
    val tipStep = if (showGroups) 1 else 2
    val tipsOn = wizard && !settings.machineTipsSeen && exercises.isEmpty() && tipStep !in tipsDone

    Box(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onCancel != null || !wizard) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onCancel != null) Text(tr("Annulla"), Modifier.pressable(onClick = onCancel).padding(vertical = 8.dp), style = PeakTheme.rounded(16, FontWeight.Medium), color = accent)
                    Text(tr(title), Modifier.weight(1f).padding(end = if (onCancel != null) 56.dp else 0.dp), style = PeakTheme.rounded(18, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = if (onCancel != null) TextAlign.Center else TextAlign.Start)
                }
            }
            SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp), placeholder = "Cerca nel catalogo")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                if (showGroups) {
                    item("header") {
                        Text(
                            tr(if (wizard) "Passo 1 di 3 · Scegli il gruppo muscolare" else "Scegli il gruppo muscolare, poi cerca"),
                            Modifier.padding(top = 4.dp), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary,
                        )
                    }
                    item("groups") {
                        Column(Modifier.tourTarget("tip-group"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (pair in MuscleGroup.entries.chunked(2)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (g in pair) GroupTile(g, Modifier.weight(1f)) { group = g }
                                    if (pair.size == 1) Box(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    if (wizard) item("step2") {
                        Text(tr("Passo 2 di 3 · Scegli il macchinario"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                    }
                    group?.let { g ->
                        item("back") {
                            Row(Modifier.pressable { group = null }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(MachinesIcons.chevronLeft, null, Modifier.size(18.dp), tint = g.tint)
                                Text(tr("Tutti i gruppi · %@", tr(g.title)), style = PeakTheme.rounded(13, FontWeight.SemiBold), color = g.tint)
                            }
                        }
                    }
                    items(results, key = { it.id }) { entry ->
                        val first = entry.id == results.firstOrNull()?.id
                        Row(
                            Modifier.fillMaxWidth().then(if (first) Modifier.tourTarget("tip-list") else Modifier)
                                .background(PeakTheme.card, RoundedCornerShape(PeakTheme.radiusSmall)).pressable { onPick(entry, group) }.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).background(entry.muscleGroup.tint, CircleShape))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.name(italian), style = PeakTheme.rounded(16, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                                Text("${entry.otherName(italian)} · ${tr(entry.muscleGroup.title)}", style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                            }
                            if (entry.id in owned) Text(tr("Ce l'hai già"), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                        }
                    }
                    if (results.isEmpty()) item("none") {
                        Text(tr("Nessun risultato."), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textTertiary)
                    }
                }
                if (allowsCustom) item("custom") {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.pressable { onPick(null, group) }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(MachinesIcons.plusCircle, null, Modifier.size(20.dp), tint = accent)
                            Text(tr("Non c'è: crea un macchinario personalizzato"), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = accent)
                        }
                        Text(
                            tr("Scegli il più vicino se puoi: solo i macchinari del catalogo entrano nelle classifiche con gli amici."),
                            style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                        )
                    }
                }
            }
        }
        MachineTip(
            if (tipStep == 1) "tip-group" else "tip-list", tipsOn,
            if (tipStep == 1) "Il gruppo" else "Scegli dalla lista",
            if (tipStep == 1) "Parti dal gruppo muscolare: tocca quello che alleni."
            else "Tocca il tuo macchinario. Solo quelli del catalogo entrano nelle classifiche con gli amici; se non c'è, in fondo lo crei tu.",
            onNext = { tipsDone = tipsDone + tipStep },
        )
    }
}

@Composable
private fun GroupTile(group: MuscleGroup, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.background(PeakTheme.card, shape).border(1.dp, PeakTheme.stroke, shape).pressable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PeakIcons.group(group), null, Modifier.size(16.dp), tint = group.tint)
        Text(tr(group.title), Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
    }
}
