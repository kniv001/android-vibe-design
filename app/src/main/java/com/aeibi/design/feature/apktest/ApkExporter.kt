package com.aeibi.design.feature.apktest

import android.content.Context
import android.content.res.AssetManager
import com.android.apksig.ApkSigner as AndroidApkSigner
import com.android.apksig.ApkVerifier as AndroidApkVerifier
import com.reandroid.apk.AndroidFrameworks
import com.reandroid.apk.ApkModule
import com.reandroid.apk.FrameworkApk
import com.reandroid.apk.framework.FrameworkManager
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ByteInputSource
import com.reandroid.archive.FileInputSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.x509.X509V3CertificateGenerator

data class ApkExportRequest(
    val templateApk: File,
    val outputApk: File,
    val packageName: String? = null,
    val appLabel: String? = null,
    val iconFile: File? = null,
    val frontendDir: File? = null,
    val config: Map<String, String>? = null,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val abiWhitelist: Set<String>? = null
)

/** Directly edits the binary APK with ARSCLib, then aligns, signs, and verifies it. */
@Singleton
class ApkExporter internal constructor(
    private val filesDir: File,
    private val cacheDir: File,
    assets: AssetManager? = null
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.filesDir, context.cacheDir, context.assets)

    init {
        assets?.let {
            AndroidFrameworks.setFrameworkManager(AssetFrameworkManager(it, File(cacheDir, "frameworks")))
        }
    }

    fun export(request: ApkExportRequest, log: (String) -> Unit = {}): File {
        validate(request)
        val workDir = File(cacheDir, "apk-export-${UUID.randomUUID()}").apply { mkdirs() }
        val unsignedApk = File(workDir, "unsigned.apk")
        val signedApk = File(workDir, "signed.apk")

        try {
            log("Loading template")
            ApkModule.loadApkFile(request.templateApk).use { module ->
                updateManifest(module, request, log)
                replaceFrontend(module, request.frontendDir, log)
                replaceConfig(module, request.config, log)
                filterAbis(module, request.abiWhitelist, log)
                replaceIcon(module, request.iconFile, log)
                log("Writing APK")
                module.writeApk(unsignedApk)
            }

            log("Signing APK")
            sign(unsignedApk, signedApk)
            verify(signedApk, request)
            request.outputApk.parentFile?.mkdirs()
            signedApk.copyTo(request.outputApk, overwrite = true)
            log("Export complete")
            return request.outputApk
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun validate(request: ApkExportRequest) {
        require(request.templateApk.isFile) { "Template APK does not exist: ${request.templateApk}" }
        request.packageName?.let { packageName ->
            require(packageName.matches(PACKAGE_NAME_REGEX)) { "Invalid package name: $packageName" }
        }
        request.appLabel?.let { require(it.isNotBlank()) { "App label must not be blank" } }
        request.iconFile?.let { require(it.isFile) { "Icon file does not exist: $it" } }
        request.frontendDir?.let { require(it.isDirectory) { "Frontend output directory does not exist: $it" } }
        request.versionCode?.let { require(it > 0) { "versionCode must be greater than 0" } }
        request.versionName?.let { require(it.isNotBlank()) { "versionName must not be blank" } }
        request.abiWhitelist?.let { require(it.isNotEmpty()) { "abiWhitelist must not be empty" } }
    }

    private fun updateManifest(module: ApkModule, request: ApkExportRequest, log: (String) -> Unit) {
        val manifest = module.androidManifest ?: error("Template is missing AndroidManifest.xml")
        request.packageName?.let { newPackage ->
            val oldPackage = module.packageName ?: error("Template manifest does not define a package")
            val applicationClass = manifest.applicationClassName
            val mainActivityClass = manifest.mainActivityClassName

            module.setPackageName(newPackage)
            applicationClass?.let(manifest::setApplicationClassName)
            mainActivityClass?.let(manifest::setMainActivityClassName)
            replacePackageScopedAttributes(manifest, oldPackage, newPackage)
            log("Package name: $oldPackage → $newPackage")
        }
        request.appLabel?.let {
            manifest.setApplicationLabel(it)
            log("App label: $it")
        }
        request.versionCode?.let {
            manifest.setVersionCode(it)
            log("versionCode: $it")
        }
        request.versionName?.let {
            manifest.setVersionName(it)
            log("versionName: $it")
        }
    }

    private fun replacePackageScopedAttributes(
        manifest: com.reandroid.arsc.chunk.xml.AndroidManifestBlock,
        oldPackage: String,
        newPackage: String
    ) {
        val attributes = manifest.recursiveAttributes()
        while (attributes.hasNext()) {
            val attribute = attributes.next()
            val parentTag = attribute.parentElement?.name
            val packageScopedName =
                attribute.equalsNameId(AndroidManifest.ID_name) &&
                    parentTag in setOf("permission", "uses-permission")
            val packageScopedAuthority = attribute.equalsNameId(AndroidManifest.ID_authorities)
            // 包名作用域的按名匹配属性（ARSCLib 无对应 ID 常量，按属性名判断）
            val packageScopedByName = attribute.name in PACKAGE_SCOPED_ATTRIBUTE_NAMES
            if (!packageScopedName && !packageScopedAuthority && !packageScopedByName) continue

            val value = attribute.valueAsString ?: continue
            when {
                value.contains(oldPackage) -> {
                    attribute.setValueAsString(value.replace(oldPackage, newPackage))
                }
                value.contains(PACKAGE_PLACEHOLDER) -> {
                    // 库模板的 ${applicationId} 占位符 → 新包名
                    attribute.setValueAsString(value.replace(PACKAGE_PLACEHOLDER, newPackage))
                }
            }
        }
    }

    private fun replaceFrontend(module: ApkModule, frontendDir: File?, log: (String) -> Unit) {
        if (frontendDir == null) return
        module.zipEntryMap.removeIf { it.alias.startsWith(FRONTEND_PREFIX) }
        var count = 0
        frontendDir.walkTopDown().filter(File::isFile).forEach { source ->
            val relative = source.relativeTo(frontendDir).invariantSeparatorsPath
            module.add(FileInputSource(source, "$FRONTEND_PREFIX$relative"))
            count++
        }
        log("Frontend assets: $count files")
    }

    private fun replaceConfig(module: ApkModule, config: Map<String, String>?, log: (String) -> Unit) {
        if (config == null) return
        val bytes = Json.encodeToString(config).encodeToByteArray()
        module.add(ByteInputSource(bytes, CONFIG_PATH))
        log("App config: ${config.size} entries")
    }

    private fun filterAbis(module: ApkModule, whitelist: Set<String>?, log: (String) -> Unit) {
        if (whitelist == null) return
        var removed = 0
        module.zipEntryMap.removeIf { source ->
            val path = source.alias
            val abi = path.removePrefix(LIB_PREFIX).substringBefore('/')
            val remove = path.startsWith(LIB_PREFIX) && abi !in whitelist
            if (remove) removed++
            remove
        }
        log("ABI: kept ${whitelist.joinToString()}, removed $removed files")
    }

    private fun replaceIcon(module: ApkModule, iconFile: File?, log: (String) -> Unit) {
        if (iconFile == null) return
        val targets = module.zipEntryMap.listInputSources().filter { source ->
            val path = source.alias
            val name = path.substringAfterLast('/').substringBeforeLast('.')
            val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            path.startsWith(MIPMAP_PREFIX) &&
                name in LAUNCHER_ICON_NAMES &&
                extension in BITMAP_EXTENSIONS
        }
        targets.forEach { current ->
            val replacement = FileInputSource(iconFile, current.alias).apply { copyAttributes(current) }
            module.add(replacement)
        }
        log("App icon: replaced ${targets.size} entries")
    }

    private fun sign(input: File, output: File) {
        val entry = signingEntry()
        val signerConfig = AndroidApkSigner.SignerConfig.Builder(
            KEY_ALIAS,
            entry.privateKey,
            listOf(entry.certificate as X509Certificate)
        ).build()
        AndroidApkSigner.Builder(listOf(signerConfig))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setAlignFileSize(true)
            .build()
            .sign()
    }

    @Synchronized
    private fun signingEntry(): KeyStore.PrivateKeyEntry {
        val keyStoreFile = File(filesDir, KEYSTORE_FILE)
        val password = KEY_PASSWORD.toCharArray()
        if (!keyStoreFile.exists()) {
            keyStoreFile.parentFile?.mkdirs()
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null, password) }
            keyStore.setKeyEntry(KEY_ALIAS, keyPair.private, password, arrayOf(selfSignedCertificate(keyPair)))
            keyStoreFile.outputStream().use { keyStore.store(it, password) }
        }

        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
            keyStoreFile.inputStream().use { load(it, password) }
        }
        return keyStore.getEntry(KEY_ALIAS, KeyStore.PasswordProtection(password)) as? KeyStore.PrivateKeyEntry
            ?: error("APK signing key is unavailable")
    }

    @Suppress("DEPRECATION")
    private fun selfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val now = Instant.now()
        return X509V3CertificateGenerator().apply {
            setSerialNumber(BigInteger(160, SecureRandom()))
            setSubjectDN(javax.security.auth.x500.X500Principal("CN=Vibe Design"))
            setIssuerDN(javax.security.auth.x500.X500Principal("CN=Vibe Design"))
            setNotBefore(Date.from(now))
            setNotAfter(Date.from(now.plus(3650, ChronoUnit.DAYS)))
            setPublicKey(keyPair.public)
            setSignatureAlgorithm("SHA256withRSA")
        }.generate(keyPair.private)
    }

    private fun verify(apk: File, request: ApkExportRequest) {
        val signature = AndroidApkVerifier.Builder(apk).build().verify()
        require(signature.isVerified) { "APK signature verification failed: ${signature.errors.joinToString()}" }
        ApkModule.loadApkFile(apk).use { module ->
            request.packageName?.let { expected ->
                require(module.packageName == expected) { "Exported package name mismatch: ${module.packageName}" }
            }
            require(module.zipEntryMap.listInputSources().any { it.alias.endsWith(".dex") }) {
                "Exported APK is missing dex files"
            }
            request.frontendDir?.let { dir ->
                require(module.zipEntryMap.listInputSources().any { it.alias.startsWith(FRONTEND_PREFIX) }) {
                    "Exported APK is missing frontend assets"
                }
            }
            request.config?.let {
                require(module.zipEntryMap.listInputSources().any { source -> source.alias == CONFIG_PATH }) {
                    "Exported APK is missing app config"
                }
            }
        }
    }

    private class AssetFrameworkManager(private val assets: AssetManager, private val frameworkCacheDir: File) :
        FrameworkManager() {

        override fun get(version: Int): FrameworkApk? = if (version == FRAMEWORK_VERSION) load() else null

        override fun getBestMatch(version: Int): FrameworkApk = load()

        override fun getNearestVersion(version: Int): Int = FRAMEWORK_VERSION

        override fun getLatestVersion(): Int = FRAMEWORK_VERSION

        override fun getLatest(): FrameworkApk = load()

        private fun load(): FrameworkApk {
            val frameworkApk = File(frameworkCacheDir, "android-$FRAMEWORK_VERSION.apk")
            if (!frameworkApk.exists()) {
                frameworkCacheDir.mkdirs()
                assets.open(FRAMEWORK_ASSET).use { input ->
                    frameworkApk.outputStream().use(input::copyTo)
                }
            }
            return FrameworkApk.loadApkFile(frameworkApk).also(::setCurrent)
        }
    }

    private companion object {
        val PACKAGE_NAME_REGEX = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
        const val FRONTEND_PREFIX = "assets/frontend_app/"
        const val CONFIG_PATH = "assets/app_config.json"
        const val LIB_PREFIX = "lib/"
        const val MIPMAP_PREFIX = "res/mipmap"
        val PACKAGE_SCOPED_ATTRIBUTE_NAMES = setOf("split", "taskAffinity", "process", "targetPackage")
        const val PACKAGE_PLACEHOLDER = "\${applicationId}"
        val LAUNCHER_ICON_NAMES = setOf("ic_launcher", "ic_launcher_round")
        val BITMAP_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg")
        const val KEYSTORE_FILE = "apk-export-key.p12"
        const val KEYSTORE_TYPE = "PKCS12"
        const val KEY_ALIAS = "vibe-design"
        const val KEY_PASSWORD = "vibe-design"
        const val FRAMEWORK_VERSION = 36
        const val FRAMEWORK_ASSET = "frameworks/android/android-36.apk"
    }
}
