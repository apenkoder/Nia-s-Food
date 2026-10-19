import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.detekt)
}

configure<KtlintExtension> {
  version.set("1.5.0")
  android.set(true)
}

configure<DetektExtension> {
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
  baseline = file("$projectDir/detekt-baseline.xml")
}

tasks.withType<Detekt>().configureEach {
  jvmTarget = "17"
  reports {
    html.required.set(true)
    xml.required.set(true)
    txt.required.set(false)
    sarif.required.set(true)
  }
}

tasks.register("codeQualityCheck") {
  group = "verification"
  description = "Runs Kotlin formatting and static analysis checks."
  dependsOn("ktlintCheck", "detekt")
}

// Release signing comes from environment variables only. Nothing secret lives in the repo.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
  namespace = "com.apenkoder.niasfood"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.apenkoder.niasfood"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"
  }

  signingConfigs {
    if (keystorePath != null) {
      create("release") {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyPassword = System.getenv("KEY_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      }
    }
  }

  buildTypes {
    debug {
      // Debug builds seed demo data. The seeder class only exists in src/debug.
      buildConfigField("boolean", "DEMO_SEED", "true")
    }
    release {
      buildConfigField("boolean", "DEMO_SEED", "false")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
  }

  // Bytecode target 17 (Gradle itself runs on JDK 21). See docs/ARCHITECTURE.md.
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.navigation.compose)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.animation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.icons.core)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.sqlite.ktx)

  implementation(libs.bcrypt)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
