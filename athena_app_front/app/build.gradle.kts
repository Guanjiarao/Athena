plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.whu.software.athena"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.whu.software.athena"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }


    compileOptions {
        // 开启核心库脱糖 (注意前面有个 is，并且用 = 赋值)
        isCoreLibraryDesugaringEnabled = true

        // 确保使用 Java 8 版本 (使用 = 赋值)
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.extJunit)
    androidTestImplementation(libs.espressoCore)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20210307")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation("com.heytap.health:sdk:2.1.7")

    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:image-glide:4.6.2")

    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    implementation("com.github.CarGuo.GSYVideoPlayer:gsyVideoPlayer-java:v8.3.4-release-jitpack")
    implementation("com.github.CarGuo.GSYVideoPlayer:GSYVideoPlayer-exo2:v8.3.4-release-jitpack")
    implementation("com.github.CarGuo.GSYVideoPlayer:gsyVideoPlayer-arm64:v8.3.4-release-jitpack")
    implementation("com.github.CarGuo.GSYVideoPlayer:gsyVideoPlayer-armv7a:v8.3.4-release-jitpack")
    implementation("com.github.CarGuo.GSYVideoPlayer:gsyVideoPlayer-x86:v8.3.4-release-jitpack")
    implementation("com.github.CarGuo.GSYVideoPlayer:gsyVideoPlayer-x64:v8.3.4-release-jitpack")



    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

}
