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
 * Excel (.xlsx) export with the front and back photos placed inside their cells (Excel "Place in Cell"), and a reader so an edited
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
    /** Row height in points, tall enough for the photos placed in the cells. */
    private const val ROW_PT = 96.0

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

        // Photo k goes in its cell as "value metadata" k+1, which points at rich value k, which points at image k
        val vm = HashMap<Pair<Int, Int>, Int>()
        pics.forEachIndexed { k, p -> vm[p.col to p.row] = k + 1 }

        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            val images = pics.isNotEmpty()
            put("[Content_Types].xml", contentTypes(images))
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Collection" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""")
                append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                if (images) {
                    append("""<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sheetMetadata" Target="metadata.xml"/>""")
                    append("""<Relationship Id="rId4" Type="http://schemas.microsoft.com/office/2022/10/relationships/richValueRel" Target="richData/richValueRel.xml"/>""")
                    append("""<Relationship Id="rId5" Type="http://schemas.microsoft.com/office/2017/06/relationships/rdRichValue" Target="richData/rdrichvalue.xml"/>""")
                    append("""<Relationship Id="rId6" Type="http://schemas.microsoft.com/office/2017/06/relationships/rdRichValueStructure" Target="richData/rdrichvaluestructure.xml"/>""")
                    append("""<Relationship Id="rId7" Type="http://schemas.microsoft.com/office/2017/06/relationships/rdRichValueTypes" Target="richData/rdRichValueTypes.xml"/>""")
                }
                append("</Relationships>")
            })
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheet(cards, vm))
            if (images) {
                val n = pics.size
                put("xl/metadata.xml", buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><metadata xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:xlrd="http://schemas.microsoft.com/office/spreadsheetml/2017/richdata">""")
                    append("""<metadataTypes count="1"><metadataType name="XLRICHVALUE" minSupportedVersion="120000" copy="1" pasteAll="1" pasteValues="1" merge="1" splitFirst="1" rowColShift="1" clearFormats="1" clearComments="1" assign="1" coerce="1"/></metadataTypes>""")
                    append("""<futureMetadata name="XLRICHVALUE" count="$n">""")
                    for (k in 0 until n) append("""<bk><extLst><ext uri="{3e2802c4-a4d2-4d8b-9148-e3be6c30e623}"><xlrd:rvb i="$k"/></ext></extLst></bk>""")
                    append("""</futureMetadata><valueMetadata count="$n">""")
                    for (k in 0 until n) append("""<bk><rc t="1" v="$k"/></bk>""")
                    append("</valueMetadata></metadata>")
                })
                put("xl/richData/rdrichvalue.xml", buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><rvData xmlns="http://schemas.microsoft.com/office/spreadsheetml/2017/richdata" count="$n">""")
                    for (k in 0 until n) append("""<rv s="0"><v>$k</v><v>5</v></rv>""")
                    append("</rvData>")
                })
                put("xl/richData/rdrichvaluestructure.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<rvStructures xmlns="http://schemas.microsoft.com/office/spreadsheetml/2017/richdata" count="1"><s t="_localImage"><k n="_rvRel:LocalImageIdentifier" t="i"/><k n="CalcOrigin" t="i"/></s></rvStructures>""")
                put("xl/richData/rdRichValueTypes.xml", RV_TYPES)
                put("xl/richData/richValueRel.xml", buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><richValueRels xmlns="http://schemas.microsoft.com/office/spreadsheetml/2022/richvaluerel" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                    for (k in 0 until n) append("""<rel r:id="rId${k + 1}"/>""")
                    append("</richValueRels>")
                })
                put("xl/richData/_rels/richValueRel.xml.rels", buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                    for (k in 0 until n) append("""<Relationship Id="rId${k + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image${k + 1}.jpeg"/>""")
                    append("</Relationships>")
                })
                pics.forEachIndexed { k, p ->
                    zip.putNextEntry(ZipEntry("xl/media/image${k + 1}.jpeg")); zip.write(p.jpeg); zip.closeEntry()
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
        if (images) {
            append("""<Override PartName="/xl/metadata.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheetMetadata+xml"/>""")
            append("""<Override PartName="/xl/richData/rdRichValueTypes.xml" ContentType="application/vnd.ms-excel.rdrichvaluetypes+xml"/>""")
            append("""<Override PartName="/xl/richData/rdrichvalue.xml" ContentType="application/vnd.ms-excel.rdrichvalue+xml"/>""")
            append("""<Override PartName="/xl/richData/rdrichvaluestructure.xml" ContentType="application/vnd.ms-excel.rdrichvaluestructure+xml"/>""")
            append("""<Override PartName="/xl/richData/richValueRel.xml" ContentType="application/vnd.ms-excel.richvaluerel+xml"/>""")
        }
        append("</Types>")
    }

    /** Style 0 = normal (top-aligned, wraps), 1 = bold header. */
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun sheet(cards: List<Card>, vm: Map<Pair<Int, Int>, Int>) = buildString {
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
            // Photos sit inside their cells (Excel "Place in Cell")
            for (col in listOf(COL_FRONT, COL_BACK)) vm[col to (r - 1)]?.let { append("""<c r="${ref(col, r)}" t="e" vm="$it"><v>#VALUE!</v></c>""") }
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

    private const val RV_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<rvTypesInfo xmlns="http://schemas.microsoft.com/office/spreadsheetml/2017/richdata2" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" mc:Ignorable="x" xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><global><keyFlags><key name="_Self"><flag name="ExcludeFromFile" value="1"/><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_DisplayString"><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_Flags"><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_Format"><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_SubLabel"><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_Attribution"><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_Icon"><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_Display"><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_CanonicalPropertyNames"><flag name="ExcludeFromCalcComparison" value="1"/></key><key name="_ClassificationId"><flag name="ExcludeFromCalcComparison" value="1"/></key></keyFlags></global></rvTypesInfo>"""

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
