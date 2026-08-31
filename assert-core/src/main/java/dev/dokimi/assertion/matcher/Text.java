package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Assertions about text.
@NullMarked
public final class Text {

  private Text() {}

  private static @Nullable String asText(@Nullable Object value) {
    return value instanceof CharSequence text ? text.toString() : null;
  }

  private static void requiresText(
      Seat seat, Mode mode, String msg, String assertion,
      Map<String, @Nullable Object> detail) {
    Report.failure(seat, mode, assertion, msg, detail);
  }

  /// Fail when got does not start with prefix.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the text to inspect
  /// @param prefix what it must start with
  /// @param msg the contract under test
  public static void hasPrefix(
      Seat seat, Mode mode, @Nullable Object got, String prefix, String msg) {
    seat.helper();
    String text = asText(got);
    if (text == null) {
      requiresText(seat, mode, msg, "has-prefix", Report.detail("got", got, "prefix", prefix));
      return;
    }
    if (!text.startsWith(prefix)) {
      Report.failure(seat, mode, "has-prefix", msg,
          Report.detail("got", text, "prefix", prefix));
    }
  }

  /// Fail when got does not end with suffix.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the text to inspect
  /// @param suffix what it must end with
  /// @param msg the contract under test
  public static void hasSuffix(
      Seat seat, Mode mode, @Nullable Object got, String suffix, String msg) {
    seat.helper();
    String text = asText(got);
    if (text == null) {
      requiresText(seat, mode, msg, "has-suffix", Report.detail("got", got, "suffix", suffix));
      return;
    }
    if (!text.endsWith(suffix)) {
      Report.failure(seat, mode, "has-suffix", msg,
          Report.detail("got", text, "suffix", suffix));
    }
  }

  /// Fail when got does not match the pattern.
  ///
  /// The pattern is searched rather than anchored: use `^` and `$` where you mean the
  /// whole value. A pattern that does not compile is reported as the failure, so a typo
  /// in a pattern does not read like a failing subject.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the text to match
  /// @param pattern a regular expression
  /// @param msg the contract under test
  public static void matches(
      Seat seat, Mode mode, @Nullable Object got, String pattern, String msg) {
    seat.helper();
    String text = asText(got);
    if (text == null) {
      requiresText(seat, mode, msg, "matches", Report.detail("got", got, "pattern", pattern));
      return;
    }

    Pattern compiled;
    try {
      compiled = Pattern.compile(pattern);
    } catch (PatternSyntaxException broken) {
      Report.failure(seat, mode, "matches", msg,
          Report.detail("got", got, "pattern", pattern));
      return;
    }
    if (!compiled.matcher(text).find()) {
      Report.failure(seat, mode, "matches", msg,
          Report.detail("got", text, "pattern", pattern));
    }
  }
}
