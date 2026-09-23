package com.coderabyss.mobile.exports

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class DocumentSection(val title: String, val text: String)
data class DocumentContent(val title: String, val author: String, val sections: List<DocumentSection>, val sources: List<List<String>>, val findings: List<List<String>> = emptyList())

/** Standards-based Office Open XML packages. No renamed text or HTML files. */
object OfficeDocuments {
    private const val REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
    private const val CONTENT = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    fun escape(s: String) = s.filter { it == '\n' || it == '\t' || it.code >= 32 }.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun packageFile(file: File, entries: Map<String, String>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip -> entries.forEach { (name, body) ->
            zip.putNextEntry(ZipEntry(name)); zip.write((XML + body).toByteArray(Charsets.UTF_8)); zip.closeEntry()
        } }
        ZipFile(file).use { zip -> check(zip.getEntry("[Content_Types].xml") != null && zip.getEntry("_rels/.rels") != null) }
    }
    private fun rootRel(path: String) = "<Relationships xmlns=\"$REL\"><Relationship Id=\"rId1\" Type=\"${OFFICE}officeDocument\" Target=\"$path\"/></Relationships>"
    private fun content(overrides: String) = "<Types xmlns=\"$CONTENT\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>$overrides</Types>"
    fun docx(file: File, doc: DocumentContent) {
        fun paragraph(text: String, style: String = "") = "<w:p>" + (if (style.isNotEmpty()) "<w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>" else "") +
            "<w:r><w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"
        val body = paragraph(doc.title, "Title") + paragraph(doc.author) + doc.sections.joinToString("") {
            paragraph(it.title, "Heading1") + it.text.split('\n').joinToString("") { line -> paragraph(line) }
        }
        packageFile(file, mapOf(
            "[Content_Types].xml" to content("<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"),
            "_rels/.rels" to rootRel("word/document.xml"),
            "word/_rels/document.xml.rels" to "<Relationships xmlns=\"$REL\"><Relationship Id=\"rId1\" Type=\"${OFFICE}styles\" Target=\"styles.xml\"/></Relationships>",
            "word/styles.xml" to "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:pPr><w:spacing w:after=\"160\" w:line=\"360\" w:lineRule=\"auto\"/></w:pPr><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\"/><w:sz w:val=\"22\"/></w:rPr></w:style><w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/><w:rPr><w:b/><w:sz w:val=\"36\"/></w:rPr></w:style><w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:keepNext/><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr></w:style></w:styles>",
            "word/document.xml" to "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>"
        ))
    }
    fun xlsx(file: File, doc: DocumentContent) {
        val sheets = listOf("Sources" to (listOf(listOf("Title", "Author", "Publisher", "Date", "URL", "DOI", "Accessed", "Notes")) + doc.sources),
            "Findings" to (listOf(listOf("Finding", "Value", "Unit", "Source / notes")) + doc.findings))
        val entries = linkedMapOf("[Content_Types].xml" to content("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" + sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }), "_rels/.rels" to rootRel("xl/workbook.xml"))
        entries["xl/workbook.xml"] = "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"${OFFICE.dropLast(1)}\"><sheets>" + sheets.mapIndexed { i, item -> "<sheet name=\"${item.first}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>" }.joinToString("") + "</sheets></workbook>"
        entries["xl/_rels/workbook.xml.rels"] = "<Relationships xmlns=\"$REL\">" + sheets.indices.joinToString("") { "<Relationship Id=\"rId${it+1}\" Type=\"${OFFICE}worksheet\" Target=\"worksheets/sheet${it+1}.xml\"/>" } + "</Relationships>"
        sheets.forEachIndexed { index, (_, rows) ->
            entries["xl/worksheets/sheet${index+1}.xml"] = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols><col min=\"1\" max=\"8\" width=\"30\" customWidth=\"1\"/></cols><sheetData>" +
                rows.mapIndexed { r, row -> "<row r=\"${r+1}\">" + row.mapIndexed { c, value -> "<c r=\"${('A'.code+c).toChar()}${r+1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>" }.joinToString("") + "</row>" }.joinToString("") + "</sheetData></worksheet>"
        }
        packageFile(file, entries)
    }
    fun pptx(file: File, doc: DocumentContent) {
        val slides = listOf(DocumentSection(doc.title, doc.author)) + doc.sections.filter { it.text.isNotBlank() }.take(30)
        val p = "http://schemas.openxmlformats.org/presentationml/2006/main"
        val a = "http://schemas.openxmlformats.org/drawingml/2006/main"
        val group = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
        fun textBox(id: Int, text: String, y: Int, size: Int) = "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Text $id\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"500000\" y=\"$y\"/><a:ext cx=\"11000000\" cy=\"${if(id==2) 900000 else 4800000}\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap=\"square\"/><a:lstStyle/>" + text.split('\n').joinToString("") { "<a:p><a:r><a:rPr lang=\"en-US\" sz=\"$size\"/><a:t>${escape(it)}</a:t></a:r></a:p>" } + "</p:txBody></p:sp>"
        val entries = linkedMapOf("_rels/.rels" to rootRel("ppt/presentation.xml"))
        entries["[Content_Types].xml"] = content("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>" + slides.indices.joinToString("") { "<Override PartName=\"/ppt/slides/slide${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>" })
        entries["ppt/presentation.xml"] = "<p:presentation xmlns:p=\"$p\" xmlns:a=\"$a\" xmlns:r=\"${OFFICE.dropLast(1)}\"><p:sldIdLst>" + slides.indices.joinToString("") { "<p:sldId id=\"${256+it}\" r:id=\"rId${it+1}\"/>" } + "</p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>"
        entries["ppt/_rels/presentation.xml.rels"] = "<Relationships xmlns=\"$REL\">" + slides.indices.joinToString("") { "<Relationship Id=\"rId${it+1}\" Type=\"${OFFICE}slide\" Target=\"slides/slide${it+1}.xml\"/>" } + "</Relationships>"
        slides.forEachIndexed { i, section ->
            val summary = section.text.split(Regex("(?<=[.!?])\\s+|\\n")).filter { it.isNotBlank() }.take(5).joinToString("\n") { if(it.length <= 180) it else it.take(180).substringBeforeLast(' ', it.take(180)) }
            entries["ppt/slides/slide${i+1}.xml"] = "<p:sld xmlns:p=\"$p\" xmlns:a=\"$a\"><p:cSld><p:spTree>$group${textBox(2, section.title, 350000, 3200)}${textBox(3, summary, 1450000, 2200)}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
        }
        packageFile(file, entries)
    }
}
