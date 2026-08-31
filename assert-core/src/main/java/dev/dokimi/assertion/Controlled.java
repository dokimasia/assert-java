package dev.dokimi.assertion;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;

/// A clock that moves only when a test advances it.
///
/// [#now()] answers what [#advance(Duration)] last left it at, and [#sleep(Duration)]
/// blocks until the clock has passed the duration rather than until the wall
/// has. An assertion that retries advances this clock between attempts rather
/// than sleeping against it, so a body that settles on the third attempt costs
/// three attempts and no waiting.
///
/// A controlled clock cannot reach the subject: code under test that calls the
/// platform directly reads a different now, and nothing here detects that.
///
/// Every method is safe to call from any thread.
@NullMarked
public final class Controlled implements Clock {

  private final Object woke = new Object();
  private Instant instant;

  /// Start a clock reading start until it is advanced.
  ///
  /// @param start the instant it reads before anything advances it
  public Controlled(Instant start) {
    this.instant = start;
  }

  /// Answer the instant this clock was last advanced to.
  ///
  /// @return the instant, as advance has left it
  @Override
  public Instant now() {
    synchronized (woke) {
      return instant;
    }
  }

  /// Move the clock forward and wake everything the new instant passed.
  ///
  /// A duration that is not positive does not move it backwards; time on this
  /// clock only goes forward.
  ///
  /// @param duration how far to move forward
  public void advance(Duration duration) {
    if (duration.isNegative() || duration.isZero()) {
      return;
    }
    synchronized (woke) {
      instant = instant.plus(duration);
      woke.notifyAll();
    }
  }

  /// Block until the clock has passed the duration.
  ///
  /// It returns at once when the duration is not positive. Otherwise it waits
  /// for another thread to advance the clock, so a test that sleeps on the only
  /// thread it has blocks until something advances it.
  ///
  /// The duration is measured from the instant this reads, so a caller racing
  /// sleep against advance on two threads cannot say which instant it slept
  /// from. Assertions do not hit this: one that retries advances the clock
  /// itself, on the thread it is already running on.
  ///
  /// @param duration how long to wait
  /// @throws InterruptedException if the waiting thread is interrupted
  @Override
  public void sleep(Duration duration) throws InterruptedException {
    if (duration.isNegative() || duration.isZero()) {
      return;
    }
    synchronized (woke) {
      Instant until = instant.plus(duration);
      while (instant.isBefore(until)) {
        woke.wait();
      }
    }
  }
}
