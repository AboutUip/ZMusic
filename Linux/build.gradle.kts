import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

group = "com.kite.zmusic"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

fun xaiopJar(): java.io.File {
    val jar = file("libs/xaiop-0.15.1.jar")
    check(jar.isFile && jar.length() > 100_000L) {
        "Missing ${jar.invariantSeparatorsPath}. " +
            "Download https://github.com/AboutUip/XAIOP/releases/download/v0.15.1/xaiop-0.15.1.jar " +
            "into Linux/libs/ (or re-run Distribution/Linux/build-deb.sh)."
    }
    return jar
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation(files(xaiopJar()))
    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:1.8.2")
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    systemProperty("zmusic.test", "true")
}

compose.desktop {
    application {
        mainClass = "com.kite.zmusic.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "zmusic"
            packageVersion = "0.1.0"
            description = "ZMusic Linux — landscape music client"
            copyright = "GPL-2.0"
            vendor = "AboutUip"
            linux {
                debMaintainer = "ZMusic <noreply@github.com>"
                menuGroup = "AudioVideo;Audio;Player;"
                appRelease = "1"
                debPackageVersion = "0.1.0"
            }
        }
    }
}
