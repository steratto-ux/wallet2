package com.example.finanzecasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Guida all'uso: ogni sezione si apre e si chiude toccando il titolo. */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(0) }
    ScreenScaffold("Guida", onBack = onBack) { inner ->
        FormColumn(inner) {
            guideSections.forEachIndexed { index, section ->
                val expanded = open == index
                AppCard(Modifier.fillMaxWidth(), onClick = { open = if (expanded) -1 else index }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "Chiudi" else "Apri"
                        )
                    }
                    if (expanded) {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            section.blocks.forEach { GuideBlockView(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideBlockView(block: GuideBlock) {
    when (block) {
        is GuideBlock.Par -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
        is GuideBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                    Text(boldTerm(item), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        is GuideBlock.Example -> Callout("Esempio", block.text, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        is GuideBlock.Tip -> Callout("Consiglio", block.text, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun Callout(label: String, text: String, background: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

/** "Termine: spiegazione" -> il termine in grassetto (solo se i due punti sono nelle prime parole). */
private fun boldTerm(item: String): AnnotatedString {
    val cut = item.indexOf(':')
    if (cut < 0 || cut > 40) return AnnotatedString(item)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(item.substring(0, cut + 1)) }
        append(item.substring(cut + 1))
    }
}
