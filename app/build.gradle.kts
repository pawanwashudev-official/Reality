import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
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
    compileSdk = 35  // Android 15 for future compatibility

    defaultConfig {
        applicationId = "com.neubofy.reality"
        minSdk = 26
        targetSdk = 35  // Android 15
        versionCode = 12
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        setProperty("archivesBaseName", "Reality-v${versionName}")
        
        // Expose WEB_CLIENT_ID from local.properties to BuildConfig
        val webClientId = localProperties.getProperty("WEB_CLIENT_ID") ?: ""
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
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
                storeFile = file("${rootProject.projectDir}/$keystoreFile")
                storePassword = keystorePass
                keyAlias = keyAliasName ?: "reality_key"
                keyPassword = keyPass ?: keystorePass
            }
        }
    }

    buildTypes {
        release {
            // Enable R8/ProGuard for smaller APK and better performance
            // DISABLED for debugging crashes
            isMinifyEnabled = false
            isShrinkResources = false
            
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
            output?.outputFileName = "Reality-v${versionName}-${buildType.name}.apk"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.mpandroidchart)
    implementation(libs.timerangepicker)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.airbnb.android:lottie:6.0.0")
    
    // QR Code Generation (lightweight, open-source)
    implementation("com.google.zxing:core:3.5.2")
    
    // Google Sign-In & Credential Manager
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    
    // Google APIs Client (for Tasks, Drive, Docs)
    // Using latest available versions
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-tasks:v1-rev20210709-2.0.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230520-2.0.0")
    implementation("com.google.apis:google-api-services-docs:v1-rev20230929-2.0.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20231123-2.0.0")
    
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
    implementation("com.itextpdf:itext7-core:7.2.5")
    implementation("com.itextpdf:html2pdf:4.0.5")
}