import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun configurationValue(name: String): String =
    localProperties.getProperty(name) ?: providers.environmentVariable(name).orNull.orEmpty()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val goalRemoteCommandsEnabled = providers.gradleProperty("goalRemoteCommandsEnabled")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: true // Planning RPCs activated after the documented T040 verification.
// QA plan overrides are opt-in for local debug/lab APKs and permanently disabled in release.
val testPlanOverridesEnabled = providers.gradleProperty("testPlanOverridesEnabled")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "com.kipu.app"
    testBuildType = if (providers.gradleProperty("isolatedAndroidTests").orNull == "true") "lab" else "debug"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kipu.app"
        minSdk = 24
        targetSdk = 36
        val appVersionCode: Int = providers.gradleProperty("appVersionCode").map { it.toInt() }.getOrElse(1)
        val appVersionName: String = providers.gradleProperty("appVersionName").getOrElse("0.1.0")

        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField(
            "boolean",
            "GOALS_REMOTE_COMMANDS_ENABLED",
            goalRemoteCommandsEnabled.toString(),
        )

        buildConfigField(
            "String",
            "SUPABASE_URL",
            configurationValue("SUPABASE_URL").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            configurationValue("SUPABASE_PUBLISHABLE_KEY").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "OFFLINE_GRANT_KEY_ID",
            configurationValue("OFFLINE_GRANT_KEY_ID").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "OFFLINE_GRANT_PUBLIC_KEY_X509_BASE64",
            configurationValue("OFFLINE_GRANT_PUBLIC_KEY_X509_BASE64").asBuildConfigString(),
        )
        buildConfigField("boolean", "TEST_PLAN_OVERRIDES_ENABLED", testPlanOverridesEnabled.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("lab") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".lab"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "SUPABASE_URL", "\"\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"\"")
            buildConfigField("boolean", "GOALS_REMOTE_COMMANDS_ENABLED", "false")
            buildConfigField("boolean", "TEST_PLAN_OVERRIDES_ENABLED", testPlanOverridesEnabled.toString())
        }
        release {
            buildConfigField("boolean", "TEST_PLAN_OVERRIDES_ENABLED", "false")
            isShrinkResources = true
            // R8 shrinks/optimizes release; library consumer rules cover Room, Hilt,
            // kotlinx.serialization, Ktor and Supabase.
            optimization {
                enable = true
                keepRules {
                    files.add(getDefaultProguardFile("proguard-android-optimize.txt"))
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }

    gradle.taskGraph.whenReady {
        val isReleaseTaskScheduled = allTasks.any { task ->
            val name = task.name
            name.contains("Release", ignoreCase = true) &&
                !name.contains("UnitTest", ignoreCase = true) &&
                !name.contains("AndroidTest", ignoreCase = true)
        }
        if (isReleaseTaskScheduled) {
            val supabaseUrl = configurationValue("SUPABASE_URL")
            val supabaseKey = configurationValue("SUPABASE_PUBLISHABLE_KEY")
            val missing = mutableListOf<String>()
            if (supabaseUrl.isBlank()) missing.add("SUPABASE_URL")
            if (supabaseKey.isBlank()) missing.add("SUPABASE_PUBLISHABLE_KEY")
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Configuracion de release invalida: faltan las variables obligatorias de Supabase: ${missing.joinToString(", ")}. " +
                    "Debes configurarlas en local.properties o mediante variables de entorno (SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY) antes de compilar la variante release."
                )
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Installs the bundled baseline profile on sideloaded and Play installs.
    implementation(libs.androidx.profileinstaller)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Persistence
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)

    // Supabase Auth / Networking
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Java API Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Platform Services
    // Bundled Latin model: receipt OCR works offline, including the first import.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.exifinterface)
    // L-12: com.tom-roush:pdfbox-android:2.0.27.0 (ultima version disponible en Maven Central)
    // trae transitivamente org.bouncycastle:bcpkix-jdk15to18:1.72. Esta version incluye clases internas
    // de EST (JcaJceUtils) con un trust manager permisivo que dispara la advertencia
    // TrustAllX509TrustManager en Android Lint. Se mantiene porque Kipu utiliza PdfBox exclusivamente
    // para extraccion offline de texto en estados de cuenta (sin llamadas de red ni certificados EST)
    // y no existe una version mas reciente compatible de pdfbox-android.
    implementation(libs.pdfbox.android)
    implementation(libs.androidx.biometric)
    implementation(libs.play.billing)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk.jvm)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)

    // Android tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
    kspAndroidTest(libs.hilt.compiler)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    add("labImplementation", libs.androidx.compose.ui.test.manifest)
}
