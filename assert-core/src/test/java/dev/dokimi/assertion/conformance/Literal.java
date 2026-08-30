package dev.dokimi.assertion.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Turning a corpus case's typed literals into native values.
///
/// A case states its arguments in a language-neutral encoding, because the same case has
/// to run in every implementation. This is the half of that contract Java owns.
@NullMarked
public final class Literal {

  /// The scalar types a list or map may name as its element type.
  private static final List<String> SCALARS = List.of("bool", "int", "float", "string");

  private Literal() {}

  /// Answer the native value a literal states.
  ///
  /// @param literal one typed literal from a corpus case
  /// @return the value, ready to hand to an assertion
  /// @throws IllegalArgumentException when the literal names a type the encoding does not
  ///     define, or a collection whose element type is missing or not a scalar. A case
  ///     that cannot be decoded stops the run rather than quietly becoming an empty one.
  public static @Nullable Object decode(JsonNode literal) {
    String type = literal.get("type").asText();
    return switch (type) {
      case "null" -> null;
      case "bool" -> literal.get("value").asBoolean();
      case "int" -> literal.get("value").asInt();
      case "float" -> asFloat(literal.get("value"));
      case "string" -> literal.get("value").asText();
      case "list" -> decodeList(literal);
      case "map" -> decodeMap(literal);
      default -> throw new IllegalArgumentException("unknown literal type: " + type);
    };
  }

  /// Answer a floating value, naming the three JSON cannot spell.
  private static double asFloat(JsonNode value) {
    if (!value.isTextual()) {
      return value.asDouble();
    }
    return switch (value.asText()) {
      case "NaN" -> Double.NaN;
      case "Inf" -> Double.POSITIVE_INFINITY;
      case "-Inf" -> Double.NEGATIVE_INFINITY;
      default -> throw new IllegalArgumentException("unknown float: " + value.asText());
    };
  }

  /// Refuse a collection whose element type is missing or unknown.
  ///
  /// An empty collection would decode without ever reading `of`, so a gap in the encoding
  /// would pass unnoticed exactly where nothing else catches it.
  private static void refuseUnknownElement(JsonNode literal, String which) {
    if (!literal.has(which)) {
      throw new IllegalArgumentException(
          "a " + literal.get("type").asText() + " states no " + which);
    }
    String named = literal.get(which).asText();
    if (!SCALARS.contains(named)) {
      throw new IllegalArgumentException(
          "a " + literal.get("type").asText() + " names " + which + " " + named
              + ", which is not a scalar");
    }
  }

  private static @Nullable List<@Nullable Object> decodeList(JsonNode literal) {
    refuseUnknownElement(literal, "of");
    JsonNode value = literal.get("value");
    if (value == null || value.isNull()) {
      return null;
    }

    List<@Nullable Object> items = new ArrayList<>();
    for (JsonNode item : value) {
      items.add(scalar(literal.get("of").asText(), item));
    }
    return items;
  }

  private static @Nullable Map<String, @Nullable Object> decodeMap(JsonNode literal) {
    refuseUnknownElement(literal, "of");
    refuseUnknownElement(literal, "key");
    JsonNode value = literal.get("value");
    if (value == null || value.isNull()) {
      return null;
    }

    Map<String, @Nullable Object> entries = new LinkedHashMap<>();
    value.properties().forEach(e -> entries.put(e.getKey(), scalar(literal.get("of").asText(), e.getValue())));
    return entries;
  }

  /// Decode one element of a collection, by the type the collection names.
  private static @Nullable Object scalar(String type, JsonNode node) {
    return switch (type) {
      case "bool" -> node.asBoolean();
      case "int" -> node.asInt();
      case "float" -> asFloat(node);
      case "string" -> node.asText();
      default -> throw new IllegalArgumentException("not a scalar type: " + type);
    };
  }
}
