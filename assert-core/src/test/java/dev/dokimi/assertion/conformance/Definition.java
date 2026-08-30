package dev.dokimi.assertion.conformance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NullMarked;

/// Reading the definition this library is held to.
///
/// Vendored as test resources rather than shipped. The published artifact carries no
/// runtime dependency, and reading JSON would need one: Java has no parser in its
/// standard library. Holding this library to the standard is a test's job anyway.
///
/// The files are vendored rather than fetched, so a build is reproducible and a test
/// run needs no network. The `specSync` task refreshes them.
@NullMarked
public final class Definition {

  /// This language's column in the naming table.
  public static final String LANGUAGE = "java";

  /// Where the vendored definition sits on the classpath.
  private static final String ROOT = "/dev/dokimi/assertion/conformance/spec/";

  private Definition() {}

  /// Read one vendored file.
  ///
  /// @param name the file, relative to the vendored root
  /// @return its contents
  /// @throws IllegalStateException when the resource is missing, which means the jar was
  ///     built without it rather than that a caller did anything wrong
  public static String read(String name) {
    try (InputStream stream = Definition.class.getResourceAsStream(ROOT + name)) {
      if (stream == null) {
        throw new IllegalStateException("the vendored definition has no " + name);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      throw new UncheckedIOException("cannot read " + name, unreadable);
    }
  }

  /// Answer the definition version this library implements.
  ///
  /// @return the version, as the VERSION file states it
  public static String version() {
    return read("VERSION").trim();
  }
}
