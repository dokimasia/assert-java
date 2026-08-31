package dev.dokimi.assertion;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;

/// Reads the platform clock.
///
/// This is what an assertion gets when the seat carries no other, so an
/// assertion that reads time behaves as it did before a clock existed.
///
/// Named SystemClock rather than System because [java.lang.System] is
/// imported into every class by default, and a type called System here
/// would make every class in this package qualify it.
@NullMarked
public final class SystemClock implements Clock {

  /// Read the platform clock.
  public SystemClock() {}

  /// Answer the platform's current instant.
  ///
  /// @return the instant the platform reports
  @Override
  public Instant now() {
    return Instant.now();
  }

  /// Wait for the duration against the platform clock.
  ///
  /// @param duration how long to wait
  /// @throws InterruptedException if the waiting thread is interrupted
  @Override
  public void sleep(Duration duration) throws InterruptedException {
    if (duration.isNegative() || duration.isZero()) {
      return;
    }
    Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000);
  }
}
