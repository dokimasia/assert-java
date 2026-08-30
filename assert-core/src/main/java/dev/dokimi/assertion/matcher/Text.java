package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
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

  private static void requiresText(Seat seat, Mode mode, String msg, @Nullable Object got) {
    String type = got == null ? "null" : got.getClass().getSimpleName();
    Report.to(seat, mode, msg + ": requires text, got " + type);
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
      requiresText(seat, mode, msg, got);
      return;
    }
    if (!text.startsWith(prefix)) {
      Report.to(
          seat,
          mode,
          msg + ": " + Show.value(text) + " does not start with " + Show.value(prefix));
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
      requiresText(seat, mode, msg, got);
      return;
    }
    if (!text.endsWith(suffix)) {
      Report.to(
          seat,
          mode,
          msg + ": " + Show.value(text) + " does not end with " + Show.value(suffix));
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
      requiresText(seat, mode, msg, got);
      return;
    }

    Pattern compiled;
    try {
      compiled = Pattern.compile(pattern);
    } catch (PatternSyntaxException broken) {
      Report.to(
          seat,
          mode,
          msg + ": pattern " + Show.value(pattern) + " does not compile: "
              + broken.getDescription());
      return;
    }
    if (!compiled.matcher(text).find()) {
      Report.to(
          seat,
          mode,
          msg + ": " + Show.value(text) + " does not match " + Show.value(pattern));
    }
  }
}
