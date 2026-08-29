package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkIo
import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext

/**
 * 修改应用名：定位 strings.xml，替换 app_name。
 */
class AppLabelOperation : ApkOperation {

    override val name: String = "修改应用名"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val label = context.request.appLabel ?: return
        require(label.isNotBlank()) { "应用名不能为空" }

        val stringsPath = findStringsPath(context) ?: return
        val content = context.fileAccess.read(stringsPath)

        // 提取旧 app_name 值
        val oldLabel = Regex("""<string\s+name="app_name"\s*>([^<]*)</string>""")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?: return

        context.fileAccess.edit(
            stringsPath,
            "<string name=\"app_name\">$oldLabel</string>",
            "<string name=\"app_name\">$label</string>"
        )
        context.log("应用名: $label")
    }

    private fun findStringsPath(context: ApkOperationContext) = ApkIo.walk(context.layout.resRoot(context.decodedDir))
        .filter { it.fileName.toString() == "strings.xml" }
        .filter { ApkIo.readString(it).contains("app_name") }
        .firstOrNull()

    private companion object {
        const val ORDER = 20
    }
}
