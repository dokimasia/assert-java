package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
import org.jspecify.annotations.NullMarked;

/// Sending one failure to a seat, under a mode.
@NullMarked
public final class Report {

  private Report() {}

  /// Send one failure to the seat.
  ///
  /// This decides nothing about whether anything failed. A matcher calls it only once
  /// its own comparison has failed, so every call produces exactly one reported failure.
  /// Under [Mode#FATAL] it may not return.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param message the failure text, already formatted
  public static void to(Seat seat, Mode mode, String message) {
    seat.helper();
    if (mode == Mode.SOFT) {
      seat.record(message);
      return;
    }
    seat.fail(message);
  }
}
