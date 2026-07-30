pluginManagement {
    repositories {
        google() {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // 🌟 JitPack 仓库已经就位
        maven { url = uri("https://jitpack.io") }

        maven {
            url = uri("https://maven.columbus.heytapmobi.com/repository/heytap-health-releases/")
            isAllowInsecureProtocol = true

            // 🌟 终极封条 1：明确禁止在这个仓库里找 GSY 播放器相关的开源库
            content {
                excludeGroup("com.github.CarGuo.GSYVideoPlayer")
                excludeGroupByRegex("com\\.github\\..*")
                excludeGroupByRegex("com\\.shuyu\\..*")
            }

            credentials {
                username = "healthUser"
                password = providers.gradleProperty("heytapHealthMavenPassword")
                    .orElse(providers.environmentVariable("HEYTAP_HEALTH_MAVEN_PASSWORD"))
                    .orElse("")
                    .get()
            }
        }

        maven {
            url = uri("https://maven.columbus.heytapmobi.com/repository/heytap-health-snapshots/")
            isAllowInsecureProtocol = true

            // 🌟 终极封条 2：明确禁止在这个仓库里找 GSY 播放器相关的开源库
            content {
                excludeGroup("com.github.CarGuo.GSYVideoPlayer")
                excludeGroupByRegex("com\\.github\\..*")
                excludeGroupByRegex("com\\.shuyu\\..*")
            }

            credentials {
                username = "healthUser"
                password = providers.gradleProperty("heytapHealthMavenPassword")
                    .orElse(providers.environmentVariable("HEYTAP_HEALTH_MAVEN_PASSWORD"))
                    .orElse("")
                    .get()
            }
        }
    }
}

rootProject.name = "Athena"
include(":app")
