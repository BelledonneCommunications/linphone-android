import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.gms.googleservices.GoogleServicesPlugin
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kapt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.navigation)
    alias(libs.plugins.crashlytics)
}

val packageName = "org.linphone"
val useDifferentPackageNameForDebugBuild = false

val sdkPath = providers.gradleProperty("LinphoneSdkBuildDir").get()
val googleServices = File(projectDir.absolutePath + "/google-services.json")
val linphoneLibs = File("$sdkPath/libs/")
val linphoneDebugLibs = File("$sdkPath/libs-debug/")
val firebaseCloudMessagingAvailable = googleServices.exists()
val crashlyticsAvailable = googleServices.exists() && linphoneLibs.exists() && linphoneDebugLibs.exists()

if (firebaseCloudMessagingAvailable) {
    println("google-services.json found, enabling CloudMessaging feature")
    apply<GoogleServicesPlugin>()
} else {
    println("google-services.json not found, disabling CloudMessaging feature")
}

var gitBranch = ByteArrayOutputStream()
var gitVersion = "6.0.3"

task("getGitVersion") {
    val gitVersionStream = ByteArrayOutputStream()
    val gitCommitsCount = ByteArrayOutputStream()
    val gitCommitHash = ByteArrayOutputStream()

    try {
        exec {
            commandLine("git", "describe", "--abbrev=0")
            standardOutput = gitVersionStream
        }
        exec {
            commandLine(
                "git",
                "rev-list",
                gitVersionStream.toString().trim() + "..HEAD",
                "--count",
            )
            standardOutput = gitCommitsCount
        }
        exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = gitCommitHash
        }
        exec {
            commandLine("git", "name-rev", "--name-only", "HEAD")
            standardOutput = gitBranch
        }

        gitVersion =
            if (gitCommitsCount.toString().trim().toInt() == 0) {
                gitVersionStream.toString().trim()
            } else {
                gitVersionStream.toString().trim() + "." +
                    gitCommitsCount.toString()
                        .trim() + "+" + gitCommitHash.toString().trim()
            }
        println("Git version: $gitVersion")
    } catch (e: Exception) {
        println("Git not found [$e], using $gitVersion")
    }
    project.version = gitVersion
}
project.tasks.preBuild.dependsOn("getGitVersion")

configurations {
    implementation { isCanBeResolved = true }
}
task("linphoneSdkSource") {
    doLast {
        configurations.implementation.get().incoming.resolutionResult.allComponents.forEach {
            if (it.id.displayName.contains("linphone-sdk-android")) {
                println("Linphone SDK used is ${it.moduleVersion?.version}")
            }
        }
    }
}
project.tasks.preBuild.dependsOn("linphoneSdkSource")

