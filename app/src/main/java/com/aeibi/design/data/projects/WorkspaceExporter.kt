package com.aeibi.design.data.projects

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 工作区导出——把目录递归打包为 zip（相对路径条目，无根前缀，导入方无需适配）。
 */
object WorkspaceExporter {

    fun exportDirectory(sourceDir: File, zipFile: File) {
        if (!sourceDir.isDirectory) throw IOException("Not a directory: $sourceDir")
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(zipFile.outputStream().buffered()).use { out ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(sourceDir).invariantSeparatorsPath
                out.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }
}
