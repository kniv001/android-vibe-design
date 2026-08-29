package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext

/**
 * 写入 assets/app_config.json（壳的运行时配置）。
 */
class ConfigJsonOperation : ApkOperation {

    override val name: String = "写入应用配置"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val config = context.request.config ?: return
        require(config.isNotEmpty()) { "config 不能为空" }

        val json = config.entries
            .joinToString(",\n  ") { (key, value) -> """  "$key": "${escape(value)}"""" }
        val path = context.layout.rootDir(context.decodedDir).resolve(ASSETS_DIR).resolve(CONFIG_FILE)

        context.fileAccess.write(path, "{\n$json\n}")
        context.log("配置: 写入 ${CONFIG_FILE}（${config.size} 项）")
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private companion object {
        const val ORDER = 40
        const val ASSETS_DIR = "assets"
        const val CONFIG_FILE = "app_config.json"
    }
}
