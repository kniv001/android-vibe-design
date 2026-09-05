package com.aeibi.design.data.projects

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceImporterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun import_restoresNestedFiles() {
        val zip = File(temporaryFolder.root, "content.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("index.html"))
            out.write("<html/>".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("img/logo.png"))
            out.write("png-data".toByteArray())
            out.closeEntry()
        }
        val target = temporaryFolder.newFolder("target")

        val count = WorkspaceImporter.importArchive(zip, target)

        assertEquals(2, count)
        assertEquals("<html/>", File(target, "index.html").readText())
        assertEquals("png-data", File(target, "img/logo.png").readText())
    }

    @Test
    fun import_rejectsZipSlipEntries() {
        val zip = File(temporaryFolder.root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../escape.txt"))
            out.write("evil".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("safe.txt"))
            out.write("ok".toByteArray())
            out.closeEntry()
        }
        val target = temporaryFolder.newFolder("target")

        val count = WorkspaceImporter.importArchive(zip, target)

        // 穿越条目被跳过，安全条目导入
        assertEquals(1, count)
        assertFalse("穿越文件不应写出", File(temporaryFolder.root, "escape.txt").exists())
        assertTrue(File(target, "safe.txt").exists())
    }
}
