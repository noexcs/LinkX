import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            // Dependencies available on Android
            install("pandas")
            // lxml removed: requires libxml2.so / libxslt.so not bundled by Chaquopy.
            // Use html.parser (Python stdlib) instead, patched in _chaquopy_patch.py.
            install("beautifulsoup4")
            install("soupsieve")  // required by bs4 4.14.x for CSS class matching
            install("html5lib")
            install("webencodings")  // html5lib dependency
            install("xlrd")
            install("openpyxl")
            install("chaquopy-openblas")
            install("chaquopy-libgfortran")
            install("numpy")
            install("tqdm")
            install("requests")
            install("certifi")
            install("charset-normalizer")
            install("idna")
            install("urllib3")
            install("tabulate")
            install("decorator")
            install("python-dateutil")
            install("pytz")
            install("six")
            install("typing_extensions")
            install("src/main/python/packages/py_mini_racer_stub")
            // Stubs for packages without Android wheels
            install("src/main/python/packages/aiohttp_stub")
            install("src/main/python/packages/curl_cffi_stub")
            install("src/main/python/packages/jsonpath_stub")
            // akshare without dependency resolution
            options("--no-deps")
            install("akshare")
        }
    }
}

android {
    namespace = "com.noexcs.indolent"
    base { archivesName = "LinkX" }

    // Load keystore properties
    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("key.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    compileSdk = 36

    defaultConfig {
        applicationId = "com.noexcs.indolent"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/io.netty.versions.properties",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Termux Shared Library for RUN_COMMAND Intent
    implementation("com.termux.termux-app:termux-shared:0.118.0")
    // Avoid conflict with guava
    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")

    // OkHttp for API calls
    implementation(libs.okhttp)
    // SLF4J runtime for akshare-android logging
    implementation("org.slf4j:slf4j-android:1.7.36")
    // Desugaring required by akshare-android
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    // Kotlinx Serialization for JSON
    implementation(libs.kotlinx.serialization.json)
    // WorkManager for background task execution
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    // DocumentFile for SAF filesystem access
    implementation("androidx.documentfile:documentfile:1.0.1")
    // MCP Client
    implementation(libs.mcp.sdk.core)
    implementation(libs.mcp.sdk.client)
    implementation(libs.ktor.client.okhttp)
    // Markdown rendering
    implementation(libs.multiplatform.markdown.m3)
    implementation(libs.multiplatform.markdown.code)
    implementation(libs.multiplatform.markdown.coil3)
    // Chrome Custom Tabs
    implementation("androidx.browser:browser:1.8.0")

    implementation(libs.material.color.utilities)

    // ONNX Runtime for local embedding inference
    implementation(libs.onnxruntime.android)
    // Tantivy via JitPack — BM25 keyword retrieval (Rust JNI)
    implementation(libs.tantivy.android)
    // Encrypted SharedPreferences for secure API key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}