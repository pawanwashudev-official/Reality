import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

layout.buildDirectory.set(File(projectDir, "build_v4"))

// Load signing config from local.properties (NOT committed to Git)
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { stream ->
        localProperties.load(stream)
    }
}

android {
    namespace = "com.neubofy.reality"
    compileSdk = 36  // Android 16 for future compatibility

    defaultConfig {
        applicationId = "com.neubofy.reality"
        minSdk = 26
        targetSdk = 36  // Android 16
        versionCode = 30
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        setProperty("archivesBaseName", "Reality-v${versionName}")
        
        val workerUrl = localProperties.getProperty("WORKER_URL") ?: ""
        buildConfigField("String", "WORKER_URL", "\"$workerUrl\"")
        val aiUrl = localProperties.getProperty("AI_URL") ?: ""
        buildConfigField("String", "AI_URL", "\"$aiUrl\"")
        val notificationWorkerUrl = localProperties.getProperty("NOTIFICATION_WORKER_URL") ?: ""
        buildConfigField("String", "NOTIFICATION_WORKER_URL", "\"$notificationWorkerUrl\"")

        val buildTimestamp = System.currentTimeMillis()
        buildConfigField("Long", "BUILD_TIMESTAMP", "${buildTimestamp}L")
    }
    
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module" 
        }
    }

    splits {
        abi {
            isEnable = false

        }
    }

    signingConfigs {
        create("release") {
            // Read from local.properties (secure - not in Git)
            val keystoreFile = localProperties.getProperty("KEYSTORE_FILE")
            val keystorePass = localProperties.getProperty("KEYSTORE_PASSWORD")
            val keyAliasName = localProperties.getProperty("KEY_ALIAS")
            val keyPass = localProperties.getProperty("KEY_PASSWORD")
            
            if (keystoreFile != null && keystorePass != null) {
                val ksFile = File(keystoreFile)
                storeFile = if (ksFile.isAbsolute) ksFile else file("${rootProject.projectDir}/$keystoreFile")
                storePassword = keystorePass
                keyAlias = keyAliasName ?: "reality_key"
                keyPassword = keyPass ?: keystorePass
            }
        }
    }

    buildTypes {
        release {
            // R8/ProGuard enabled
            isMinifyEnabled = true
            isShrinkResources = true
            
            // Only use release signing if credentials are available
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig?.storeFile?.exists() == true) {
                signingConfig = releaseConfig
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        
        debug {
            // No suffix - same package for debug and release
            versionNameSuffix = "-DEBUG"
        }
    }
    
    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val timestamp = defaultConfig.buildConfigFields["BUILD_TIMESTAMP"]?.value?.replace("L", "") ?: ""
            output?.outputFileName = "Reality-v${versionName}-${timestamp}-${buildType.name}.apk"
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
        viewBinding = true
        buildConfig = true
    }
    
    lint {
        checkReleaseBuilds = true
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.gson)
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.13.3")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.mpandroidchart)
    implementation(libs.timerangepicker)
    
    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.airbnb.android:lottie:6.0.0")
    
    // QR Code Generation (lightweight, open-source)
    
    // Google Sign-In & Credential Manager
    
    // Google APIs Client (for Tasks, Drive, Docs)
    // Using latest available versions
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-tasks:v1-rev20210709-2.0.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230520-2.0.0")
    implementation("com.google.apis:google-api-services-docs:v1-rev20230929-2.0.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20231123-2.0.0")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20230815-2.0.0")
    
    // HTTP Client for Google APIs (Synchronized version)
    implementation("com.google.http-client:google-http-client-gson:1.45.1")
    implementation("com.google.http-client:google-http-client-android:1.45.1")

    // Coil for Image Loading
    implementation("io.coil-kt:coil:2.6.0")
    
    // Markdown Rendering
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:image-coil:4.6.2")
    // implementation("io.noties.markwon:image-coil:4.6.2") // Conflict with coil version often, skip for now unless requested
    
    // PDF Generation (iText7 for styled markdown rendering)
    // implementation("com.itextpdf:itext7-core:7.2.5") // Removed to avoid version conflict with html2pdf
    implementation("com.itextpdf:html2pdf:4.0.5")

    // HTML Parsing for Web Search
    implementation("org.jsoup:jsoup:1.17.2")
    
    // QR Scanning & Generation
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("com.google.zxing:core:3.5.3")
    
    // Professional App Updater (Open Source)
    implementation("io.github.azhon:appupdate:4.3.6")

    // Firebase Cloud Messaging & Analytics
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
}
