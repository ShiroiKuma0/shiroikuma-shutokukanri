import buildlogic.CiDirs
import buildlogic.CiUtils
import buildlogic.versioning.convertToVersionCode
import buildlogic.versioning.getAppName
import buildlogic.versioning.getAppVersion
import buildlogic.versioning.getAppVersionString
import buildlogic.versioning.getApplicationPackageName
import com.android.build.api.artifact.SingleArtifact
import ir.amirab.installer.InstallerTargetFormat
import ir.amirab.plugin.common_android.task.SignApkTask
import ir.amirab.plugin.common_android.task.androidEnableFileTypesGeneratorForManifest
import org.gradle.kotlin.dsl.registering
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id(Plugins.Android.application)
    id(MyPlugins.kotlinAndroid)
    id(MyPlugins.composeAndroid)
    id(Plugins.ksp)
    id(Plugins.Kotlin.serialization)
    id(Plugins.aboutLibraries)
    id(Plugins.aboutLibrariesAndroid)
}
// Fork versioning (see gradle.properties): versionName = "<VERSION_NAME>+<BUILD_NUMBER>",
// versionCode = upstream packed semver * 100 + BUILD_NUMBER (BUILD_NUMBER must stay <= 99).
val forkVersionName = getAppVersionString()
val forkBuildNumber = project.property("BUILD_NUMBER").toString().toInt()
val forkVersionCode = getAppVersion().convertToVersionCode() * 100 + forkBuildNumber

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    defaultConfig {
        minSdk = 26
        targetSdk = 36
        applicationId = project.property("APP_ID").toString()
        versionCode = forkVersionCode
        versionName = forkVersionName
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    compileSdk = 36
    namespace = "com.abdownloadmanager.android"
    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_short_name", "白い熊 ABDM - Debug")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.register("buildApk") {
    description = "Build the signed release APK, copy it to ~/tmp, and bump BUILD_NUMBER for next time."
    dependsOn("assembleRelease")
    doLast {
        val apkName = "shiroikuma-shutokukanri_${forkVersionName}_arm64-v8a.apk"
        val outputDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val targetDir = File(System.getProperty("user.home"), "tmp")
        targetDir.mkdirs()
        outputDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()?.let { apk ->
            val targetFile = File(targetDir, apkName)
            apk.copyTo(targetFile, overwrite = true)
            println("\u001b[1;36m>>> ${targetFile.absolutePath}\u001b[0m")
            println("\u001b[1;36m>>> versionCode $forkVersionCode\u001b[0m")
        } ?: throw GradleException("No APK found in $outputDir")

        // Auto-increment BUILD_NUMBER for the next build.
        val propsFile = rootProject.file("gradle.properties")
        propsFile.writeText(
            propsFile.readText().replace(
                "BUILD_NUMBER=$forkBuildNumber",
                "BUILD_NUMBER=${forkBuildNumber + 1}"
            )
        )
        println("\u001b[1;36m>>> BUILD_NUMBER bumped to ${forkBuildNumber + 1}\u001b[0m")
    }
}

dependencies {
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.decompose.jbCompose)
    implementation(libs.aboutLibraries.core)
    implementation(project(":shared:app"))
    ksp(libs.arrow.opticKsp)
}

androidEnableFileTypesGeneratorForManifest(
    targetActivityClass = ".pages.add.AddDownloadActivity",
    fileTypesFile = project.layout.projectDirectory.file("filetypes.txt")
)


// ======= begin of GitHub action stuff
val ciDir = CiUtils.getCiDir(project)
androidComponents.onVariants { variant ->
    tasks.register(
        "createReleaseSignedBinary${variant.name.uppercaseFirstChar()}",
        SignApkTask::class
    ) {
        inputDir.set(variant.artifacts.get(SingleArtifact.APK))
        outputDIr.set(project.layout.buildDirectory.dir("generatedSignedApks"))
        platformToolsVersion.set("36.1.0")
        keystoreUri.set(provider {
            getFromEnvOrProperties("ABDM_KEYSTORE_FILE")
        })
        keystorePassword.set(provider {
            getFromEnvOrProperties("ABDM_KEYSTORE_FILE_PASSWORD")
        })
        keyPassword.set(provider {
            getFromEnvOrProperties("ABDM_KEYSTORE_KEY_PASSWORD")
        })
        keyAlias.set(provider {
            getFromEnvOrProperties("ABDM_KEYSTORE_KEY_ALIAS")
        })
    }
}

val androidBinaries by tasks.registering {
    val signedApks = tasks.named("createReleaseSignedBinaryRelease")
        .map { task ->
            task.outputs.files.singleFile
        }
    inputs.dir(signedApks)
    outputs.dir(ciDir.binariesDir)
    doLast {
        // at the moment we only have one apk
        // if I decided to add multiple targets (arm64 x64 etc..)
        // ... I need to extract arch and use forEach instead of first
        val signedApk = signedApks.get().listFiles()
            .first { it.name.endsWith(".apk") }
        val outputFileName = CiUtils.getTargetFileName(
            getAppName(),
            getAppVersion(),
            InstallerTargetFormat.Apk,
            null,
        )
        CiUtils.copyAndHashToDestination(
            src = signedApk,
            destinationFolder = ciDir.binariesDir.get().asFile,
            name = outputFileName,
        )
    }
}

tasks.register(CiUtils.getCreateBinaryFolderForCiTaskName()) {
    dependsOn(androidBinaries)
}


private val localProperties by lazy {
    val file = project.rootProject.projectDir.resolve("local.properties")
    file.inputStream().use {
        Properties().apply { load(it) }
    }
}

fun getFromEnvOrProperties(key: String): String? {
    val string = (System.getenv(key)?.takeIf { it.isNotEmpty() }
        ?: localProperties.getProperty(key))
    return string
}
