package dev.dokimi.assertion.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// The corpus, and the encoding a case states its values in.
///
/// This is what checks meaning rather than membership: the same cases run in every
/// implementation of the standard, so a library that means something different by the
/// same name fails here.
@NullMarked
public final class Corpus {

  private static final ObjectMapper JSON = new ObjectMapper();

  /// The seventeen assertions whose arguments cross a language boundary as data.
  private static final List<String> FILES =
      List.of(
          "close-to", "contains", "contains-in-order", "empty", "equal", "false",
          "has-prefix", "has-suffix", "in-range", "length", "matches", "nil",
          "not-contains", "not-empty", "not-equal", "not-nil", "true");

  private Corpus() {}

  /// One corpus case: what an assertion is given, and what it must report.
  ///
  /// @param id the case's id, which names its assertion first
  /// @param assertion the assertion under test, by canonical id
  /// @param args its arguments, already decoded
  /// @param expect whether the assertion must pass or fail
  /// @param messageContains text the failure must carry
  /// @param skip why a language skips this case, by language
  public record Case(
      String id,
      String assertion,
      List<@Nullable Object> args,
      String expect,
      List<String> messageContains,
      Map<String, String> skip) {

    /// Why this language skips the case, or null when it does not.
    ///
    /// @return the declared reason, or null
    public @Nullable String skipReason() {
      return skip.get(Definition.LANGUAGE);
    }
  }

  /// Answer every case the vendored corpus states.
  ///
  /// @return the cases, with their arguments decoded
  public static List<Case> cases() {
    List<Case> found = new ArrayList<>();
    for (String file : FILES) {
      JsonNode document = parse("corpus/" + file + ".json");
      String assertion = document.get("assertion").asText();

      for (JsonNode one : document.get("cases")) {
        List<@Nullable Object> args = new ArrayList<>();
        for (JsonNode arg : one.get("args")) {
          args.add(Literal.decode(arg));
        }

        List<String> contains = new ArrayList<>();
        if (one.has("message_contains")) {
          one.get("message_contains").forEach(node -> contains.add(node.asText()));
        }

        Map<String, String> skip = new LinkedHashMap<>();
        if (one.has("skip")) {
          one.get("skip").properties().forEach(e -> skip.put(e.getKey(), e.getValue().asText()));
        }

        found.add(
            new Case(
                one.get("id").asText(),
                assertion,
                args,
                one.get("expect").asText(),
                List.copyOf(contains),
                Map.copyOf(skip)));
      }
    }
    return found;
  }

  /// Read and parse one vendored file.
  ///
  /// @param name the file, relative to the vendored root
  /// @return its parsed contents
  public static JsonNode parse(String name) {
    try {
      return JSON.readTree(Definition.read(name));
    } catch (com.fasterxml.jackson.core.JsonProcessingException broken) {
      throw new IllegalStateException("the vendored " + name + " is not JSON", broken);
    }
  }
}
