package app.rem.core.export

import java.io.ByteArrayOutputStream

/**
 * A tiny, dependency-free PDF writer for clean printable lists (A4, Helvetica). Text outside
 * the PDF's built-in WinAnsi set is transliterated (₹ -> "Rs ", curly quotes -> straight).
 */
object PdfWriter {
    data class Line(val text: String, val size: Float = 11f, val bold: Boolean = false, val grey: Boolean = false, val gapBefore: Float = 0f)

    private const val W = 595f
    private const val H = 842f
    private const val MARGIN = 56f

    fun write(lines: List<Line>): ByteArray {
        // Lay lines out into pages.
        val pages = mutableListOf<StringBuilder>()
        var page = StringBuilder()
        var y = H - MARGIN
        for (l in lines) {
            val lead = l.size * 1.45f + l.gapBefore
            if (y - lead < MARGIN) {
                pages += page
                page = StringBuilder()
                y = H - MARGIN
            }
            y -= lead
            val font = if (l.bold) "F2" else "F1"
            val gray = if (l.grey) "0.45 g" else "0 g"
            page.append("BT /$font ${fmt(l.size)} Tf $gray ${fmt(MARGIN)} ${fmt(y)} Td (${escape(l.text)}) Tj ET\n")
        }
        pages += page

        val objects = mutableListOf<String>()
        // 1 catalog, 2 pages, 3 font regular, 4 font bold, then (content, page) pairs.
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        val kids = pages.indices.joinToString(" ") { "${6 + it * 2} 0 R" }
        objects += "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
        pages.forEachIndexed { i, content ->
            val bytes = content.toString().toByteArray(Charsets.ISO_8859_1)
            objects += "<< /Length ${bytes.size} >>\nstream\n${content}endstream"
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(W)} ${fmt(H)}] /Contents ${5 + i * 2} 0 R /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> >>"
        }

        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("%PDF-1.4\n%âãÏÓ\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { i, body ->
            offsets += out.size()
            w("${i + 1} 0 obj\n$body\nendobj\n")
        }
        val xref = out.size()
        w("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { w("%010d 00000 n \n".format(it)) }
        w("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    private fun fmt(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else "%.2f".format(f)

    internal fun transliterate(s: String): String = buildString {
        for (ch in s) when (ch) {
            '₹' -> append("Rs ")
            '“', '”' -> append('"')
            '‘', '’' -> append('\'')
            '–', '—' -> append('-')
            '→' -> append("->")
            '…' -> append("...")
            else -> append(if (ch.code in 32..255) ch else '?')
        }
    }

    private fun escape(s: String) = transliterate(s).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
}
