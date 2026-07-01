plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.astralspun.sampleapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.astralspun.sampleapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
}