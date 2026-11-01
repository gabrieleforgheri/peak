package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.util.tr

/** Il campo di ricerca dei selettori (il `.searchable` di SwiftUI): lente, testo, e una X per svuotarlo. `placeholder` è una chiave di traduzione. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Cerca") {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().background(PeakTheme.cardStrong, shape).border(1.dp, PeakTheme.stroke, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PeakIcons.search, null, Modifier.size(18.dp), tint = PeakTheme.textTertiary)
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (value.isEmpty()) Text(tr(placeholder), style = PeakTheme.rounded(15, androidx.compose.ui.text.font.FontWeight.Medium), color = PeakTheme.textTertiary)
            BasicTextField(
                value, onChange, singleLine = true, cursorBrush = SolidColor(LocalAccent.current),
                textStyle = PeakTheme.rounded(15, androidx.compose.ui.text.font.FontWeight.Medium).copy(color = PeakTheme.textPrimary),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                PeakIcons.close, tr("Svuota"),
                Modifier.size(18.dp).clickable(remember { MutableInteractionSource() }, null) { onChange("") }, tint = PeakTheme.textSecondary,
            )
        }
    }
}
