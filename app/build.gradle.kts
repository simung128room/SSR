import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        
        val geminiKey = project.providers.environmentVariable("GEMINI_API_KEY").orNull
            ?: run {
                val envFile = project.rootProject.file(".env")
                if (envFile.exists()) {
                    val props = Properties()
                    FileInputStream(envFile).use { props.load(it) }
                    props.getProperty("GEMINI_API_KEY") ?: ""
                } else {
                    ""
                }
            }
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")

        val googleWebClientId = project.providers.environmentVariable("GOOGLE_WEB_CLIENT_ID").orNull
            ?: run {
                val envFile = project.rootProject.file(".env")
                if (envFile.exists()) {
                    val props = Properties()
                    FileInputStream(envFile).use { props.load(it) }
                    props.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""
                } else {
                    ""
                }
            }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.1")

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
