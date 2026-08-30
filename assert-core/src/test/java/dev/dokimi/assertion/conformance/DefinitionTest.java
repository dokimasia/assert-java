package dev.dokimi.assertion.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dokimi.assertion.Check;
import dev.dokimi.assertion.Soft;
import dev.dokimi.assertion.bench.Contract;
import dev.dokimi.assertion.golden.Golden;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This library's surface, held to the definition.
 *
 * <p>The completeness gate: every assertion the standard states must be present under the
 * name the naming table gives it, unless the overlay declares this language cannot supply
 * it.
 */
class DefinitionTest {

  /** The classes a qualified name may name. */
  private static final Map<String, Class<?>> OWNERS =
      Map.of("Golden", Golden.class, "Contract", Contract.class);

  /** Members the recording surface is not expected to carry, with the reason. */
  private static final Map<String, String> CHECK_ONLY =
      Map.of("rejects", "drives a check to failure, which needs a seat that stops");

  private static JsonNode assertions() {
    return Corpus.parse("assertions.json").get("assertions");
  }

  private static Map<String, String> names() {
    Map<String, String> mapped = new LinkedHashMap<>();
    Corpus.parse("naming.json")
        .get("names")
        .properties()
        .forEach(
            entry -> {
              JsonNode name = entry.getValue().get(Definition.LANGUAGE);
              if (name != null) {
                mapped.put(entry.getKey(), name.asText());
              }
            });
    return mapped;
  }

  private static JsonNode overlay() {
    return Corpus.parse("overlay.json");
  }

  /** Whether the overlay declares this language cannot supply the assertion. */
  private static boolean diverges(String id) {
    for (JsonNode entry : overlay().get("diverge")) {
      if (entry.get("id").asText().equals(id)) {
        return true;
      }
    }
    return false;
  }

  /** Whether a class carries a member of that name. */
  private static boolean present(Class<?> owner, String member) {
    return Arrays.stream(owner.getMethods()).map(Method::getName).anyMatch(member::equals);
  }

  /** Answer where an assertion should live, and under what member name. */
  private static Map.Entry<Class<?>, String> locate(String id, String name) {
    if (!name.contains(".")) {
      return Map.entry(Check.class, name);
    }
    String[] parts = name.split("\\.", 2);
    Class<?> owner = OWNERS.get(parts[0]);
    assertNotNull(owner, "no class known for " + parts[0]);
    return Map.entry(owner, parts[1]);
  }

  static Stream<String> everyAssertion() {
    List<String> ids = new ArrayList<>();
    assertions().fieldNames().forEachRemaining(ids::add);
    return ids.stream().sorted();
  }

  @Test
  @DisplayName("the vendored definition states all 41 assertions")
  void theDefinitionIsComplete() {
    assertEquals(41, assertions().size());
  }

  @Test
  @DisplayName("every assertion has a Java name")
  void everyAssertionIsNamed() {
    List<String> missing =
        everyAssertion().filter(id -> !names().containsKey(id)).toList();
    assertTrue(missing.isEmpty(), () -> "no Java name for: " + missing);
  }

  @ParameterizedTest
  @MethodSource("everyAssertion")
  @DisplayName("each assertion is implemented, or declared absent")
  void implementedOrDeclared(String id) {
    String name = names().get(id);
    assertNotNull(name, id + " has no name");
    var where = locate(id, name);

    if (diverges(id)) {
      assertFalse(
          present(where.getKey(), where.getValue()),
          id + " is declared divergent but implemented anyway");
      return;
    }
    assertTrue(present(where.getKey(), where.getValue()), id + " is missing as " + name);
  }

  @Test
  @DisplayName("the overlay declares what this language cannot supply")
  void theOverlayIsHonest() {
    JsonNode diverge = overlay().get("diverge");
    assertTrue(diverge.size() > 0, "the overlay declares nothing");

    Set<String> defined = everyAssertion().collect(java.util.stream.Collectors.toSet());
    for (JsonNode entry : diverge) {
      assertTrue(defined.contains(entry.get("id").asText()), "diverges on an unknown id");
      assertFalse(entry.get("why").asText().isBlank(), "a divergence with no reason");
    }
  }

  @Test
  @DisplayName("a limit names an assertion that is implemented")
  void limitsAreOnThingsThatExist() {
    JsonNode limits = overlay().get("limits");
    assertNotNull(limits, "the overlay states no limits");

    for (JsonNode entry : limits) {
      String id = entry.get("id").asText();
      assertFalse(diverges(id), id + " is both diverged from and limited");
      assertFalse(entry.get("what").asText().isBlank(), "a limit with no what");

      var where = locate(id, names().get(id));
      assertTrue(present(where.getKey(), where.getValue()), id + " is limited but absent");
    }
  }

  @Test
  @DisplayName("the two surfaces carry the same members")
  void theSurfacesAgree() {
    Set<String> check = memberNames(Check.class);
    Set<String> soft = memberNames(Soft.class);
    check.removeAll(CHECK_ONLY.keySet());

    assertEquals(check, soft, "the surfaces carry different members");
  }

  private static Set<String> memberNames(Class<?> surface) {
    return Arrays.stream(surface.getDeclaredMethods())
        .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
        .map(Method::getName)
        .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
  }
}
