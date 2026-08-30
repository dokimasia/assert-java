description = "Test assertions defined by a language-neutral standard"

dependencies {
    // Nullability the compiler and the IDE can both read, which is
    // what makes this usable from Kotlin without platform types.
    api(libs.jspecify)

    // Test-only: the published artifact carries no runtime dependency.
    testImplementation(libs.jackson.databind)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
