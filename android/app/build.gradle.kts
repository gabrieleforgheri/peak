plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Il catalogo degli esercizi è unico per web, server e iPhone: common/catalog.json.
// Qui si copia negli asset a ogni build, mai a mano.
val copyCatalog by tasks.registering(Copy::class) {
    from(rootProject.file("../common/catalog.json"))
    into(layout.buildDirectory.dir("generated/catalog"))
}

android {
    namespace = "com.gabrieleforghieri.peak"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gabrieleforghieri.peak"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "3.4"
    }

    // L'indirizzo del server è fisso (https://peak.yrb4g.com). Solo nei build di debug si può cambiare, per provare con un server locale:
    // ./gradlew assembleDebug -PpeakBaseUrl=http://10.0.2.2:3416   (dall'emulatore il Mac è 10.0.2.2)
    buildTypes {
        debug {
            buildConfigField("String", "PEAK_BASE_URL", "\"${(findProperty("peakBaseUrl") as String?) ?: "https://peak.yrb4g.com"}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "PEAK_BASE_URL", "\"https://peak.yrb4g.com\"")
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

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/catalog"))

    // Le prove usano i dati condivisi con web e iPhone: il catalogo e i vettori dei record.
    sourceSets.getByName("test").resources.srcDirs(rootProject.file("../common/fixtures"), layout.buildDirectory.dir("generated/catalog"))

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
