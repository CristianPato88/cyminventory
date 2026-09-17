plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
}

android {
    namespace = "com.cym.inventory"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cym.inventory"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            configure<com.google.firebase.appdistribution.gradle.AppDistributionExtension> {
                releaseNotes = "Nueva versión de prueba de CyMInventory."
                testers = "cristianpatosanchez88@gmail.com, meugeniaalhambralara@gmail.com"
            }
        }
    }
}


dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val firebaseBom = platform("com.google.firebase:firebase-bom:34.19.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}

