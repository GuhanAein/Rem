package app.rem.core.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfWriterTest {
    @Test fun writesAWellFormedMultiPagePdf() {
        val lines = listOf(PdfWriter.Line("Rem", 26f, bold = true)) + (1..80).map { PdfWriter.Line("Pay rent (₹25,000) · “soon”", gapBefore = 4f) }
        val bytes = PdfWriter.write(lines)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("/Count 3"))
        assertTrue(text.contains("(Pay rent \\(Rs 25,000\\) · \"soon\") Tj"))
        // The xref offsets must point at real objects.
        val xrefAt = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.substring(xrefAt).startsWith("xref"))
        val firstOffset = text.substring(xrefAt).lines()[3].substring(0, 10).toInt()
        assertTrue(text.substring(firstOffset).startsWith("1 0 obj"))
    }

    @Test fun transliteratesUnsupportedCharacters() {
        assertEquals("Rs 649 -> \"ok\"...", PdfWriter.transliterate("₹649 → “ok”…"))
    }
}
