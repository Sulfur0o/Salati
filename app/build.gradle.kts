import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val signingPropertiesPath = providers.environmentVariable("SALATI_SIGNING_PROPERTIES")
  .orNull
  ?.trim()
  ?.takeIf(String::isNotEmpty)
val signingIsRequired = providers.gradleProperty("salati.requireSigning")
  .orNull
  ?.toBooleanStrictOrNull()
  ?: false
val signingPropertiesFile = signingPropertiesPath?.let(::file)
val signingProperties = signingPropertiesFile?.let { propertiesFile ->
  if (!propertiesFile.isFile) {
    throw GradleException("SALATI_SIGNING_PROPERTIES must reference a readable properties file")
  }
  Properties().apply {
    propertiesFile.inputStream().use(::load)
  }
}

val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
if (signingProperties != null) {
  val missingKeys = signingKeys.filter { signingProperties.getProperty(it).isNullOrBlank() }
  if (missingKeys.isNotEmpty()) {
    throw GradleException("External signing properties are incomplete; required keys: ${signingKeys.joinToString()}")
  }
  val configuredStoreFile = file(signingProperties.getProperty("storeFile"))
  if (!configuredStoreFile.isFile) {
    throw GradleException("The external release keystore does not exist")
  }
} else if (signingIsRequired) {
  throw GradleException(
    "Release signing is required. Set SALATI_SIGNING_PROPERTIES to a valid external properties file."
  )
}

android {
    namespace = "io.github.sulfuro25.salati"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sulfuro.salati"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
    }

    buildTypes {
        if (signingProperties != null) {
            signingConfigs.create("externalRelease") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("externalRelease")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }
}

androidComponents {
  beforeVariants(selector().withBuildType("release")) { variantBuilder ->
    (variantBuilder as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
  }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner, Robolectric
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.compose.ui.test.manifest)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)

  // WorkManager
  implementation(libs.androidx.work.runtime)
  testImplementation(libs.androidx.work.testing)

  // DataStore and serialization
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.serialization.json)

  // Material Icons
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)

  // Desugaring
  coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// Release policy tests inspect the generated release manifest even when invoked via the debug unit-test task.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
  dependsOn("processReleaseMainManifest")
  javaLauncher.set(
    javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(21))
    }
  )
}
