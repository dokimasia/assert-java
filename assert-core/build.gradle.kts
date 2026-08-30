description = "Test assertions defined by a language-neutral standard"

dependencies {
    // Nullability the compiler and the IDE can both read, which is
    // what makes this usable from Kotlin without platform types.
    api(libs.jspecify)

    // compileOnly: the JUnit extension is optional, and a consumer
    // on another framework should not inherit JUnit for it.
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter.api)

    // Test-only: the published artifact carries no runtime dependency.
    testImplementation(libs.jackson.databind)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
