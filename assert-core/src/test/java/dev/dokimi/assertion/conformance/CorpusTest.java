package dev.dokimi.assertion.conformance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dokimi.assertion.Check;
import dev.dokimi.assertion.Where;
import dev.dokimi.assertion.Controlled;
import java.time.Instant;
import dev.dokimi.assertion.Failure;
import java.util.Objects;
import dev.dokimi.assertion.Recorder;
import dev.dokimi.assertion.Seat;
import dev.dokimi.assertion.Soft;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The corpus, driven against both surfaces.
 *
 * <p>Every case runs twice, once per surface. The two carrying the same assertions means
 * they produce the same outcome from the same case, and running one and trusting the
 * other would leave that untested.
 */
class CorpusTest {

  /** Both surfaces, so every case is driven through each. */
  private static final Map<String, Class<?>> SURFACES =
      Map.of("Check", Check.class, "Soft", Soft.class);

  /** The name this language gives each assertion. */
  private static Map<String, String> names() {
    JsonNode table = Corpus.parse("naming.json").get("names");
    var mapped = new java.util.LinkedHashMap<String, String>();
    table.properties()
        .forEach(
            entry -> {
              JsonNode name = entry.getValue().get(Definition.LANGUAGE);
              if (name != null) {
                mapped.put(entry.getKey(), name.asText());
              }
            });
    return mapped;
  }

  static Stream<Arguments> everyCaseOnEverySurface() {
    List<Arguments> runs = new ArrayList<>();
    for (Corpus.Case one : Corpus.cases()) {
      for (String surface : SURFACES.keySet().stream().sorted().toList()) {
        runs.add(Arguments.of(surface, one));
      }
    }
    return runs.stream();
  }

  @Test
  void theVendoredCorpusStatesCases() {
    assertTrue(Corpus.cases().size() >= 70, "the corpus states fewer cases than expected");
  }

  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("everyCaseOnEverySurface")
  void case_(String surface, Corpus.Case one) throws Exception {
    assumeTrue(one.skipReason() == null, () -> "declared skip: " + one.skipReason());

    String member = names().get(one.assertion());
    assertNotNull(member, "no " + Definition.LANGUAGE + " name for " + one.assertion());

    if (one.subject() != null) {
      runSubjectCase(one, surface);
      return;
    }

    Recorder recorder = new Recorder();
    invoke(SURFACES.get(surface), member, recorder, one);

    String mismatch = mismatch(one, recorder);
    assertNotNull(one.id());
    assertTrue(mismatch == null, () -> one.id() + " on " + surface + ": " + mismatch);
    checkWhere(one, recorder);
  }

  /**
   * Drive a case that names a behaviour, and hold the outcome to what it states.
   *
   * <p>A kind this language cannot build is a skip, which is what the standard
   * states for one an implementation cannot make.
   */
  private static void runSubjectCase(Corpus.Case one, String surface) {
    SubjectRegistry.Subject held = SubjectRegistry.build(one.subject());
    assumeTrue(held != null, "no subject named " + one.subject());

    Recorder recorder = new Recorder().withClock(new Controlled(Instant.EPOCH));
    boolean ran =
        surface.equals("check")
            ? SubjectRegistry.runCheck(one.assertion(), held, recorder, one.id())
            : SubjectRegistry.runSoft(one.assertion(), held, recorder, one.id());
    assumeTrue(ran, "no driver for " + one.assertion());

    String mismatch = mismatch(one, recorder);
    assertTrue(mismatch == null, () -> one.id() + " on " + surface + ": " + mismatch);
    checkWhere(one, recorder);
  }

  /**
   * Hold every record to naming a real call site outside the library.
   *
   * <p>A case cannot state a line: the line is wherever the caller put the call.
   * What every case can state is that the record points somewhere a reader can
   * open, and never at the machinery that built it. Both call-site bugs this
   * standard has found were of that shape.
   */
  private static void checkWhere(Corpus.Case one, Recorder recorder) {
    for (Failure held : recorder.failures()) {
      Where where = held.where();
      assertNotNull(where, () -> one.id() + ": " + held.assertion() + " reported no call site");
      assertTrue(
          where.line() > 0,
          () -> one.id() + ": " + held.assertion() + " reported line zero");
      assertFalse(
          where.file().equals("Report.java"),
          () -> one.id() + ": " + held.assertion() + " reports the library's own frame");
    }
  }

