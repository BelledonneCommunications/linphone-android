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
        maven { // for com.github.chrisbanes:PhotoView
            url = uri("https://www.jitpack.io")
        }

        maven {
            name = "local linphone-sdk maven repository"
            url = uri("file://${providers.gradleProperty("LinphoneSdkBuildDir").get()}/maven_repository/")
            content {
                includeGroup ("org.linphone")
            }
        }

        maven {
            name = "linphone.org maven repository"
            url = uri("https://linphone.org/maven_repository")
            content {
                includeGroup ("org.linphone")
            }
        }
    }
}

rootProject.name = "Linphone"
include(":app")
