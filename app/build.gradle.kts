plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sasya.arogya"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sasya.arogya"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Build timestamp for version display
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "${versionCode}")
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    flavorDimensions += "server"
    productFlavors {
        create("gpu") {
            dimension = "server"
            applicationIdSuffix = ".gpu"
            versionNameSuffix = "-gpu"
            
            buildConfigField("String", "SERVER_URL_GPU", "\"http://engine-sasya-arogya.shailendra14k.in/\"")
            buildConfigField("String", "SERVER_URL_NON_GPU", "\"http://engine-sasya-arogya.apps.cluster-dg9gp.dg9gp.sandbox1039.opentlc.com/\"")
            buildConfigField("String", "DEFAULT_SERVER_TYPE", "\"GPU\"")
            buildConfigField("String", "APP_VARIANT", "\"GPU\"")
        }
        
        create("nongpu") {
            dimension = "server"
            applicationIdSuffix = ".nongpu"
            versionNameSuffix = "-nongpu"
            
            buildConfigField("String", "SERVER_URL_GPU", "\"http://engine-sasya-arogya.shailendra14k.in/\"")
            buildConfigField("String", "SERVER_URL_NON_GPU", "\"http://engine-sasya-arogya.apps.cluster-dg9gp.dg9gp.sandbox1039.opentlc.com/\"")
            buildConfigField("String", "DEFAULT_SERVER_TYPE", "\"NON_GPU\"")
            buildConfigField("String", "APP_VARIANT", "\"Non-GPU\"")
            
            resValue("string", "app_name", "Sasya Chikitsa (Non-GPU)")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
            // Use the same URLs as production flavors, but with DEBUG server type
            // This allows debug builds to connect to production servers when needed
            buildConfigField("String", "SERVER_URL_GPU", "\"http://engine-sasya-arogya.shailendra14k.in/\"")
            buildConfigField("String", "SERVER_URL_NON_GPU", "\"http://engine-sasya-arogya.apps.cluster-dg9gp.dg9gp.sandbox1039.opentlc.com/\"")
            buildConfigField("String", "DEFAULT_SERVER_TYPE", "\"NON_GPU\"") // Default to Non-GPU for debug
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = false
        buildConfig = true
    }
    buildToolsVersion = "34.0.0"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // FSM Module dependency

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // For JSON processing
    implementation("com.squareup.retrofit2:retrofit:2.11.0") // Latest stable version
    implementation("com.squareup.retrofit2:converter-gson:2.11.0") // Latest stable version
    // For handling streaming responses, especially Server-Sent Events (SSE)
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Latest stable version
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // For debugging

    // Additional UI Components for FSM integration
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.1")

    // JSON processing
    implementation("com.google.code.gson:gson:2.10.1")

    // Image handling
    implementation("com.github.bumptech.glide:glide:4.15.1")

    // Coroutines for FSM streaming
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
}

// Custom tasks for building release variants
tasks.register("buildGpuRelease") {
    group = "build"
    description = "Build release APK for GPU cluster (http://engine-sasya-chikitsa.apps.cluster-mqklc.mqklc.sandbox601.opentlc.com/)"
    dependsOn("assembleGpuRelease")
    
    doLast {
        val apkPath = file("$buildDir/outputs/apk/gpu/release")
        val apkFile = apkPath.listFiles()?.find { it.name.endsWith(".apk") }
        
        if (apkFile?.exists() == true) {
            println("✅ GPU Release APK built successfully:")
            println("   📱 File: ${apkFile.name}")
            println("   📂 Path: ${apkFile.absolutePath}")
            println("   🌐 Server: GPU Cluster (mqklc.sandbox601)")
            println("   📏 Size: ${String.format("%.2f", apkFile.length() / 1024.0 / 1024.0)} MB")
        } else {
            println("❌ GPU Release APK not found at: $apkPath")
        }
    }
}

tasks.register("buildNonGpuRelease") {
    group = "build"
    description = "Build release APK for Non-GPU cluster (http://engine-sasya-arogya.apps.cluster-dg9gp.dg9gp.sandbox1039.opentlc.com/)"
    dependsOn("assembleNongpuRelease")
    
    doLast {
        val apkPath = file("$buildDir/outputs/apk/nongpu/release")
        val apkFile = apkPath.listFiles()?.find { it.name.endsWith(".apk") }
        
        if (apkFile?.exists() == true) {
            println("✅ Non-GPU Release APK built successfully:")
            println("   📱 File: ${apkFile.name}")
            println("   📂 Path: ${apkFile.absolutePath}")
            println("   🌐 Server: Non-GPU Cluster (dg9gp.sandbox1039)")
            println("   📏 Size: ${String.format("%.2f", apkFile.length() / 1024.0 / 1024.0)} MB")
        } else {
            println("❌ Non-GPU Release APK not found at: $apkPath")
        }
    }
}

tasks.register("buildAllReleaseVariants") {
    group = "build"
    description = "Build both GPU and Non-GPU release APKs"
    dependsOn("buildGpuRelease", "buildNonGpuRelease")
    
    doLast {
        println("\n🚀 Both release variants built successfully!")
        println("📦 GPU APK: Configured for GPU cluster processing")
        println("📦 Non-GPU APK: Configured for standard cluster processing")
        println("\n📋 Next steps:")
        println("   1. Test both APKs on target devices")
        println("   2. Verify server connectivity for each variant")
        println("   3. Deploy to respective environments")
        println("\n🔗 Server URLs:")
        println("   GPU:     http://engine-sasya-chikitsa.apps.cluster-mqklc.mqklc.sandbox601.opentlc.com/")
        println("   Non-GPU: http://engine-sasya-arogya.apps.cluster-dg9gp.dg9gp.sandbox1039.opentlc.com/")
    }
}

tasks.register<Copy>("copyReleasesToDistribution") {
    group = "distribution"
    description = "Copy release APKs to releases/ directory with descriptive names"
    dependsOn("buildAllReleaseVariants")
    
    doLast {
        val releasesDir = file("${project.rootDir}/releases")
        releasesDir.mkdirs()
        
        // Copy GPU APK
        val gpuApkPath = file("$buildDir/outputs/apk/gpu/release")
        val gpuApkFile = gpuApkPath.listFiles()?.find { it.name.endsWith(".apk") }
        if (gpuApkFile?.exists() == true) {
            val gpuDestName = "sasya-chikitsa-gpu-cluster-v${android.defaultConfig.versionName}.apk"
            gpuApkFile.copyTo(File(releasesDir, gpuDestName), overwrite = true)
            println("✅ GPU APK copied to: releases/$gpuDestName")
        }
        
        // Copy Non-GPU APK
        val nonGpuApkPath = file("$buildDir/outputs/apk/nongpu/release")
        val nonGpuApkFile = nonGpuApkPath.listFiles()?.find { it.name.endsWith(".apk") }
        if (nonGpuApkFile?.exists() == true) {
            val nonGpuDestName = "sasya-chikitsa-nongpu-cluster-v${android.defaultConfig.versionName}.apk"
            nonGpuApkFile.copyTo(File(releasesDir, nonGpuDestName), overwrite = true)
            println("✅ Non-GPU APK copied to: releases/$nonGpuDestName")
        }
        
        println("\n📁 All release APKs are now available in the releases/ directory")
    }
}