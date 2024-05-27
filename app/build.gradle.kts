import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kapt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.navigation)
    alias(libs.plugins.googleGmsServices)
    alias(libs.plugins.crashlytics)
}

val packageName = "org.linphone"
val useDifferentPackageNameForDebugBuild = false

val sdkPath = providers.gradleProperty("LinphoneSdkBuildDir").get()
val googleServices = File(projectDir.absolutePath + "/google-services.json")
val linphoneLibs = File("$sdkPath/libs/")
val linphoneDebugLibs = File("$sdkPath/libs-debug/")
val crashlyticsAvailable = googleServices.exists() && linphoneLibs.exists() && linphoneDebugLibs.exists()

var gitBranch = ByteArrayOutputStream()
var gitVersion = "6.0.0"

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
                "--count"
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

        gitVersion = if (gitCommitsCount.toString().trim().toInt() == 0) {
            gitVersionStream.toString().trim()
        } else {
            gitVersionStream.toString().trim() + "." + gitCommitsCount.toString()
                .trim() + "+" + gitCommitHash.toString().trim()
        }
        println("Git version: $gitVersion")
    } catch (e: Exception) {
        println("Git not found [$e], using $gitVersion")
    }
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
    compileSdk = 34

    defaultConfig {
        applicationId = packageName
        minSdk = 28
        targetSdk = 34
        versionCode = 60000
        versionName = "6.0.0"

        manifestPlaceholders["appAuthRedirectScheme"] = "org.linphone"
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "linphone-android-${variant.buildType.name}-$versionName.apk"
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

            if (crashlyticsAvailable) {
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = File("$sdkPath/libs-debug/").toString()
                }
            }
        }

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            resValue("string", "file_provider", "$packageName.fileprovider")
            resValue("string", "linphone_app_version", gitVersion.trim())
            resValue("string", "linphone_app_branch", gitBranch.toString().trim())
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

ktlint {
    android = true
    ignoreFailures = true
}
project.tasks.preBuild.dependsOn("ktlintFormat")

if (crashlyticsAvailable) {
    afterEvaluate {
        tasks.getByName("assembleDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug")
        )
        tasks.getByName("packageDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug")
        )
    }
}
