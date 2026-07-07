import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.ryosoftware.calls_blocker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ryosoftware.calls_blocker"

        minSdk = 30
        targetSdk = 37

        versionCode = 3
        versionName = "1.2"

        buildConfigField(
            "String",
            "TAG",
            "\"calls_blocker\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        resValues = true
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.hilt:hilt-common:1.4.0")
    implementation("androidx.hilt:hilt-work:1.4.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.34")
    implementation("com.google.errorprone:error_prone_annotations:2.26.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("com.google.dagger:hilt-android:2.60")
    ksp("com.google.dagger:hilt-compiler:2.60")
    ksp("androidx.hilt:hilt-compiler:1.4.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
