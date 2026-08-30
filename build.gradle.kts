plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

// Doc comments are Markdown, which the javadoc tool renders from JDK
// 23 onwards. Older tools read `///` as an ordinary line comment and
// silently produce no documentation at all, so refuse to build rather
// than ship empty Javadoc.
val docTool = JavaVersion.current()
require(docTool >= JavaVersion.VERSION_23) {
    "building needs JDK 23 or newer for Markdown doc comments; found $docTool. " +
        "The published artifact still targets Java 17."
}

// Java 17, not 21. The only thing a newer floor would have bought is
// seeing virtual threads in noTaskLeaks, and they appear in no
// standard enumeration on any version, so it would have bought
// nothing. See docs/rfc/0001.
val javaFloor = 17

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "dev.dokimi"
    version = "0.1.0"

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    // release, not a toolchain: it holds the compile to the Java 17
    // API on whatever JDK is present, without every contributor
    // downloading a second one. CI runs the tests on a real 17.
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(javaFloor)
        // Warnings are how the compiler reports what it cannot check.
        // Treating them as errors is the point of turning them on.
        options.compilerArgs.addAll(
            listOf("-Xlint:all,-serial,-processing", "-Werror"),
        )
    }

    // The artifact says it runs on Java 17, and compiling against the
    // 17 API is not the same as running there. Pass -Ptest.jvm=17 to
    // run the tests on a JDK of that version; CI does.
    val testJvm = providers.gradleProperty("test.jvm").orNull
    val toolchains = extensions.getByType<JavaToolchainService>()

    tasks.withType<Test>().configureEach {
        if (testJvm != null) {
            javaLauncher.set(
                toolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(testJvm))
                },
            )
        }
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    // doclint with missing left on: a public member with no comment,
    // a parameter with no @param and a dead [reference] all fail the
    // build. That is what stops the documentation rotting quietly.
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("library") {
                from(components["java"])
            }
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:all", "-quiet")
            addBooleanOption("Werror", true)
            encoding = "UTF-8"
        }
        // Javadoc reports a missing comment as a warning, and a
        // warning nobody fails on is a warning nobody reads.
        isFailOnError = true
    }
}
