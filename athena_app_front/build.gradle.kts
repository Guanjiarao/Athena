// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}

// 顶级 build.gradle.kts
allprojects {
    repositories {
        mavenCentral()

        maven { url = uri("https://jitpack.io") }

        // HeyTap 健康正式版仓库
        maven {
            url = uri("https://maven.columbus.heytapmobi.com/repository/heytap-health-releases/")
            isAllowInsecureProtocol = true // 允许非安全协议（如果仓库需要）

            // 🌟 封条 1：禁止在这个仓库搜索 GSY 播放器
            content {
                excludeGroup("com.github.CarGuo.GSYVideoPlayer")
            }

            credentials {
                username = "healthUser"
                password = "8174a9eac1264495b593a9d5ab221491"
            }
        }

        // HeyTap 健康快照版仓库
        maven {
            url = uri("https://maven.columbus.heytapmobi.com/repository/heytap-health-snapshots/")
            isAllowInsecureProtocol = true

            // 🌟 封条 2：禁止在这个仓库搜索 GSY 播放器
            content {
                excludeGroup("com.github.CarGuo.GSYVideoPlayer")
            }

            credentials {
                username = "healthUser"
                password = "8174a9eac1264495b593a9d5ab221491"
            }
        }
    }
}