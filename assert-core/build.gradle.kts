description = "Test assertions defined by a language-neutral standard"

dependencies {
    // Nullability the compiler and the IDE can both read, which is
    // what makes this usable from Kotlin without platform types.
    api(libs.jspecify)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
