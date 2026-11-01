package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.MachineGlyphView
import com.gabrieleforghieri.peak.design.MuscleGroupChip
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SearchField
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.CatalogEntry
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
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
        CatalogPickerContent(title, allowsCustom, onPick = { entry, group -> onPick(entry, group); close() })
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
    val exercises by LocalRepository.current.exercises.collectAsState(emptyList())
    val owned = remember(exercises) { exercises.filter { !it.isArchived }.map { it.catalogID }.toSet() }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    val results = remember(query, group) { Catalog.search(query, group) }
    val italian = Lang.isItalian()

    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr(title), Modifier.padding(top = 8.dp), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary)
        SearchField(query, { query = it }, placeholder = "Cerca un macchinario")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MuscleGroup.entries) { g ->
                val on = group == g
                Text(
                    tr(g.title),
                    Modifier
                        .background(if (on) g.tint.copy(alpha = 0.22f) else PeakTheme.card, CircleShape)
                        .border(1.dp, if (on) g.tint.copy(alpha = 0.4f) else PeakTheme.stroke, CircleShape)
                        .pressable { group = if (on) null else g }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = PeakTheme.rounded(13, FontWeight.Bold), color = if (on) g.tint else PeakTheme.textSecondary,
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (allowsCustom) item("custom") {
                Row(
                    Modifier.fillMaxWidth().background(PeakTheme.card, RoundedCornerShape(PeakTheme.radiusSmall)).pressable { onPick(null, group) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(tr("Personalizzato"), Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary) }
            }
            items(results, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().background(PeakTheme.card, RoundedCornerShape(PeakTheme.radiusSmall)).pressable { onPick(entry, group) }.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.background(entry.muscleGroup.tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).padding(8.dp)) {
                        MachineGlyphView(entry.machineGlyph, tint = entry.muscleGroup.tint)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(entry.name(italian), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
                        if (entry.id in owned) Text(tr("Già aggiunto"), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                    }
                    MuscleGroupChip(entry.muscleGroup, compact = true)
                }
            }
        }
    }
}
