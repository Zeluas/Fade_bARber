plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.secrets.gradle)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.zejyv.azizul.uitm.fadebarber"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zejyv.azizul.uitm.fadebarber"
        minSdk = 28
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
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.appcompat)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.splashscreen)
    implementation(libs.swiperefreshlayout)
    implementation(libs.security.crypto)

    val cameraxVersion = "1.6.1"
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.ar.deepar)

    implementation(libs.generativeai)
    implementation(libs.guava)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}