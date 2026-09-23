package com.coderabyss.mobile.exports

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class DocumentImage(val png: ByteArray, val width: Int, val height: Int, val caption: String)

/** Embed independent managed images in Office packages rather than linking external paths. */
object DocumentAssets {
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    fun embed(file: File, format: String, images: List<DocumentImage>) {
        if(images.isEmpty() || format !in setOf("docx", "pptx")) return
        val entries = linkedMapOf<String, ByteArray>()
        ZipFile(file).use { zip -> zip.entries().asSequence().forEach { entry -> entries[entry.name] = zip.getInputStream(entry).use { it.readBytes() } } }
        fun xml(path: String) = entries.getValue(path).toString(Charsets.UTF_8)
        fun put(path: String, text: String) { entries[path] = text.toByteArray(Charsets.UTF_8) }
        put("[Content_Types].xml", xml("[Content_Types].xml").replace("</Types>", "<Default Extension=\"png\" ContentType=\"image/png\"/></Types>"))
        images.forEachIndexed { index, image ->
            val i = index + 1; val caption = OfficeDocuments.escape(image.caption)
            if(format == "docx") {
                val width = minOf(5_500_000L, 7_000_000L * image.width / image.height); val height = width * image.height / image.width
                entries["word/media/asset$i.png"] = image.png
                put("word/_rels/document.xml.rels", xml("word/_rels/document.xml.rels").replace("</Relationships>", "<Relationship Id=\"image$i\" Type=\"$R/image\" Target=\"media/asset$i.png\"/></Relationships>"))
                val drawing = """<w:p><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="$A" xmlns:r="$R"><wp:extent cx="$width" cy="$height"/><wp:docPr id="${1000+i}" name="Asset $i"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="$i" name="Asset $i"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="image$i"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$width" cy="$height"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:p><w:r><w:t>$caption</w:t></w:r></w:p>"""
                put("word/document.xml", xml("word/document.xml").replace("<w:sectPr>", drawing + "<w:sectPr>"))
            } else {
                val slide = 1000 + i; val width = minOf(10_500_000L, 5_800_000L * image.width / image.height); val height = width * image.height / image.width
                entries["ppt/media/asset$i.png"] = image.png
                put("ppt/presentation.xml", xml("ppt/presentation.xml").replace("</p:sldIdLst>", "<p:sldId id=\"${2000+i}\" r:id=\"imageSlide$i\"/></p:sldIdLst>"))
                put("ppt/_rels/presentation.xml.rels", xml("ppt/_rels/presentation.xml.rels").replace("</Relationships>", "<Relationship Id=\"imageSlide$i\" Type=\"$R/slide\" Target=\"slides/slide$slide.xml\"/></Relationships>"))
                put("[Content_Types].xml", xml("[Content_Types].xml").replace("</Types>", "<Override PartName=\"/ppt/slides/slide$slide.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/></Types>"))
                put("ppt/slides/_rels/slide$slide.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="image" Type="$R/image" Target="../media/asset$i.png"/></Relationships>""")
                put("ppt/slides/slide$slide.xml", """<p:sld xmlns:p="$P" xmlns:a="$A" xmlns:r="$R"><p:cSld name="$caption"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/><p:pic><p:nvPicPr><p:cNvPr id="2" name="$caption"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="image"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="600000" y="500000"/><a:ext cx="$width" cy="$height"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>""")
            }
        }
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, data) -> zip.putNextEntry(ZipEntry(path)); zip.write(data); zip.closeEntry() } }
        ZipFile(file).use { zip -> check(zip.entries().asSequence().count { it.name.endsWith(".png") } == images.size) }
    }
}
