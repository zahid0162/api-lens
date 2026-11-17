plugins {
  alias(libs.plugins.android.library)
  id("maven-publish")
}

android {
  namespace = "com.apilens"
  compileSdk = 36

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
  val mavenIdRegex = Regex("^[A-Za-z0-9_.\\-]+$")
  val envGroup = System.getenv("GROUP")?.trim()?.takeIf { it.matches(mavenIdRegex) }
  val envArtifact = System.getenv("ARTIFACT")?.trim()?.takeIf { it.matches(mavenIdRegex) }
  val propGroup = project.findProperty("mavenGroup")?.toString()?.trim()?.takeIf { it.matches(mavenIdRegex) }
  val resolvedGroup = propGroup ?: when {
    envGroup != null && envArtifact != null -> "$envGroup.$envArtifact"
    envGroup != null -> envGroup
    else -> "com.github.apilens"
  }
  val resolvedVersion = (project.findProperty("mavenVersion")?.toString()?.trim()
    ?: System.getenv("VERSION")?.trim())
    ?.takeIf { it.matches(Regex("^[A-Za-z0-9_.\\-+]+$")) && it != "unspecified" }
    ?: "1.0.0"

  publishing {
    publications {
      create<MavenPublication>("release") {
        from(components["release"])
        groupId = resolvedGroup
        artifactId = "api-lens-noop"
        version = resolvedVersion
      }
    }
  }
}

dependencies {
  implementation(libs.okhttp)
  testImplementation(libs.junit)
}
