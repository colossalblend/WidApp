import java.util.Properties

// Version comes from the release tag in CI (-PappVersion=1.2.3 or 1.3.0-beta.2).
// versionCode = MMM·1_000_000 + mm·10_000 + pp·100 + (beta number, or 99 for a final release),
// so every newer tag installs over the previous one and a final release beats its betas.
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "1.0.0"
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-[A-Za-z]*\.?(\d+)?)?""").find(appVersion)?.groupValues
    ?.let { g ->
        val pre = if (appVersion.contains('-')) (g[4].toIntOrNull() ?: 1).coerceIn(1, 98) else 99
        g[1].toInt() * 1_000_000 + g[2].toInt() * 10_000 + g[3].toInt() * 100 + pre
    } ?: 1

// Release signing comes from the environment (CI secrets) or a local, git-ignored keystore.properties.
// Without it, release APKs are left unsigned rather than signed with a public key.
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(prop)
val releaseStoreFile: String? = signingValue("WIDAPP_KEYSTORE_FILE", "storeFile")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.widapp"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "app.widapp"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        // Debug builds share a committed key so dev APKs from any machine install over each other.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("WIDAPP_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("WIDAPP_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("WIDAPP_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.maxHeapSize = "3g"
                it.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                )
            }
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.graphics.shapes)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
