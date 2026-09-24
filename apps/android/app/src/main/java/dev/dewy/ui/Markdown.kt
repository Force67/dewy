package dev.dewy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The little Markdown agents write: fences, bullets, headings, `code`, **bold**. */
sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Bullet(val text: String, val marker: String) : Block
    data class Code(val code: String) : Block
}

fun parseBlocks(text: String): List<Block> {
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim())
        para.clear()
    }
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                out += Block.Code(code.toString())
            }
            trimmed.startsWith("#") -> {
                flush()
                out += Block.Heading(trimmed.trimStart('#').trim())
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flush()
                out += Block.Bullet(trimmed.drop(2), "•")
            }
            Regex("^\\d+[.)] ").containsMatchIn(trimmed) -> {
                flush()
                val marker = trimmed.substringBefore(' ')
                out += Block.Bullet(trimmed.substringAfter(' '), marker)
            }
            line.isBlank() -> flush()
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
        }
        i++
    }
    flush()
    return out
}

/** Inline `code` and **bold** as styled spans. */
fun inlineSpans(text: String, codeStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) && text.indexOf("**", i + 2) > i + 2 -> {
                val end = text.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text, i + 2, end) }
                i = end + 2
            }
            text[i] == '`' && text.indexOf('`', i + 1) > i + 1 -> {
                val end = text.indexOf('`', i + 1)
                withStyle(codeStyle) { append(text, i + 1, end) }
                i = end + 1
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

@Composable
fun Markdown(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.ink) {
    val p = LocalPalette.current
    val blocks = remember(text) { parseBlocks(text) }
    val codeSpan = SpanStyle(fontFamily = FontFamily.Monospace, background = p.code, color = p.ink)
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { b ->
                when (b) {
                    is Block.Paragraph -> Text(inlineSpans(b.text, codeSpan), style = Type.body, color = color)
                    is Block.Heading -> Text(inlineSpans(b.text, codeSpan), style = Type.headline, color = color)
                    is Block.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(b.marker, style = Type.body, color = p.inkSoft)
                        Text(inlineSpans(b.text, codeSpan), style = Type.body, color = color)
                    }
                    is Block.Code -> Text(
                        b.code,
                        style = Type.mono,
                        color = p.ink,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardShape)
                            .background(p.code)
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}
