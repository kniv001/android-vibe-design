package com.aeibi.design.apk

import com.aeibi.design.apk.engine.ApkLayout
import com.aeibi.design.apk.model.ApkBuildRequest
import java.nio.file.Path

/**
 * APK 结构修改操作接口——扩展点。
 *
 * 新增一类 APK 修改（如：增删权限、增删组件、改启动页）时，
 * 实现本接口并加入 [ApkPipeline] 的 operations 列表即可。
 */
interface ApkOperation {

    /** 操作的人类可读名称，用于构建日志与结果汇报。 */
    val name: String

    /** 执行顺序（越小越先执行）。 */
    val order: Int

    /** 对解码后的明文目录执行修改。 */
    fun apply(context: ApkOperationContext)
}

/**
 * 操作执行上下文。
 *
 * @param decodedDir 解码后的 APK 明文目录（引擎 [com.aeibi.design.apk.engine.ApkEngine] 的产物）
 * @param layout     解码产物布局（操作层经此访问 manifest/资源/root 文件）
 * @param fileAccess 文件操作开口（[FileAccess]——未来工具层的替换点）
 * @param request    本次构建请求
 * @param log        构建日志回调（进度/结果，供 UI 与"诚实报告"使用）
 */
class ApkOperationContext(
    val decodedDir: Path,
    val layout: ApkLayout,
    val fileAccess: FileAccess,
    val request: ApkBuildRequest,
    val log: (String) -> Unit
)
