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
}