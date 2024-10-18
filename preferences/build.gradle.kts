plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "dev.xenoncolt.jellyflix.preferences"
    compileSdk = Versions.compileSdk
    buildToolsVersion = Versions.buildTools

    defaultConfig {
        minSdk = Versions.minSdk
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
        }
        register("staging") {
            initWith(getByName("release"))
        }
    }

    compileOptions {
        sourceCompatibility = Versions.java
        targetCompatibility = Versions.java
    }
}

ktlint {
    version.set(Versions.ktlint)
    android.set(true)
    ignoreFailures.set(false)
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.media3.common)
    implementation(libs.hilt.android)
}
