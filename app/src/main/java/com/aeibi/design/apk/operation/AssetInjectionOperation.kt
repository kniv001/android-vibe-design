package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext

/**
 * 注入前端产物：把静态项目目录（HTML/CSS/JS/图片）写入
 * assets/frontend_app/（壳运行时 WebView 的加载根目录）。
 */
class AssetInjectionOperation : ApkOperation {

    override val name: String = "注入前端产物"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val frontendDir = context.request.frontendDir ?: return
        require(frontendDir.toFile().isDirectory) { "前端产物目录不存在: $frontendDir" }

        val frontendRoot = context.layout.rootDir(context.decodedDir).resolve(ASSETS_DIR).resolve(FRONTEND_DIR)

        var fileCount = 0
        frontendDir.toFile().walkTopDown().forEach { source ->
            if (!source.isFile) return@forEach
            val relative = frontendDir.relativize(source.toPath()).toString().replace('\\', '/')
            context.fileAccess.writeBytes(frontendRoot.resolve(relative), source.readBytes())
            fileCount++
        }
        context.log("前端产物: 注入 $fileCount 个文件 → assets/frontend_app/")
    }

    private companion object {
        const val ORDER = 50
        const val ASSETS_DIR = "assets"
        const val FRONTEND_DIR = "frontend_app"
    }
}
