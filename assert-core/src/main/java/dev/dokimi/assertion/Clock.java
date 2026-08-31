package dev.dokimi.assertion;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;

/// Where an assertion reads time.
///
/// An assertion that waits, retries or measures reads it here rather than
/// calling the platform, so a test can supply time it controls and a busy
/// machine cannot make the assertion flaky.
@NullMarked
public interface Clock {

  /// Answer the current instant.
  ///
  /// @return a reading comparable against another from the same clock
  Instant now();

  /// Block until the duration has passed on this clock.
  ///
  /// @param duration how long to wait
  /// @throws InterruptedException if the waiting thread is interrupted
  void sleep(Duration duration) throws InterruptedException;
}