  /**
   * Call the named assertion with the case's arguments.
   *
   * <p>Reflection, because the corpus names its assertion as a string. Two things the
   * plain call does not handle: a varargs assertion reports one more parameter than a
   * caller passes, and a reflective call will not widen a boxed Integer to a double
   * parameter the way the compiler would.
   */
  private static void invoke(Class<?> surface, String member, Seat seat, Corpus.Case one)
      throws Exception {

    int given = one.args().size() + 2;
    Method found = null;
    for (Method candidate : surface.getMethods()) {
      if (!candidate.getName().equals(member)) {
        continue;
      }
      int fixed = candidate.isVarArgs() ? candidate.getParameterCount() - 1 : candidate.getParameterCount();
      if (candidate.isVarArgs() ? given >= fixed : given == fixed) {
        found = candidate;
        break;
      }
    }
    assertNotNull(found, surface.getSimpleName() + " has no " + member + " taking " + given);

    Class<?>[] wanted = found.getParameterTypes();
    int fixed = found.isVarArgs() ? wanted.length - 1 : wanted.length;

    Object[] call = new Object[found.isVarArgs() ? wanted.length : given];
    call[0] = seat;
    for (int i = 0; i < one.args().size(); i++) {
      call[i + 1] = widen(one.args().get(i), wanted[i + 1]);
    }
    call[fixed - 1] = one.id();
    if (found.isVarArgs()) {
      call[wanted.length - 1] = java.lang.reflect.Array.newInstance(
          wanted[wanted.length - 1].getComponentType(), 0);
    }
    found.invoke(null, call);
  }

  /** Widen a boxed number where the parameter is a wider primitive. */
  private static Object widen(Object value, Class<?> wanted) {
    if (value instanceof Number number) {
      if (wanted == double.class || wanted == Double.class) {
        return number.doubleValue();
      }
      if (wanted == int.class || wanted == Integer.class) {
        return number.intValue();
      }
    }
    if (value instanceof List<?> items && wanted == String[].class) {
      return items.stream().map(String::valueOf).toArray(String[]::new);
    }
    return value;
  }

  /** Hold a recorder to what the case says must have happened. */
  private static String mismatch(Corpus.Case one, Recorder recorder) {
    if (one.expect().equals("pass")) {
      return recorder.failed() ? "expected a pass, got: " + recorder.message() : null;
    }
    if (!recorder.failed()) {
      return "expected a failure, got a pass";
    }

    List<Failure> records = recorder.failures();
    if (records.isEmpty()) {
      return "reported no record; the assertion did not report one";
    }
    return detailMismatch(one, records.get(0));
  }

  /**
   * Say how a record's detail differs from what the case states, and null when
   * every stated field matches.
   */
  private static String detailMismatch(Corpus.Case one, Failure held) {
    for (Map.Entry<String, Object> stated : one.detail().entrySet()) {
      String name = stated.getKey();
      if (!held.detail().containsKey(name)) {
        return "the record holds no detail \"" + name + "\", want " + stated.getValue();
      }
      Object found = held.detail().get(name);
      if (!same(found, stated.getValue())) {
        return "detail \"" + name + "\" is " + found + ", want " + stated.getValue();
      }
    }
    return null;
  }

  /**
   * Whether a reported value matches what a case states.
   *
   * A NaN is unequal to itself under the standard's own rules, which would make
   * a case stating one impossible to satisfy. Here the question is whether the
   * assertion reported the value the case named, so two NaNs count as the same.
   */
  private static boolean same(Object held, Object want) {
    if (held instanceof Double first && want instanceof Double second
        && first.isNaN() && second.isNaN()) {
      return true;
    }
    if (held instanceof Number first && want instanceof Number second
        && !(held instanceof Double) && !(want instanceof Double)) {
      return first.longValue() == second.longValue();
    }
    return Objects.equals(held, want);
  }
}
