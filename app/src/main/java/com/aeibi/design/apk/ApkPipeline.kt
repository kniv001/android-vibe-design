package com.aeibi.design.apk

import com.aeibi.design.apk.engine.ApkEditorLayout
import com.aeibi.design.apk.engine.ApkEngine
import com.aeibi.design.apk.engine.ApkLayout
import com.aeibi.design.apk.model.ApkBuildRequest
import com.aeibi.design.apk.model.ApkBuildResult
import com.aeibi.design.apk.verify.ApkVerifier
import com.aeibi.design.apk.verify.VerificationReport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * APK 构建管线：decode → 明文操作 → build → 签名 → verify。
 *
 * 签名以闭包注入（`signing`），由装配方组合"密钥获取 + 签名"——
 * 管线不关心密钥来源与签名实现。
 */
class ApkPipeline(
    private val engine: ApkEngine,
    private val signing: (input: Path, output: Path) -> Unit,
    private val layout: ApkLayout = ApkEditorLayout(),
    private val operations: List<ApkOperation> = emptyList(),
    private val verifier: ApkVerifier? = null,
    private val logger: BuildLogger = PrintBuildLogger,
    private val workDir: Path = ApkIo.createTempDir("apk-build")
) {

    /** 使用构造器注入的 logger 构建。 */
    fun build(request: ApkBuildRequest): ApkBuildResult = build(request, logger)

    /** 使用指定 logger 构建（调用方可按需提供日志消费，如临时调试 UI）。 */
    fun build(request: ApkBuildRequest, logger: BuildLogger): ApkBuildResult {
        require(Files.exists(request.templateApk)) { "模板 APK 不存在: ${request.templateApk}" }
        val startedAt = System.currentTimeMillis()
        val executed = mutableListOf<String>()
        val decodedDir = workDir.resolve("decoded")

        try {
            logger.log(BuildStage.DECODE, "解码模板: ${request.templateApk.fileName}")
            engine.decode(request.templateApk, decodedDir)

            operations.sortedBy(ApkOperation::order).forEach { operation ->
                val context = ApkOperationContext(
                    decodedDir = decodedDir,
                    layout = layout,
                    fileAccess = DirectFileAccess,
                    request = request,
                    log = { logger.log(BuildStage.OPERATION, "$operation.name: $it") }
                )
                logger.log(BuildStage.OPERATION, "执行: ${operation.name}")
                operation.apply(context)
                executed += operation.name
            }

            val rebuilt = workDir.resolve("rebuilt.apk")
            logger.log(BuildStage.BUILD, "重打包")
            val buildSummary = engine.build(decodedDir, rebuilt)

            val signed = workDir.resolve("signed.apk")
            logger.log(BuildStage.SIGN, "签名")
            signing(rebuilt, signed)

            val verification = verifier?.let {
                logger.log(BuildStage.VERIFY, "验证")
                it.verify(signed)
            } ?: VerificationReport.passed()

            Files.copy(signed, request.outputApk, StandardCopyOption.REPLACE_EXISTING)
            logger.log(BuildStage.COMPLETE, "完成: ${request.outputApk}")
            return ApkBuildResult(
                outputApk = request.outputApk,
                operationsExecuted = executed,
                buildSummary = buildSummary,
                verification = verification,
                durationMs = System.currentTimeMillis() - startedAt
            )
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }
}
