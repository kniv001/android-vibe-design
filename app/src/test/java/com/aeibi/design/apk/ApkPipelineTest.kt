package com.aeibi.design.apk

import com.aeibi.design.apk.engine.ApkEngine
import com.aeibi.design.apk.engine.ApkLayout
import com.aeibi.design.apk.engine.BuildSummary
import com.aeibi.design.apk.model.ApkBuildRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkPipelineTest {

    private class FakeEngine : ApkEngine {

        val calls = mutableListOf<String>()

        override fun decode(sourceApk: Path, destDir: Path) {
            calls += "decode"
            Files.createDirectories(destDir)
            Files.writeString(destDir.resolve("AndroidManifest.xml"), "<manifest/>")
        }

        override fun build(decodedDir: Path, outApk: Path): BuildSummary {
            calls += "build"
            Files.writeString(outApk, "built")
            return BuildSummary(
                apkSizeBytes = 5,
                entryCount = 1,
                hasDex = true,
                dexCount = 1,
                hasFrontendAssets = false,
                frontendFileCount = 0
            )
        }
    }

    private class FakeLayout : ApkLayout {
        override fun manifestFile(decodedDir: Path): Path = decodedDir.resolve("AndroidManifest.xml")

        override fun resRoot(decodedDir: Path): Path = decodedDir.resolve("res")

        override fun rootDir(decodedDir: Path): Path = decodedDir.resolve("root")
    }

    private class LoggingOperation(private val log: (String) -> Unit) : ApkOperation {
        override val name: String = "测试操作"

        override val order: Int = 0

        override fun apply(context: ApkOperationContext) {
            log("操作执行")
        }
    }

    @Test
    fun `管线按 decode-操作-build-签名 顺序执行`() {
        val engine = FakeEngine()
        val signCalls = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val workDir = Files.createTempDirectory("pipeline")
        val template = workDir.resolve("shell.apk")
        Files.writeString(template, "template")
        val output = workDir.resolve("out.apk")
        val pipeline = ApkPipeline(
            engine = engine,
            signing = { input, out ->
                signCalls += "sign"
                Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING)
            },
            layout = FakeLayout(),
            operations = listOf(LoggingOperation(logs::add)),
            logger = BuildLogger { _, message -> logs.add(message) },
            workDir = workDir.resolve("work")
        )

        val result = pipeline.build(ApkBuildRequest(templateApk = template, outputApk = output))

        assertEquals(listOf("decode", "build"), engine.calls)
        assertEquals(listOf("sign"), signCalls)
        assertEquals(listOf("测试操作"), result.operationsExecuted)
        assertTrue(Files.exists(output))
        assertTrue(result.verification.passed)
    }

    @Test
    fun `操作按order排序执行`() {
        val engine = FakeEngine()
        val executed = mutableListOf<String>()
        val workDir = Files.createTempDirectory("pipeline-order")
        val template = workDir.resolve("shell.apk")
        Files.writeString(template, "template")
        val ops = listOf(
            object : ApkOperation {
                override val name: String = "后执行"

                override val order: Int = 20

                override fun apply(context: ApkOperationContext) {
                    executed += name
                }
            },
            object : ApkOperation {
                override val name: String = "先执行"

                override val order: Int = 10

                override fun apply(context: ApkOperationContext) {
                    executed += name
                }
            }
        )
        val pipeline = ApkPipeline(
            engine = engine,
            signing = { input, out -> Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING) },
            operations = ops,
            workDir = workDir.resolve("work")
        )

        pipeline.build(ApkBuildRequest(templateApk = template, outputApk = workDir.resolve("out.apk")))

        assertEquals(listOf("先执行", "后执行"), executed)
    }
}
