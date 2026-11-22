package dev.mazzagotte.strike3.data

import android.graphics.Bitmap
import android.util.Xml
import dev.mazzagotte.strike3.render.EditParams
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.SourceStore
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Excel (.xlsx) export with a small front and back photo in every row, and a reader so an edited
 * file can be imported straight back. Written by hand (an .xlsx is a zip of XML files), so no big
 * spreadsheet library is needed.
 *
 * Same columns as the CSV, plus Front and Back photos. The ID column links each row to its card.
 */
object Xlsx {

    private val HEADER = listOf(
        "ID", "Front", "Back", "Player", "Year", "Set", "Parallel", "Serial", "Team",
        "Auto", "Rookie", "Relic", "Current value", "Date acquired", "Posted", "Notes",
    )
    private const val COL_FRONT = 1
    private const val COL_BACK = 2
    private const val THUMB_PX = 360
    /** Row height in points; photos are a little shorter so they sit inside the row. */
    private const val ROW_PT = 96.0
    private const val PHOTO_PT = 90.0
    private const val EMU_PER_PT = 12700L

    // ---------- Export ----------

    private class Pic(val col: Int, val row: Int, val jpeg: ByteArray, val w: Int, val h: Int)

    /** Writes the collection to [out]. [onProgress] gets (done, total) while photos are made. */
    fun write(out: OutputStream, cards: List<Card>, onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        val pics = mutableListOf<Pic>()
        cards.forEachIndexed { i, c ->
            val row = i + 1 // row 0 is the header
            val turn = EditParams.fromJson(c.editParams).rotation
            thumb(c.sourcePath, turn)?.let { (b, w, h) -> pics += Pic(COL_FRONT, row, b, w, h) }
            thumb(c.backPath, 0)?.let { (b, w, h) -> pics += Pic(COL_BACK, row, b, w, h) }
            onProgress(i + 1, cards.size)
        }

        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(pics.isNotEmpty()))
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Collection" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheet(cards, pics.isNotEmpty()))
            if (pics.isNotEmpty()) {
                put("xl/worksheets/_rels/sheet1.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>""")
                put("xl/drawings/drawing1.xml", drawing(pics))
                put("xl/drawings/_rels/drawing1.xml.rels", buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                    pics.indices.forEach { n ->
                        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image${n + 1}.jpeg"/>""")
                    }
                    append("</Relationships>")
                })
                pics.forEachIndexed { n, p ->
                    zip.putNextEntry(ZipEntry("xl/media/image${n + 1}.jpeg")); zip.write(p.jpeg); zip.closeEntry()
                }
            }
        }
    }

    private fun thumb(path: String?, turn: Int): Triple<ByteArray, Int, Int>? {
        val src = SourceStore.loadScaled(path, THUMB_PX * 2) ?: return null
        val upright = PhotoTools.rotate(src, turn)
        val scale = THUMB_PX.toFloat() / maxOf(upright.width, upright.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt().coerceAtLeast(1), (upright.height * scale).toInt().coerceAtLeast(1), true) else upright
        val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        return Triple(bytes, small.width, small.height)
    }

    private fun contentTypes(images: Boolean) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
        if (images) append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        if (images) append("""<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
        append("</Types>")
    }

    /** Style 0 = normal (top-aligned, wraps), 1 = bold header. */
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun sheet(cards: List<Card>, hasDrawing: Boolean) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        // Keep the header visible while scrolling
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        val widths = listOf(6, 13, 13, 22, 7, 22, 18, 10, 18, 6, 7, 6, 13, 13, 7, 30)
        widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        append("""<row r="1">""")
        HEADER.forEachIndexed { i, h -> append(textCell(i, 1, h, style = 1)) }
        append("</row>")
        cards.forEachIndexed { i, c ->
            val r = i + 2
            append("""<row r="$r" ht="$ROW_PT" customHeight="1">""")
            append(numberCell(0, r, c.id.toDouble()))
            val values = listOf(
                c.player, c.year, c.setName, c.parallel, c.serial, c.team,
                yes(c.isAuto), yes(c.isRookie), yes(c.isPatch),
            )
            values.forEachIndexed { k, v -> if (v.isNotEmpty()) append(textCell(3 + k, r, v)) }
            val value = c.valueNumber
            if (value != null && c.currentValue.trim().all { it.isDigit() || it in "$,. " }) append(numberCell(12, r, value))
            else if (c.currentValue.isNotBlank()) append(textCell(12, r, c.currentValue))
            if (c.dateAcquired.isNotBlank()) append(textCell(13, r, c.dateAcquired))
            append(textCell(14, r, yes(c.posted)))
            if (c.notes.isNotBlank()) append(textCell(15, r, c.notes))
            append("</row>")
        }
        append("</sheetData>")
        if (hasDrawing) append("""<drawing r:id="rId1"/>""")
        append("</worksheet>")
    }

    private fun yes(b: Boolean) = if (b) "Yes" else ""

    private fun ref(col: Int, row: Int): String {
        var n = col + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }
        return "$sb$row"
    }

    private fun textCell(col: Int, row: Int, text: String, style: Int = 0) =
        """<c r="${ref(col, row)}" t="inlineStr"${if (style != 0) " s=\"$style\"" else ""}><is><t xml:space="preserve">${esc(text)}</t></is></c>"""

    private fun numberCell(col: Int, row: Int, v: Double): String {
        val s = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        return """<c r="${ref(col, row)}"><v>$s</v></c>"""
    }

    private fun esc(s: String) = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch == '\n' || ch == '\t' || ch >= ' ' -> append(ch)
            else -> {} // control characters aren't allowed in XML
        }
    }

    private fun drawing(pics: List<Pic>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        pics.forEachIndexed { n, p ->
            val cy = (PHOTO_PT * EMU_PER_PT).toLong()
            val cx = cy * p.w / p.h
            val pad = 3 * EMU_PER_PT
            append("""<xdr:oneCellAnchor><xdr:from><xdr:col>${p.col}</xdr:col><xdr:colOff>$pad</xdr:colOff><xdr:row>${p.row}</xdr:row><xdr:rowOff>$pad</xdr:rowOff></xdr:from>""")
            append("""<xdr:ext cx="$cx" cy="$cy"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="${n + 2}" name="Photo ${n + 1}"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>""")
            append("""<xdr:blipFill><a:blip r:embed="rId${n + 1}"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>""")
            append("""<xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>""")
        }
        append("</xdr:wsDr>")
    }

    // ---------- Import ----------

    /** True when the bytes are a zip (an .xlsx), not a CSV. */
    fun looks(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** The first sheet's cells as rows of text, ready for [CsvImport.planRows]. */
    fun readRows(input: InputStream): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && e.name.endsWith(".xml") || e.name.endsWith(".rels")) files[e.name.removePrefix("/")] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val sheetPath = firstSheetPath(files) ?: error("no sheet found in the file")
        val sheet = files[sheetPath] ?: error("no sheet found in the file")
        return cells(sheet, shared)
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(bytes.inputStream(), "UTF-8")
        }

    private fun local(name: String) = name.substringAfter(':')

    /** Follows workbook.xml to the first sheet's file (usually xl/worksheets/sheet1.xml). */
    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        val wb = files["xl/workbook.xml"] ?: return files.keys.firstOrNull { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
        var rid: String? = null
        val p = parser(wb)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && local(p.name) == "sheet") {
                rid = (0 until p.attributeCount).firstOrNull { local(p.getAttributeName(it)) == "id" }?.let { p.getAttributeValue(it) }
                break
            }
        }
        val rels = files["xl/_rels/workbook.xml.rels"]
        if (rid != null && rels != null) {
            val r = parser(rels)
            while (r.next() != XmlPullParser.END_DOCUMENT) {
                if (r.eventType == XmlPullParser.START_TAG && local(r.name) == "Relationship" && r.getAttributeValue(null, "Id") == rid) {
                    val target = r.getAttributeValue(null, "Target") ?: break
                    return if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
                }
            }
        }
        return files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.minOrNull()
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val p = parser(bytes)
        var cur: StringBuilder? = null
        var inText = false
        var inPhonetic = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "si" -> cur = StringBuilder()
                    "t" -> inText = !inPhonetic
                    "rPh" -> inPhonetic = true
                }
                XmlPullParser.TEXT -> if (inText) cur?.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "si" -> { out += cur?.toString().orEmpty(); cur = null }
                    "t" -> inText = false
                    "rPh" -> inPhonetic = false
                }
            }
        }
        return out
    }

    private fun cells(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        val p = parser(bytes)
        var rowIndex = 0
        var col = 0
        var type: String? = null
        var value = StringBuilder()
        var inValue = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "row" -> rowIndex = p.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowIndex + 1)
                    "c" -> {
                        val r = p.getAttributeValue(null, "r")
                        col = if (r != null) colIndex(r) else col + 1
                        type = p.getAttributeValue(null, "t")
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                            "b" -> if (raw.trim() == "1") "Yes" else ""
                            else -> raw
                        }
                        rows.getOrPut(rowIndex) { mutableMapOf() }[col] = text
                    }
                }
            }
        }
        return rows.values.map { m ->
            val width = (m.keys.maxOrNull() ?: -1) + 1
            List(width) { m[it].orEmpty() }
        }
    }

    /** "C12" -> 2 */
    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (!ch.isLetter()) break; n = n * 26 + (ch.uppercaseChar() - 'A' + 1) }
        return n - 1
    }
}
