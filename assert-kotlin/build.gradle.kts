plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Coroutine-aware assertions, for the six a Java signature cannot reach"

dependencies {
    api(project(":assert-core"))
    api(libs.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

// The javadoc jar this module publishes holds nothing but a manifest.
// javadoc does not read Kotlin, and Maven Central wants the file
// present whatever is in it. The KDoc ships in the sources jar, which
// is what an IDE reads for a Kotlin dependency anyway.
