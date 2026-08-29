package com.aeibi.design.apk.operation

import com.aeibi.design.apk.ApkOperation
import com.aeibi.design.apk.ApkOperationContext

/**
 * 修改包名（applicationId）。
 *
 * 改动范围（三类，缺一不可）：
 * 1. manifest 根元素的 package 属性
 * 2. permission / uses-permission 的 android:name（包名前缀声明，不改会与
 *    已安装的旧包冲突：INSTALL_FAILED_DUPLICATE_PERMISSION）
 * 3. provider 的 android:authorities（包名前缀）
 *
 * 组件类名（android:name 指向 dex 类）保持模板原样——dex 类未被修改，
 * 类名改了会导致运行时 ClassNotFoundException。
 */
class PackageNameOperation : ApkOperation {

    override val name: String = "修改包名"

    override val order: Int = ORDER

    override fun apply(context: ApkOperationContext) {
        val newPackage = context.request.packageName ?: return
        require(newPackage.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) {
            "包名格式不合法: $newPackage"
        }

        val manifestPath = context.layout.manifestFile(context.decodedDir)
        val content = context.fileAccess.read(manifestPath)

        // 1. 提取旧包名
        val oldPackage = Regex("""package="([^"]+)"""").find(content)
            ?.groupValues
            ?.get(1)
            ?: error("manifest 缺少 package 属性")

        // 2. 三类替换（package 属性 / 权限声明 / authorities）
        var updated = content
            .replace("package=\"$oldPackage\"", "package=\"$newPackage\"")
            .replace("<permission android:name=\"$oldPackage.", "<permission android:name=\"$newPackage.")
            .replace("<uses-permission android:name=\"$oldPackage.", "<uses-permission android:name=\"$newPackage.")
            .replace("android:authorities=\"$oldPackage.", "android:authorities=\"$newPackage.")

        context.fileAccess.write(manifestPath, updated)
        context.log("包名: $oldPackage → $newPackage（权限/authority 跟随，组件类名保持模板）")
    }

    private companion object {
        const val ORDER = 10
    }
}
