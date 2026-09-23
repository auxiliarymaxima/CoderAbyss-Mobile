package com.coderabyss.mobile

import com.coderabyss.mobile.exports.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class OfficeDocumentsTest {
    @Test fun packagesAreRealOpenXmlAndEscapeUntrustedText() {
        val doc = DocumentContent("A < B & research", "Author", listOf(DocumentSection("Findings", "One result. Another result.")), listOf(listOf("Source", "", "Publisher", "2026", "https://example.org/?x=1&y=2", "", "2026-09-22", "Notes")))
        val directory = File(System.getProperty("java.io.tmpdir"), "coder-abyss-office-validation").apply { mkdirs() }
        for (format in listOf("docx", "pptx", "xlsx")) {
            val file = File(directory, "research.$format")
            when (format) { "docx" -> OfficeDocuments.docx(file, doc); "pptx" -> OfficeDocuments.pptx(file, doc); else -> OfficeDocuments.xlsx(file, doc) }
            ZipFile(file).use { zip ->
                assertNotNull(zip.getEntry("[Content_Types].xml"))
                zip.entries().asSequence().forEach { entry ->
                    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    zip.getInputStream(entry).use { factory.newDocumentBuilder().parse(it) }
                }
                val expected = when (format) { "docx" -> "word/document.xml"; "pptx" -> "ppt/slides/slide1.xml"; else -> "xl/worksheets/sheet1.xml" }
                assertNotNull(zip.getEntry(expected))
                if (format == "xlsx") assertFalse(zip.getInputStream(zip.getEntry(expected)).reader().readText().contains("One result."))
            }
        }
    }
    @Test fun embeddedImagesArePackagedWithInternalRelationships() {
        val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l1sAAAAASUVORK5CYII=")
        val directory = File(System.getProperty("java.io.tmpdir"), "coder-abyss-office-validation")
        val doc = DocumentContent("Asset report", "Author", listOf(DocumentSection("Results", "Saved project image.")), emptyList())
        for(format in listOf("docx", "pptx")) {
            val file = File(directory, "assets.$format")
            if(format == "docx") OfficeDocuments.docx(file, doc) else OfficeDocuments.pptx(file, doc)
            DocumentAssets.embed(file, format, listOf(DocumentImage(png, 1, 1, "Managed image")))
            ZipFile(file).use { zip ->
                assertEquals(1, zip.entries().asSequence().count { it.name.endsWith(".png") })
                zip.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".rels") }.forEach { entry ->
                    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    zip.getInputStream(entry).use { factory.newDocumentBuilder().parse(it) }
                }
            }
        }
    }
}
