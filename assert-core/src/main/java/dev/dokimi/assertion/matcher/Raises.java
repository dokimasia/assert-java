package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Assertions about a call that throws.
///
/// Both take a body rather than a value, because the throw has to happen inside the
/// assertion for it to be caught.
@NullMarked
public final class Raises {

  private Raises() {}

  /// Work that may throw anything, including a checked exception.
  ///
  /// `Runnable` cannot throw a checked exception, so a caller would have to wrap every
  /// method that declares one. This is the interface that spares them.
  @FunctionalInterface
  public interface Body {
    /// Run the work.
    ///
    /// @throws Throwable whatever the work throws
    void run() throws Throwable;
  }

  /// Fail when the body does not throw.
  ///
  /// Any Throwable counts, including an Error. Where the type matters, assert on the
  /// return value or use [Errors#errorAs].
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param body the work under test
  /// @param msg the contract under test
  /// @return what the body threw, or null when it returned
  public static @Nullable Throwable throwsException(
      Seat seat, Mode mode, Body body, String msg) {
    seat.helper();
    try {
      body.run();
    } catch (Throwable thrown) {
      return thrown;
    }
    Report.to(seat, mode, msg + ": returned without throwing");
    return null;
  }

  /// Fail when the body throws.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param body the work under test
  /// @param msg the contract under test
  public static void doesNotThrow(Seat seat, Mode mode, Body body, String msg) {
    seat.helper();
    try {
      body.run();
    } catch (Throwable thrown) {
      Report.to(seat, mode, msg + ": threw " + Show.value(thrown));
    }
  }
}
