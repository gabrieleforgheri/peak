package com.gabrieleforghieri.peak.ui.start

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.Motion
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.launch

/** Le quattro idee di Peak: titolo e riga (`slides` di `IdeasView`). */
val ideaSlides: List<Pair<String, String>> = listOf(
    "Sali ogni settimana" to "Raggiungi l'obiettivo e allunga la serie di settimane.",
    "Batti i tuoi record" to "Ogni serie più forte diventa un record.",
    "Medaglie a tre livelli" to "Bronzo, argento, oro: sempre un traguardo più su.",
    "Sfida gli amici" to "Invitali con un link e confrontatevi sugli stessi macchinari.",
)

/**
 * Le quattro idee di Peak, a tutto schermo e nello stesso linguaggio del primo avvio: una piccola animazione che si ripete, un
 * titolo e una riga. Si scorre con «Avanti» o col dito (`IdeasView.swift`, come `Ideas.svelte`): con «riduci movimento» ogni disegno
 * resta il fotogramma finale. `onNext` parte all'ultima («Proviamo»), `onSkip` da «Salta».
 */
@Composable
fun IdeasScreen(onNext: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val accent = LocalAccent.current
    val reduce = LocalReduceMotion.current
    val pages = ideaSlides.size
    val pager = rememberPagerState(initialPage) { pages }
    val scope = rememberCoroutineScope()
    val at = pager.currentPage
    val haptics = LocalApp.current.haptics
    val fill by animateFloatAsState((at + 1f) / pages, if (reduce) snap() else Motion.out(350), label = "ideas-progress")

    BoxWithConstraints(modifier.fillMaxSize().then(PeakTheme.backgroundModifier(accent)).statusBarsPadding().navigationBarsPadding()) {
        val w = maxWidth
        val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape).background(accent))
                }
                Text(
                    tr("Salta"), Modifier.pressable(onClick = onSkip).heightIn(min = 44.dp).padding(horizontal = 12.dp).padding(top = 12.dp),
                    style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textSecondary,
                )
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i -> Slide(i, active = at == i, w.value, h.value) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                PrimaryButton(
                    tr(if (at == pages - 1) "Proviamo" else "Avanti"),
                    {
                        haptics.tap()
                        if (at == pages - 1) onNext() else scope.launch { pager.animateScrollToPage(at + 1) }
                    },
                    Modifier.widthIn(max = 460.dp),
                )
            }
        }
    }
}

@Composable
private fun Slide(i: Int, active: Boolean, width: Float, height: Float) {
    val size = minOf(34f, maxOf(26f, width * 0.074f))
    val artW = minOf(480f, width - 48f, minOf(height * 0.46f, 400f) * (320f / 240f))
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IdeaArt(i, active, Modifier.width(artW.dp))
        Column(Modifier.widthIn(max = 460.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StartTitleCentered(tr(ideaSlides[i].first), size)
            // due righe sempre: il titolo non salta da una pagina all'altra
            Text(
                tr(ideaSlides[i].second),
                style = PeakTheme.rounded(16, FontWeight.Normal), color = PeakTheme.textSecondary, textAlign = TextAlign.Center, minLines = 2, maxLines = 2,
            )
        }
    }
}

@Composable
private fun StartTitleCentered(text: String, size: Float) {
    Text(
        text,
        style = PeakTheme.rounded(size.toInt(), FontWeight.Black).copy(
            fontSize = androidx.compose.ui.unit.TextUnit(size, androidx.compose.ui.unit.TextUnitType.Sp),
            letterSpacing = androidx.compose.ui.unit.TextUnit(-0.03f * size, androidx.compose.ui.unit.TextUnitType.Sp),
        ),
        color = PeakTheme.textPrimary, textAlign = TextAlign.Center,
    )
}
