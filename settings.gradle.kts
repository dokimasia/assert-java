rootProject.name = "assert-java"

// One repository, two artifacts. Kotlin calls the Java one for most of
// the set and adds its own only where a coroutine cannot be reached
// through a Java signature.
include("assert-core", "assert-kotlin")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
