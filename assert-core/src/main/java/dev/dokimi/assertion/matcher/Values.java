package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Option;
import dev.dokimi.assertion.Seat;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Equality, truth and absence.
///
/// Each matcher takes a seat and a mode, so one implementation serves both surfaces.
@NullMarked
public final class Values {

  private Values() {}

  /// Fail when got and want differ.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the value produced by the code under test
  /// @param want the value it is supposed to produce
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void equal(
      Seat seat,
      Mode mode,
      @Nullable Object got,
      @Nullable Object want,
      String msg,
      Option... options) {
    seat.helper();
    if (!Compare.equal(got, want, Relaxations.of(options))) {
      Report.failure(seat, mode, "equal", msg, Report.detail("want", want, "got", got));
    }
  }

  /// Fail when got and want are equal.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the value produced by the code under test
  /// @param want the value it must not equal
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void notEqual(
      Seat seat,
      Mode mode,
      @Nullable Object got,
      @Nullable Object want,
      String msg,
      Option... options) {
    seat.helper();
    if (Compare.equal(got, want, Relaxations.of(options))) {
      Report.failure(seat, mode, "not-equal", msg, Report.detail("got", got));
    }
  }

  /// Fail when the condition does not hold.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param condition the condition that must hold
  /// @param msg the contract under test
  public static void isTrue(Seat seat, Mode mode, boolean condition, String msg) {
    seat.helper();
    if (!condition) {
      Report.failure(seat, mode, "true", msg);
    }
  }

  /// Fail when the condition holds.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param condition the condition that must not hold
  /// @param msg the contract under test
  public static void isFalse(Seat seat, Mode mode, boolean condition, String msg) {
    seat.helper();
    if (condition) {
      Report.failure(seat, mode, "false", msg);
    }
  }

  /// Fail when got is not null.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the value that must be absent
  /// @param msg the contract under test
  public static void isNull(Seat seat, Mode mode, @Nullable Object got, String msg) {
    seat.helper();
    if (got != null) {
      Report.failure(seat, mode, "nil", msg, Report.detail("got", got));
    }
  }

  /// Fail when got is null.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param got the value that must be present
  /// @param msg the contract under test
  public static void isNotNull(Seat seat, Mode mode, @Nullable Object got, String msg) {
    seat.helper();
    if (got == null) {
      Report.failure(seat, mode, "not-nil", msg);
    }
  }
}
