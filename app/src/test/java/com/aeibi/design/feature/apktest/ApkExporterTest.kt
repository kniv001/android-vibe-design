package com.aeibi.design.feature.apktest

import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkExporterTest {

    @Test
    fun `exports real shell apk without decoding`() {
        val tempDir = Files.createTempDirectory("apk-exporter-test").toFile()
        val frontend = File(tempDir, "frontend").apply { mkdirs() }
        File(frontend, "index.html").writeText("<html>exported</html>")
        File(frontend, "assets/app.js").apply {
            parentFile?.mkdirs()
            writeText("console.log('exported')")
        }
        val output = File(tempDir, "output.apk")
        val template = File("../shell/build/outputs/apk/debug/shell-debug.apk")

        ApkExporter(File(tempDir, "files"), File(tempDir, "cache")).export(
            ApkExportRequest(
                templateApk = template,
                outputApk = output,
                packageName = "com.vibetest.exported",
                appLabel = "导出测试",
                frontendDir = frontend,
                config = mapOf("entry" to "index.html"),
                versionCode = 42,
                versionName = "4.2"
            )
        )

        assertTrue(output.isFile)
        assertTrue(ApkVerifier.Builder(output).build().verify().isVerified)
        ApkModule.loadApkFile(output).use { module ->
            val manifest = module.androidManifest
            assertEquals("com.vibetest.exported", module.packageName)
            assertEquals("com.vibeshell.MainActivity", manifest.mainActivityClassName)
            assertEquals("导出测试", manifest.applicationLabelString)
            assertEquals(42, manifest.versionCode)
            assertEquals("4.2", manifest.versionName)
            assertTrue(
                manifest.usesPermissions.contains(
                    "com.vibetest.exported.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                )
            )

            val authorities = mutableListOf<String>()
            val attributes = manifest.recursiveAttributes()
            while (attributes.hasNext()) {
                val attribute = attributes.next()
                if (attribute.equalsNameId(AndroidManifest.ID_authorities)) {
                    attribute.valueAsString?.let(authorities::add)
                }
            }
            assertTrue(authorities.contains("com.vibetest.exported.androidx-startup"))
            assertFalse(authorities.any { it.contains("com.vibeshell") })

            assertEquals(
                "<html>exported</html>",
                module.getInputSource("assets/frontend_app/index.html").openStream()
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "{\"entry\":\"index.html\"}",
                module.getInputSource("assets/app_config.json").openStream()
                    .bufferedReader()
                    .use { it.readText() }
            )
        }
    }

    @Test
    fun packageScoped_attributesFollowButClassNamesPreserved() {
        val tempDir = Files.createTempDirectory("apk-exporter-scoped").toFile()
        val output = File(tempDir, "output.apk")
        val template = extractResourceTemplate("templates/package-scoped.apk", tempDir)

        ApkExporter(File(tempDir, "files"), File(tempDir, "cache")).export(
            ApkExportRequest(
                templateApk = template,
                outputApk = output,
                packageName = "com.vibetest.newpkg"
            )
        )

        ApkModule.loadApkFile(output).use { module ->
            assertEquals("com.vibetest.newpkg", module.packageName)
            // 最小模板的 resources.arsc 无 package 块，serializeToXml 不可用——遍历属性断言
            val values = mutableListOf<String>()
            val attributes = module.androidManifest.recursiveAttributes()
            while (attributes.hasNext()) {
                attributes.next().valueAsString?.let(values::add)
            }
            val joined = values.joinToString("\n")
            // 包名作用域属性跟随
            assertTrue("permission 未跟随: $joined", values.contains("com.vibetest.newpkg.permission.CUSTOM"))
            assertTrue("authorities 未跟随: $joined", values.contains("com.vibetest.newpkg.provider"))
            assertTrue("split 未跟随: $joined", values.contains("com.vibetest.newpkg.splita"))
            assertTrue("process 未跟随: $joined", values.contains("com.vibetest.newpkg.main"))
            // ${applicationId} 占位符 → 新包名
            assertTrue("占位符未替换: $joined", values.contains("com.vibetest.newpkg.dynamic"))
            // 类名保留（dex 未变）
            assertTrue("activity 类名被改: $joined", values.contains("com.vibetest.tpl.Main"))
            assertTrue("provider 类名被改: $joined", values.contains("com.vibetest.tpl.Provider"))
        }
    }

    private fun extractResourceTemplate(resource: String, dir: File): File {
        val target = File(dir, "template.apk")
        javaClass.getResourceAsStream("/$resource")!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }
}
