package com.aeibi.design.data.projects

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceExporterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun export_producesZipWithRelativeEntries() {
        val source = temporaryFolder.newFolder("source")
        File(source, "index.html").writeText("<html/>")
        File(source, "assets/app.js").apply { parentFile.mkdirs() }.writeText("console.log(1)")
        val zip = File(temporaryFolder.root, "workspace.zip")

        WorkspaceExporter.exportDirectory(source, zip)

        assertTrue(zip.isFile)
        val target = temporaryFolder.newFolder("target")
        assertEquals(2, WorkspaceImporter.importArchive(zip, target))
        assertEquals("<html/>", File(target, "index.html").readText())
        assertEquals("console.log(1)", File(target, "assets/app.js").readText())
    }

    @Test
    fun export_emptyDirectory_producesEmptyZip() {
        val source = temporaryFolder.newFolder("empty")
        val zip = File(temporaryFolder.root, "empty.zip")

        WorkspaceExporter.exportDirectory(source, zip)

        assertTrue(zip.isFile)
        assertEquals(0, WorkspaceImporter.importArchive(zip, temporaryFolder.newFolder("out")))
    }
}
