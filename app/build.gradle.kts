plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.videoecraft"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.videoecraft"
        minSdk = 24
        targetSdk = 37

        //
        // Versionado manejado por el workflow de release.
        //
        // El CI pasa `-PversionName=<tag>` y `-PversionCode=<n>`; en local
        // se usan los valores por defecto para no exigir nada al que
        // simplemente compila el proyecto. Así el tag de Git y la versión
        // del APK no pueden desincronizarse nunca.
        //
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    //
    // Firma de release, leída de variables de entorno.
    //
    // Si no existen (forks, PRs de colaboradores, compilaciones locales)
    // la configuración queda vacía y Gradle genera un APK **sin firmar**
    // en lugar de fallar. Solo el workflow de release define estas
    // variables, y lo hace desde los GitHub Secrets.
    //
    signingConfigs {
        create("release") {
            val storePath = System.getenv("VIDEOECRAFT_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("VIDEOECRAFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VIDEOECRAFT_KEY_ALIAS")
                keyPassword = System.getenv("VIDEOECRAFT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Solo se aplica la firma si las credenciales están presentes.
            if (System.getenv("VIDEOECRAFT_KEYSTORE_PATH")?.isNotBlank() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Robolectric ejecuta org.json / Base64 reales en la JVM.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

// Altura de compilación de Kotlin para esta app (AGP 9 aplica el KGP integrado).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.media3.transformer.ExperimentalApi",
        )
    }
}

// Room: exporta el esquema para migraciones versionadas (open-source friendly)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // ---------- Compose ----------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ---------- Core / Lifecycle ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ---------- Navegación ----------
    implementation(libs.androidx.navigation.compose)

    // ---------- Media3: previsualización, composición y exportación ----------
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.kotlinx.coroutines.guava)

    // ---------- Room (proyectos y borradores) ----------
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---------- DataStore (preferencias del editor) ----------
    implementation(libs.androidx.datastore.preferences)

    // ---------- Imágenes / miniaturas de video ----------
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ---------- Utilidades multimedia ----------
    implementation(libs.androidx.exifinterface)

    // ---------- Desugaring (java.time, APIs modernas en API 24+) ----------
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ---------- Test ----------
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric: org.json y Base64 reales para los tests del codec
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
