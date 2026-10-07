import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.netbanding.app.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Microbenchmark-style isolation is not what we want here; the generator
        // needs to drive the real app UI.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    // The app whose release build gets traced.
    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)

    // Traces the *release* build of the app and wires up
    // `gradle :app:generateBaselineProfile`. The configuration is created by the
    // androidx.baselineprofile plugin, which registers it late enough that the
    // Kotlin DSL accessor is not generated — hence `add(...)`.
    }

// The plugin registers its `baselineProfile` configuration during afterEvaluate,
// so the dependency has to be declared at the same point. `add(...)` rather than
// the typed accessor: the Kotlin DSL accessor is not generated for a
// configuration that does not exist yet at script-compile time.
// The androidx.baselineprofile plugin creates its `baselineProfile` configuration
// lazily (on variant finalization), so neither the Kotlin DSL accessor nor a
// plain `dependencies.add` in the script body can see it. `maybeCreate` here is
// idempotent with the plugin's own call; the plugin then applies the usage,
// build-type and version attributes to this same instance.
configurations.maybeCreate("baselineProfile")

dependencies.add("baselineProfile", project(":app"))

