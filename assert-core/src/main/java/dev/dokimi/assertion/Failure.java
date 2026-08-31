package dev.dokimi.assertion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// What a failing assertion reports.
///
/// The record is the same shape in every implementation of the standard. The
/// sentence a person reads is rendered from it and is not standardised, because
/// each language reads its own conventions.
///
/// @param assertion the canonical id the definition names
/// @param contract the caller's message, unchanged
/// @param detail the values named by that assertion's declared fields
/// @param where the call site, or null when the frame could not be read
@NullMarked
public record Failure(
    String assertion, String contract, Map<String, @Nullable Object> detail, @Nullable Where where) {

  /// The order Java names detail fields in, which is want before got and the
  /// rest in a fixed reading order. A field not listed here follows these,
  /// alphabetically.
  ///
  /// The standard fixes the record, not the sentence.
  private static final List<String> ORDER =
      List.of(
          "want",
          "got",
          "length",
          "haystack",
          "needle",
          "index",
          "prefix",
          "suffix",
          "pattern",
          "tolerance",
          "low",
          "high",
          "first",
          "second",
          "attempts",
          "last",
          "leaked",
          "field");

  /// Turn this record into the sentence a person reads.
  ///
  /// @return the contract, then the detail it carries
  public String render() {
    if (detail.isEmpty()) {
      return contract;
    }

    Map<String, @Nullable Object> ordered = new LinkedHashMap<>();
    for (String name : ORDER) {
      if (detail.containsKey(name)) {
        ordered.put(name, detail.get(name));
      }
    }
    detail.keySet().stream().filter(name -> !ORDER.contains(name)).sorted()
        .forEach(name -> ordered.put(name, detail.get(name)));

    StringBuilder said = new StringBuilder(contract).append(": ");
    boolean first = true;
    for (Map.Entry<String, @Nullable Object> held : ordered.entrySet()) {
      if (!first) {
        said.append(", ");
      }
      first = false;
      said.append(held.getKey()).append(' ').append(dev.dokimi.assertion.matcher.Show.value(held.getValue()));
    }
    return said.toString();
  }
}
