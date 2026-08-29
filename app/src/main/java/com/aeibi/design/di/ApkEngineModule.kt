package com.aeibi.design.di

import android.content.Context
import com.aeibi.design.apk.ApkPipeline
import com.aeibi.design.apk.DefaultPipeline
import com.aeibi.design.apk.engine.AndroidApkEngine
import com.aeibi.design.apk.engine.ApkEngine
import com.aeibi.design.apk.engine.ApksigSigner
import com.aeibi.design.apk.engine.RuntimeKeystoreProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * APK 引擎装配模块。
 *
 * 新增修改项：实现 [com.aeibi.design.apk.ApkOperation] → 加入 [DefaultPipeline.defaultOperations]。
 */
@Module
@InstallIn(SingletonComponent::class)
object ApkEngineModule {

    @Provides
    @Singleton
    fun provideApkEngine(@ApplicationContext context: Context): ApkEngine = AndroidApkEngine(context)

    @Provides
    @Singleton
    fun provideApkPipeline(@ApplicationContext context: Context, engine: ApkEngine): ApkPipeline {
        val keystoreFile = File(context.filesDir, TEST_KEYSTORE)
        val keyProvider = RuntimeKeystoreProvider(keystoreFile)
        val signer = ApksigSigner(keystoreFile.toPath(), TEST_ALIAS, TEST_PASS.toCharArray())
        return ApkPipeline(
            engine = engine,
            signing = { input, output -> signer.sign(input, output, keyProvider.provideKey()) },
            operations = DefaultPipeline.defaultOperations
        )
    }

    private const val TEST_KEYSTORE = "test-keystore.p12"
    private const val TEST_ALIAS = "vibe-design-test"
    private const val TEST_PASS = "vibetest"
}
