package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// How many entries a value holds.
///
/// A value with no length is itself the failure rather than a ClassCastException, so a
/// wrong argument reads like every other failure.
@NullMarked
public final class Sizes {

  private Sizes() {}

  /// Answer how many entries a value holds, or null when it has no length.
  ///
  /// @param value anything an assertion was handed
  /// @return the count, or null when the value cannot answer
  public static @Nullable Integer sizeOf(@Nullable Object value) {
    if (value instanceof CharSequence text) {
      return text.length();
    }
    if (value instanceof Collection<?> items) {
      return items.size();
    }
    if (value instanceof Map<?, ?> map) {
      return map.size();
    }
    if (value != null && value.getClass().isArray()) {
      return Array.getLength(value);
    }
    return null;
  }

  /// Report that a value cannot answer for its length.
  private static void unsupported(Seat seat, Mode mode, String msg, @Nullable Object got) {
    String type = got == null ? "null" : got.getClass().getSimpleName();
    Report.to(seat, mode, msg + ": length is not supported for " + type);
  }

  /// Fail when got does not hold want entries.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the container to measure
  /// @param want how many entries it must hold
  /// @param msg the contract under test
  public static void length(
      Seat seat, Mode mode, @Nullable Object got, int want, String msg) {
    seat.helper();
    Integer size = sizeOf(got);
    if (size == null) {
      unsupported(seat, mode, msg, got);
      return;
    }
    if (size != want) {
      Report.to(seat, mode, msg + ": expected length " + want + ", got " + size);
    }
  }

  /// Fail when got holds anything.
  ///
  /// Empty is not absent: null has no length, so it fails here rather than passing.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the container that must hold nothing
  /// @param msg the contract under test
  public static void isEmpty(Seat seat, Mode mode, @Nullable Object got, String msg) {
    seat.helper();
    Integer size = sizeOf(got);
    if (size == null) {
      unsupported(seat, mode, msg, got);
      return;
    }
    if (size != 0) {
      Report.to(seat, mode, msg + ": expected empty, got length " + size);
    }
  }

  /// Fail when got holds nothing.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the container that must hold something
  /// @param msg the contract under test
  public static void isNotEmpty(Seat seat, Mode mode, @Nullable Object got, String msg) {
    seat.helper();
    Integer size = sizeOf(got);
    if (size == null) {
      unsupported(seat, mode, msg, got);
      return;
    }
    if (size == 0) {
      Report.to(seat, mode, msg + ": expected non-empty, got length 0");
    }
  }
}
