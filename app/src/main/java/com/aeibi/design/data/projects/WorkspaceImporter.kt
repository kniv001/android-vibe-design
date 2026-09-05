package com.aeibi.design.data.projects

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 工作区导入——把 zip 解压到目标目录（zip-slip 防护：条目路径规范化后必须留在目标内）。
 * File 与 InputStream 双入口（SAF 场景：uri 流直接解压，不必落盘）。
 */
object WorkspaceImporter {

    /** 解压 zip 文件到目标目录，返回解压的文件条目数。 */
    fun importArchive(zipFile: File, targetDir: File): Int {
        if (!zipFile.isFile) throw IOException("Not a file: $zipFile")
        return zipFile.inputStream().use { importArchive(it, targetDir) }
    }

    /** 从输入流解压 zip 到目标目录，返回解压的文件条目数。 */
    fun importArchive(input: InputStream, targetDir: File): Int {
        targetDir.mkdirs()
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val resolved = safeResolve(targetDir, entry.name)
                if (resolved != null) {
                    if (entry.isDirectory) {
                        resolved.mkdirs()
                    } else {
                        resolved.parentFile?.mkdirs()
                        resolved.outputStream().use { out -> zip.copyTo(out) }
                        count++
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return count
    }

    /** zip-slip 防护：条目名规范化后必须留在 targetDir 内；危险条目返回 null（跳过）。 */
    private fun safeResolve(targetDir: File, entryName: String): File? {
        val normalized = File(targetDir, entryName).normalize()
        if (!normalized.toPath().startsWith(targetDir.toPath())) return null
        return normalized
    }
}
