package dev.dewy

import androidx.compose.ui.text.SpanStyle
import dev.dewy.ui.Block
import dev.dewy.ui.inlineSpans
import dev.dewy.ui.parseBlocks
import dev.dewy.ui.shortAge
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class MarkdownTest {
    @Test
    fun blocks() {
        val blocks = parseBlocks("Fixed it.\nTwo lines.\n\n## Changes\n- one\n2. two\n```\nval x = 1\n```")
        assertEquals(
            listOf(
                Block.Paragraph("Fixed it.\nTwo lines."),
                Block.Heading("Changes"),
                Block.Bullet("one", "•"),
                Block.Bullet("two", "2."),
                Block.Code("val x = 1"),
            ),
            blocks,
        )
    }

    @Test
    fun inlineMarkersDisappear() {
        assertEquals("run npm test now", inlineSpans("run `npm test` **now**", SpanStyle()).text)
        assertEquals("a ` lone tick", inlineSpans("a ` lone tick", SpanStyle()).text)
    }

    @Test
    fun ages() {
        val now = 1_790_000_000_000L
        assertEquals("now", shortAge(now - 5_000, now, ZoneOffset.UTC))
        assertEquals("4m", shortAge(now - 4 * 60_000, now, ZoneOffset.UTC))
        assertEquals("3h", shortAge(now - 3 * 3_600_000, now, ZoneOffset.UTC))
    }
}
