package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Clock;
import dev.dokimi.assertion.Controlled;
import dev.dokimi.assertion.Seat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Assertions that retry, and the one that checks nothing was left running.
///
/// These read time from the seat's clock. Against the platform clock they spend real
/// time, which is what a condition something outside the test makes true requires.
/// Against a clock a test controls they advance it between attempts instead, so the
/// retrying costs nothing; that only helps where the subject reads the same clock.
@NullMarked
public final class Waiting {

  private Waiting() {}

  /// A seat that keeps one trial's failure instead of reporting it.
  ///
  /// Not the public recorder: a retry loop needs nothing more than whether the attempt
  /// failed and with what.
  private static final class Trial implements Seat {
    private @Nullable String message;

    @Override
    public void fail(String text) {
      if (message == null) {
        message = text;
      }
    }

    @Override
    public void record(String text) {
      fail(text);
    }
  }

  /// Fail when a body of assertions never passes within the timeout.
  ///
  /// The body is handed a seat of its own, so assertions inside it record an attempt
  /// rather than ending the test. It runs at least once however short the timeout, and
  /// the failure carries the last attempt's own reason rather than a bare timeout.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param timeout how long to keep retrying
  /// @param interval how long to wait between attempts
  /// @param body states the condition as assertions, against the seat it is handed
  /// @param msg the contract under test
  public static void eventually(
      Seat seat,
      Mode mode,
      Duration timeout,
      Duration interval,
      Consumer<Seat> body,
      String msg) {
    seat.helper();
    Clock clock = Report.clockOf(seat);
    Instant deadline = clock.now().plus(timeout);

    for (int attempt = 1; ; attempt++) {
      Trial trial = new Trial();
      body.accept(trial);

      String failure = trial.message;
      if (failure == null) {
        return;
      }
      if (clock.now().isAfter(deadline)) {
        Report.failure(seat, mode, "eventually", msg,
            Report.detail("attempts", attempt, "last", failure));
        return;
      }
      if (!wait(clock, interval)) {
        return;
      }
    }
  }

  /// Fail when a predicate never becomes true within the timeout.
  ///
  /// Retried with a backoff that starts at a millisecond and doubles, capped at a quarter
  /// of the timeout so the last attempts are not one long sleep. A predicate carries no
  /// reason, so the failure says only that the wait ran out.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param timeout how long to keep retrying
  /// @param predicate must eventually answer true
  /// @param msg the contract under test
  public static void eventuallyTrue(
      Seat seat, Mode mode, Duration timeout, BooleanSupplier predicate, String msg) {
    seat.helper();
    Clock clock = Report.clockOf(seat);
    Instant deadline = clock.now().plus(timeout);
    Duration backoff = Duration.ofMillis(1);
    Duration cap = timeout.dividedBy(4);

    for (int attempt = 1; ; attempt++) {
      if (predicate.getAsBoolean()) {
        return;
      }
      if (clock.now().isAfter(deadline)) {
        Report.failure(seat, mode, "eventually-true", msg,
            Report.detail("attempts", attempt));
        return;
      }
      if (!wait(clock, backoff)) {
        return;
      }
      backoff = backoff.multipliedBy(2);
      if (!cap.isZero() && backoff.compareTo(cap) > 0) {
        backoff = cap;
      }
    }
  }

  /// Move time forward by the duration.
  ///
  /// A clock a test controls is advanced, because nothing else will move it
  /// while this call is running. Any other clock is slept against.
  ///
  /// @param clock where time is read
  /// @param duration how far to move forward
  /// @return false when the thread was interrupted while waiting
  private static boolean wait(Clock clock, Duration duration) {
    if (clock instanceof Controlled moving) {
      moving.advance(duration);
      return true;
    }
    try {
      clock.sleep(Duration.ofMillis(Math.max(1, duration.toMillis())));
      return true;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /// Answer a callable that fails when work started in the scope outlives it.
  ///
  /// Reads the live non-daemon threads either side of the scope. Daemon threads are
  /// excluded because the carrier threads a virtual thread runs on are daemons, and
  /// counting those would report a leak for every subject that starts one.
  ///
  /// A leaked virtual thread is not reported. Virtual threads appear in no standard
  /// enumeration on any JVM version, which the standard's overlay records as a limit
  /// rather than leaving for someone to discover.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param msg the contract under test
  /// @return a callable to invoke where the scope ends
  public static Runnable noTaskLeaks(Seat seat, Mode mode, String msg) {
    seat.helper();
    Set<Thread> before = liveThreads();

    return () -> {
      seat.helper();
      Set<Thread> leaked = new HashSet<>(liveThreads());
      leaked.removeAll(before);

      if (!leaked.isEmpty()) {
        List<String> names =
            leaked.stream().map(Thread::getName).sorted().collect(Collectors.toList());
        Report.failure(seat, mode, "no-task-leaks", msg, Report.detail("leaked", names));
      }
    };
  }

  private static Set<Thread> liveThreads() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(thread -> !thread.isDaemon() && thread.isAlive())
        .collect(Collectors.toSet());
  }
}
