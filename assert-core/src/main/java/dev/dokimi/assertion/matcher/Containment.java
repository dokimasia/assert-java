package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Option;
import dev.dokimi.assertion.Seat;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Whether a container holds something.
///
/// What holding means follows the haystack: text holds a substring, a map holds a key,
/// and any other collection or array holds an element compared structurally.
@NullMarked
public final class Containment {

  private Containment() {}

  /// Whether the haystack held the needle, and whether it could answer at all.
  ///
  /// @param held whether the needle was found
  /// @param answered whether the haystack is a kind that can be searched
  private record Outcome(boolean held, boolean answered) {
    static final Outcome NO_ANSWER = new Outcome(false, false);

    static Outcome of(boolean held) {
      return new Outcome(held, true);
    }
  }

  private static Outcome holds(
      @Nullable Object haystack, @Nullable Object needle, Relaxations relax) {

    if (haystack instanceof CharSequence text) {
      if (!(needle instanceof CharSequence sought)) {
        return Outcome.NO_ANSWER;
      }
      return Outcome.of(text.toString().contains(sought));
    }
    if (haystack instanceof Map<?, ?> map) {
      return Outcome.of(map.containsKey(needle));
    }
    if (haystack instanceof Collection<?> items) {
      return Outcome.of(items.stream().anyMatch(item -> Compare.equal(item, needle, relax)));
    }
    if (haystack != null && haystack.getClass().isArray()) {
      int length = Array.getLength(haystack);
      for (int i = 0; i < length; i++) {
        if (Compare.equal(Array.get(haystack, i), needle, relax)) {
          return Outcome.of(true);
        }
      }
      return Outcome.of(false);
    }
    return Outcome.NO_ANSWER;
  }

  private static void unsupported(
      Seat seat, Mode mode, String msg, String assertion,
      Map<String, @Nullable Object> detail) {
    Report.failure(seat, mode, assertion, msg, detail);
  }

  /// Fail when haystack does not hold needle.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param haystack the container or text to search
  /// @param needle the element, key or substring to find
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void contains(
      Seat seat,
      Mode mode,
      @Nullable Object haystack,
      @Nullable Object needle,
      String msg,
      Option... options) {
    seat.helper();
    Outcome outcome = holds(haystack, needle, Relaxations.of(options));
    if (!outcome.answered()) {
      unsupported(seat, mode, msg, "contains",
          Report.detail("haystack", haystack, "needle", needle));
      return;
    }
    if (!outcome.held()) {
      Report.failure(seat, mode, "contains", msg,
          Report.detail("haystack", haystack, "needle", needle));
    }
  }

  /// Fail when haystack holds needle.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param haystack the container or text to search
  /// @param needle what must be absent
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void notContains(
      Seat seat,
      Mode mode,
      @Nullable Object haystack,
      @Nullable Object needle,
      String msg,
      Option... options) {
    seat.helper();
    Outcome outcome = holds(haystack, needle, Relaxations.of(options));
    if (!outcome.answered()) {
      unsupported(seat, mode, msg, "not-contains",
          Report.detail("haystack", haystack, "needle", needle));
      return;
    }
    if (outcome.held()) {
      Report.failure(seat, mode, "not-contains", msg,
          Report.detail("haystack", haystack, "needle", needle));
    }
  }

  /// Fail when got does not hold every needle, in order.
  ///
  /// Each needle is looked for after the previous one's match ends, so the same text
  /// cannot satisfy two needles. Anything may sit between them.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the text to search
  /// @param needles the substrings, in the order they must appear
  /// @param msg the contract under test
  public static void containsInOrder(
      Seat seat, Mode mode, @Nullable Object got, String[] needles, String msg) {
    seat.helper();
    if (!(got instanceof CharSequence sequence)) {
      Report.failure(seat, mode, "contains-in-order", msg,
          Report.detail("haystack", got, "needle", "", "index", 0));
      return;
    }

    String text = sequence.toString();
    int from = 0;
    for (int i = 0; i < needles.length; i++) {
      String needle = needles[i];
      int at = text.indexOf(needle, from);
      if (at < 0) {
        Report.failure(seat, mode, "contains-in-order", msg,
            Report.detail("haystack", text, "needle", needle, "index", i));
        return;
      }
      from = at + needle.length();
    }
  }
}
