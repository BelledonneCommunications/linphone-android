pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup ("com.github.chrisbanes")
            }
        }

        val localSdk = File("${providers.gradleProperty("LinphoneSdkBuildDir").get()}/maven_repository/org/linphone/linphone-sdk-android/maven-metadata.xml")
        if (localSdk.exists()) {
            val localSdkPath = providers.gradleProperty("LinphoneSdkBuildDir").get()
            println("Using locally built SDK from maven repository at ${localSdkPath}/maven_repository/")
            maven {
                name = "local linphone-sdk maven repository"
                url = uri(
                    "file://${localSdkPath}/maven_repository/"
                )
                content {
                    includeGroup("com.linphone")
                }
            }
        } else {
            maven {
                println("Using CI built SDK from maven repository at https://download.linphone.org/maven_repository")
                name = "linphone.org maven repository"
                url = uri("https://download.linphone.org/maven_repository")
                content {
                    includeGroup("org.linphone")
                }
            }
        }
    }
}

rootProject.name = "NewMobionFS-V2"
include(":app")
