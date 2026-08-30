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

// One version for both artifacts. The release workflow checks the tag
// against this before anything leaves the runner.
val libVersion = "0.1.0"

version = libVersion

// Where both subprojects publish before the bundle is zipped. Maven
// Central takes one archive holding every artifact of a release, so
// the two share a directory rather than each writing its own.
val stagingDir = layout.buildDirectory.dir("staging-deploy")

// A staging directory left over from an earlier version would ship a
// release nobody built, so it is emptied before either publish runs.
val clearStaging = tasks.register<Delete>("clearStaging") {
    delete(stagingDir)
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    group = "dev.dokimi"
    version = libVersion

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

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("library") {
                from(components["java"])

                // Maven Central rejects a POM without these. The
                // description comes from the subproject's own build
                // file, which Gradle has not read yet, so it is read
                // through a provider rather than now.
                pom {
                    name.set(project.name)
                    description.set(provider { project.description })
                    url.set("https://github.com/dokimasia/assert-java")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://github.com/dokimasia/assert-java/blob/main/LICENSE")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("rklopper")
                            name.set("Roy Klopper")
                            email.set("roy.klopper@stealthscale.io")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/dokimasia/assert-java.git")
                        developerConnection.set("scm:git:ssh://git@github.com/dokimasia/assert-java.git")
                        url.set("https://github.com/dokimasia/assert-java")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "staging"
                url = stagingDir.get().asFile.toURI()
            }
        }
    }

    tasks.named("publishLibraryPublicationToStagingRepository") {
        dependsOn(clearStaging)
    }

    // Every file Central takes has to carry a detached signature.
    // Signing only when a key is present keeps an ordinary build and
    // an ordinary contributor out of the key's way.
    extensions.configure<SigningExtension> {
        val key = providers.environmentVariable("SIGNING_KEY").orNull
        val password = providers.environmentVariable("SIGNING_PASSWORD").orNull
        if (key != null && password != null) {
            useInMemoryPgpKeys(key, password)
            sign(extensions.getByType<PublishingExtension>().publications["library"])
        }
    }

    // doclint with missing left on: a public member with no comment,
    // a parameter with no @param and a dead [reference] all fail the
    // build. That is what stops the documentation rotting quietly.
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

// The release workflow checks the tag against this before it builds
// anything. A version is not readable from a tag alone: the tag can
// say anything.
tasks.register("printVersion") {
    group = "help"
    description = "Print the version both artifacts are published under"
    val stated = libVersion
    doLast { println(stated) }
}

// The archive Central takes: the staging directory, zipped whole. It
// holds both artifacts with their sources, javadoc, POMs, checksums
// and signatures, in Maven repository layout.
tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Package both artifacts as one Maven Central deployment bundle"

    dependsOn(subprojects.map { "${it.path}:publishLibraryPublicationToStagingRepository" })

    from(stagingDir) {
        // Central builds its own index, and every file in the bundle
        // has to carry a signature. Gradle writes this one and does
        // not sign it, so it would fail validation for no gain.
        exclude("**/maven-metadata.xml*")
    }
    archiveFileName.set("central-bundle-$libVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central"))
}
