import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val releaseKeystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use { load(it) }
    }
}
val releaseStoreFile = releaseKeystoreProperties.getProperty("storeFile")
    ?.let { path ->
        val configured = rootProject.file(path)
        when {
            configured.isFile -> configured
            else -> rootProject.file("keystore/${configured.name}").takeIf { it.isFile }
        }
    }
val hasReleaseSigning =
    releaseStoreFile != null &&
        !releaseKeystoreProperties.getProperty("storePassword").isNullOrBlank() &&
        !releaseKeystoreProperties.getProperty("keyAlias").isNullOrBlank() &&
        !releaseKeystoreProperties.getProperty("keyPassword").isNullOrBlank()
if (hasReleaseSigning) {
    logger.lifecycle("Release signing: ${releaseStoreFile!!.name} (Android/keystore)")
    logger.lifecycle("Debug uses the same keystore so Studio Run can overlay the daily install")
}

/**
 * 默认固定为线上 API 基址；若本地调试可在 `Android/local.properties` 设置 `ncm.api.base.url`（无末尾 `/`）覆盖。
 */
val ncmApiBaseUrl: String =
    localProperties.getProperty("ncm.api.base.url")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "http://120.27.244.170:3000"

android {
    namespace = "com.kite.zmusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kite.zmusic"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val escaped = ncmApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "NCM_API_BASE_URL", "\"$escaped\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 本机有正式密钥时，debug 也用同一把钥匙。
            // 开发者即日常用户：Studio Run 可覆盖安装，登录/队列/显示偏好不会因换签名被清掉。
            // 开源克隆无 keystore 时仍走默认 debug 签名。
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // 开源默认：无 keystore.properties 时用 debug 签名，克隆即可编译
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        jniLibs {
            // 这些预编译 .so 没有可剥离符号，AGP 默认 strip 会打警告。
            keepDebugSymbols.addAll(
                listOf(
                    "**/libandroidx.graphics.path.so",
                    "**/libimage_processing_util_jni.so",
                    "**/libsurface_util_jni.so",
                ),
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.haze)
    // Kyant Backdrop 1.0.6：真正的液体玻璃（lens 折射）。排除其 Compose 1.10，沿用工程 BOM。
    implementation(libs.backdrop) {
        exclude(group = "androidx.compose.ui")
        exclude(group = "androidx.compose.foundation")
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(libs.compose.material.icons.extended)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.security.crypto)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.tooling)
}

// ---------------------------------------------------------------------------
// Distribution: copy release APK/AAB into repo-level artifacts/android/
// (same top-level artifacts/ tree used by Windows Setup/MSI; gitignored)
// ---------------------------------------------------------------------------
val releaseArtifactsDir = rootProject.file("../artifacts/android")
val releaseVersionName = android.defaultConfig.versionName ?: "0.0"

tasks.register("publishReleaseToArtifacts") {
    group = "distribution"
    description =
        "Assemble release APK + AAB and copy them to ../artifacts/android/ (repo root)"
    dependsOn("assembleRelease", "bundleRelease")

    doLast {
        releaseArtifactsDir.mkdirs()
        copy {
            from(layout.buildDirectory.dir("outputs/apk/release"))
            include("*.apk")
            into(releaseArtifactsDir)
            rename { _ -> "ZMusic-$releaseVersionName-release.apk" }
        }
        copy {
            from(layout.buildDirectory.dir("outputs/bundle/release"))
            include("*.aab")
            into(releaseArtifactsDir)
            rename { _ -> "ZMusic-$releaseVersionName-release.aab" }
        }
        logger.lifecycle("Release artifacts → ${releaseArtifactsDir.canonicalPath}")
        releaseArtifactsDir.listFiles()?.forEach { logger.lifecycle("  ${it.name}") }
    }
}
