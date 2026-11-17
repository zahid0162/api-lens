plugins {
  alias(libs.plugins.android.library)
  id("maven-publish")
}

android {
  namespace = "com.apilens"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 23
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
  }
}

afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("release") {
        from(components["release"])
        groupId = "com.github.apilens"
        artifactId = "api-lens-noop"
        version = "1.0.0"
      }
    }
  }
}

dependencies {
  implementation(libs.okhttp)
  testImplementation(libs.junit)
}
