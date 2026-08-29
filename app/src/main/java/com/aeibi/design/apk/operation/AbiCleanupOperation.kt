package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext

/**
 * 删除不需要的 ABI 原生库目录。
 */
class AbiCleanupOperation : ApkOperation {

    override val name: String = "清理多余 ABI"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val whitelist = context.request.abiWhitelist ?: return
        require(whitelist.isNotEmpty()) { "abiWhitelist 不能为空" }

        val libDir = context.layout.rootDir(context.decodedDir).resolve(LIB_DIR)
        val removed = context.fileAccess.list(libDir)
            .filter { it.fileName.toString() !in whitelist }
            .onEach { context.fileAccess.delete(it) }
            .size
        context.log("ABI: 保留 ${whitelist.joinToString()}，删除 $removed 个目录")
    }

    private companion object {
        const val ORDER = 60
        const val LIB_DIR = "lib"
    }
}