android {
    namespace = "org.linphone"
    compileSdk = 35

    defaultConfig {
        applicationId = packageName
        minSdk = 28
        targetSdk = 35
        versionCode = 600003 // 6.00.003
        versionName = "6.0.3"

        manifestPlaceholders["appAuthRedirectScheme"] = packageName

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "linphone-android-${variant.buildType.name}-${project.version}.apk"
            }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))

    signingConfigs {
        create("release") {
            val keyStorePath = keystoreProperties["storeFile"] as String
            val keyStore = project.file(keyStorePath)
            if (keyStore.exists()) {
                storeFile = keyStore
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                println("Signing config release is using keystore [$storeFile]")
            } else {
                println("Keystore [$storeFile] doesn't exists!")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (useDifferentPackageNameForDebugBuild) {
                applicationIdSuffix = ".debug"
            }
            isDebuggable = true
            isJniDebuggable = true

            if (useDifferentPackageNameForDebugBuild) {
                resValue("string", "file_provider", "$packageName.debug.fileprovider")
            } else {
                resValue("string", "file_provider", "$packageName.fileprovider")
            }
            resValue("string", "linphone_app_version", gitVersion.trim())
            resValue("string", "linphone_app_branch", gitBranch.toString().trim())
            resValue("string", "linphone_openid_callback_scheme", packageName)

            if (crashlyticsAvailable) {
                val path = File("$sdkPath/libs-debug/").toString()
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = path
                }
            }
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", crashlyticsAvailable.toString())
        }

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")

            resValue("string", "file_provider", "$packageName.fileprovider")
            resValue("string", "linphone_app_version", gitVersion.trim())
            resValue("string", "linphone_app_branch", gitBranch.toString().trim())
            resValue("string", "linphone_openid_callback_scheme", packageName)

            if (crashlyticsAvailable) {
                val path = File("$sdkPath/libs-debug/").toString()
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = path
                }
            }
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", crashlyticsAvailable.toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.annotations)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraint.layout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.telecom)
    implementation(libs.androidx.media)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.slidingpanelayout)
    implementation(libs.androidx.window)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.car)

    // https://github.com/google/flexbox-layout/blob/main/LICENSE Apache v2.0
    implementation(libs.google.flexbox)
    // https://github.com/material-components/material-components-android/blob/master/LICENSE Apache v2.0
    implementation(libs.google.material)
    // To be able to parse native crash tombstone and print them with SDK logs the next time the app will start
    implementation(libs.google.protobuf)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.messaging)
    implementation(libs.google.firebase.crashlytics)

    // https://github.com/coil-kt/coil/blob/main/LICENSE.txt Apache v2.0
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)
    // https://github.com/tommybuonomo/dotsindicator/blob/master/LICENSE Apache v2.0
    implementation(libs.dots.indicator)
    // https://github.com/Baseflow/PhotoView/blob/master/LICENSE Apache v2.0
    implementation(libs.photoview)
    // https://github.com/openid/AppAuth-Android/blob/master/LICENSE Apache v2.0
    implementation(libs.openid.appauth)

    implementation(libs.linphone)
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    android.set(true)
    ignoreFailures.set(true)
    additionalEditorconfig.set(
        mapOf(
            "max_line_length" to "120",
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_standard_function-signature" to "disabled",
            "ktlint_standard_no-blank-line-before-rbrace" to "disabled",
            "ktlint_standard_no-empty-class-body" to "disabled",
            "ktlint_standard_annotation-spacing" to "disabled",
            "ktlint_standard_class-signature" to "disabled",
            "ktlint_standard_function-expression-body" to "disabled",
            "ktlint_standard_function-type-modifier-spacing" to "disabled",
            "ktlint_standard_if-else-wrapping" to "disabled",
            "ktlint_standard_argument-list-wrapping" to "disabled",
            "ktlint_standard_trailing-comma-on-call-site" to "disabled",
            "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
            "ktlint_standard_no-empty-first-line-in-class-body" to "disabled",
            "ktlint_standard_no-empty-first-line-in-method-block" to "disabled",
            "ktlint_standard_no-trailing-spaces" to "disabled",
            "ktlint_standard_no-blank-line-in-list" to "disabled",
            "ktlint_standard_no-multi-spaces" to "disabled",
            "ktlint_standard_try-catch-finally-spacing" to "disabled",
            "ktlint_standard_block-comment-initial-star-alignment" to "disabled",
            "ktlint_standard_spacing-between-declarations-with-comments" to "disabled",
            "ktlint_standard_no-consecutive-comments" to "disabled",
            "ktlint_standard_multiline-expression-wrapping" to "disabled",
            "ktlint_standard_parameter-list-wrapping" to "disabled",
            "ktlint_standard_comment-wrapping" to "disabled",
            "ktlint_standard_discouraged-comment-location" to "disabled",
            "ktlint_standard_string-template-indent" to "disabled",
            "ktlint_standard_parameter-list-spacing" to "disabled",
            "ktlint_standard_statement-wrapping" to "disabled",
            "ktlint_standard_import-ordering" to "disabled",
            "ktlint_standard_paren-spacing" to "disabled",
            "ktlint_standard_curly-spacing" to "disabled",
            "ktlint_standard_indent" to "disabled",
        )
    )
}
project.tasks.preBuild.dependsOn("ktlintFormat")

if (crashlyticsAvailable) {
    afterEvaluate {
        tasks.getByName("assembleDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug"),
        )
        tasks.getByName("packageDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug"),
        )
        tasks.getByName("assembleRelease").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileRelease"),
        )
        tasks.getByName("packageRelease").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileRelease"),
        )
    }
}
