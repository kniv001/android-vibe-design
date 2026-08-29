package com.aeibi.design.apk

import java.nio.file.Files
import java.nio.file.Path

/**
 * 明文目录文件操作开口——操作层与文件系统之间的薄边界。
 *
 * 当前由 [DirectFileAccess] 直连实现；未来接入 Agent 文件工具层
 * （issue #25）时替换实现即可，操作层无需改动。
 */
interface FileAccess {

    /** 读取文本文件（UTF-8）。 */
    fun read(path: Path): String

    /** 写入文本文件（UTF-8，覆盖）。 */
    fun write(path: Path, content: String)

    /** 写入二进制文件（覆盖）。 */
    fun writeBytes(path: Path, bytes: ByteArray)

    /** 文本替换（首个匹配）。返回是否发生替换。 */
    fun edit(path: Path, oldText: String, newText: String): Boolean

    /** 删除文件或目录（递归）。 */
    fun delete(path: Path)

    /** 列出目录直接子项。 */
    fun list(path: Path): List<Path>
}

/** 默认实现：直接文件操作（ApkIo 兼容层）。 */
object DirectFileAccess : FileAccess {

    override fun read(path: Path): String = ApkIo.readString(path)

    override fun write(path: Path, content: String) {
        path.parent?.toFile()?.mkdirs()
        ApkIo.writeString(path, content)
    }

    override fun writeBytes(path: Path, bytes: ByteArray) {
        path.parent?.toFile()?.mkdirs()
        Files.write(path, bytes)
    }

    override fun edit(path: Path, oldText: String, newText: String): Boolean {
        val content = read(path)
        if (!content.contains(oldText)) return false
        write(path, content.replace(oldText, newText))
        return true
    }

    override fun delete(path: Path) {
        path.toFile().deleteRecursively()
    }

    override fun list(path: Path): List<Path> = ApkIo.list(path)
}
