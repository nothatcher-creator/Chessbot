plugins {
    id("com.android.application")
}

android {
    namespace = "com.nothatcher.acasbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nothatcher.acasbridge"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-m1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("com.github.bhlangonijr:chesslib:1.3.6")
    testImplementation("junit:junit:4.13.2")
}
