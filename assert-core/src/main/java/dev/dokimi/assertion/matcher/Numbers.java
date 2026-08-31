package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Assertions about numbers, where exact equality is the wrong question.
@NullMarked
public final class Numbers {

  private Numbers() {}

  private static @Nullable Double asNumber(@Nullable Object value) {
    return value instanceof Number number ? number.doubleValue() : null;
  }

  private static void requiresNumber(
      Seat seat, Mode mode, String msg, String assertion,
      Map<String, @Nullable Object> detail) {
    Report.failure(seat, mode, assertion, msg, detail);
  }

  /// Fail when got is further than tolerance from want.
  ///
  /// The tolerance is an absolute difference and the bound is inclusive, so a difference
  /// exactly equal to tolerance passes. NaN is outside every tolerance, whether it is the
  /// value, the target or the tolerance.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the number produced
  /// @param want the number it should be near
  /// @param tolerance the largest acceptable absolute difference
  /// @param msg the contract under test
  public static void closeTo(
      Seat seat, Mode mode, @Nullable Object got, double want, double tolerance, String msg) {
    seat.helper();
    Double value = asNumber(got);
    if (value == null) {
      requiresNumber(seat, mode, msg, "close-to",
          Report.detail("got", got, "want", want, "tolerance", tolerance));
      return;
    }

    // Every comparison against NaN is false, so a bare `diff > tolerance`
    // would pass a NaN rather than reject it. Name the case instead.
    double diff = Math.abs(value - want);
    if (Double.isNaN(diff) || Double.isNaN(tolerance)) {
      Report.failure(seat, mode, "close-to", msg,
          Report.detail("got", got, "want", want, "tolerance", tolerance));
      return;
    }
    if (diff > tolerance) {
      Report.failure(seat, mode, "close-to", msg,
          Report.detail("got", got, "want", want, "tolerance", tolerance));
    }
  }

  /// Fail when got falls outside low to high.
  ///
  /// The interval is closed, so both bounds pass. A range with low above high can hold
  /// nothing, and says so rather than reporting the value. NaN is in no range.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the number to place
  /// @param low the lowest acceptable value
  /// @param high the highest acceptable value
  /// @param msg the contract under test
  public static void inRange(
      Seat seat, Mode mode, @Nullable Object got, double low, double high, String msg) {
    seat.helper();
    Double value = asNumber(got);
    if (value == null) {
      requiresNumber(seat, mode, msg, "in-range",
          Report.detail("got", got, "low", low, "high", high));
      return;
    }
    if (low > high) {
      Report.failure(seat, mode, "in-range", msg,
          Report.detail("got", got, "low", low, "high", high));
      return;
    }
    if (Double.isNaN(value) || value < low || value > high) {
      Report.failure(seat, mode, "in-range", msg,
          Report.detail("got", got, "low", low, "high", high));
    }
  }
}
