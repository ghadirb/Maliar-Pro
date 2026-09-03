import java.util.Properties
import java.io.FileInputStream

fun projectSetting(name: String): String =
    providers.gradleProperty(name).orNull ?: System.getenv(name).orEmpty()

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

// Dedicated Google Apps Script Web App for AI proxy requests (chat/STT/TTS). The real
// GAPGPT_API_KEY/LIARA_API_KEY never ship in the APK - they live only in this script's
// own Script Properties, server-side; the app only ever knows this URL, which is not a
// secret (it's just an endpoint, same as any other API base URL). Payment URLs remain
// separately configured in SubscriptionManager.kt.
val aiBackendUrl = projectSetting("AI_BACKEND_URL").ifBlank {
    "https://script.google.com/macros/s/AKfycbyknX6jghzFv6Ofm1t5MgU0reB2UpEht2j0cyEfJbDFOdCA2YlVD0R9cQVbyUjONGI/exec"
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

// Load keystore properties from file (local) or environment variables (CI)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    // Local build: load from keystore.properties file
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
} else {
    // CI build: load from environment variables (GitHub Secrets)
    keystoreProperties.setProperty("storePassword", System.getenv("KEYSTORE_STORE_PASSWORD") ?: "MaliarPro123!")
    keystoreProperties.setProperty("keyPassword", System.getenv("KEYSTORE_KEY_PASSWORD") ?: "MaliarPro123!")
    keystoreProperties.setProperty("keyAlias", System.getenv("KEYSTORE_KEY_ALIAS") ?: "maliar_pro_key")
    keystoreProperties.setProperty("storeFile", System.getenv("KEYSTORE_STORE_FILE") ?: "android_release_key.jks")
}

android {
    namespace = "com.maliar.pro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.maliar.pro"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.6"
        buildConfigField("String", "AI_BACKEND_URL", buildConfigString(aiBackendUrl))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "store"
    productFlavors {
        // NOTE: myket-billing-client's own AndroidManifest.xml (merged into every flavor's
        // manifest, since the dependency isn't flavor-scoped) references THREE
        // placeholders per Myket's own official "جاوا (Java)" integration docs -
        // marketApplicationId, marketBindAddress, and marketPermission. All three must be
        // set for EVERY flavor (even "direct", which never actually uses billing) or the
        // manifest merger fails with "requires a placeholder substitution but no value is
        // provided". Direct gets harmless placeholder values since it's never used there.
        create("direct") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"direct\"")
            buildConfigField("String", "IAB_PUBLIC_KEY", "\"\"")
            manifestPlaceholders["marketApplicationId"] = "com.maliar.pro"
            manifestPlaceholders["marketBindAddress"] = "com.maliar.pro.UNUSED_BILLING_BIND"
            manifestPlaceholders["marketPermission"] = "com.maliar.pro.permission.UNUSED_BILLING"
        }
        create("bazaar") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"bazaar\"")
            buildConfigField("String", "IAB_PUBLIC_KEY", buildConfigString(projectSetting("BAZAAR_IAB_PUBLIC_KEY")))
            manifestPlaceholders["marketApplicationId"] = "com.farsitel.bazaar"
            manifestPlaceholders["marketBindAddress"] = "ir.cafebazaar.pardakht.InAppBillingService.BIND"
            manifestPlaceholders["marketPermission"] = "com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR"
        }
        create("myket") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"myket\"")
            buildConfigField("String", "IAB_PUBLIC_KEY", buildConfigString(projectSetting("MYKET_IAB_PUBLIC_KEY")))
            manifestPlaceholders["marketApplicationId"] = "ir.mservices.market"
            manifestPlaceholders["marketBindAddress"] = "ir.mservices.market.InAppBillingService.BIND"
            manifestPlaceholders["marketPermission"] = "ir.mservices.market.BILLING"
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            storeFile = if (storeFilePath != null && storeFilePath.isNotEmpty()) {
                rootProject.file(storeFilePath)
            } else {
                null
            }
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // CardView
    implementation("androidx.cardview:cardview:1.0.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Work Manager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // OkHttp for network requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // The official Myket client is configured per product flavor for both Myket and
    // Cafe Bazaar, as documented by Myket. Each store receives its own signed APK/AAB.
    implementation("com.github.myketstore:myket-billing-client:1.19")

    // Charting for the professional financial reports screen (income/expense trend line).
    // Hosted on jitpack, which settings.gradle.kts already lists as a repository for the
    // Myket billing client above - no new repo needed.
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
