plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.astralspun.eleutheriax"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.keep")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    compileOnly(libs.xposed.api)
}