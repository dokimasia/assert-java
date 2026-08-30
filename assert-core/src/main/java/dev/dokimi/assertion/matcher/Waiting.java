package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
import java.time.Duration;
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
/// These spend real time, deliberately. They are for a condition something outside the
/// test makes true, which is what a controlled clock cannot reach: a fake clock only
/// moves when someone advances it, and nobody will while this is waiting.
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
    long deadline = System.nanoTime() + timeout.toNanos();

    for (int attempt = 1; ; attempt++) {
      Trial trial = new Trial();
      body.accept(trial);

      String failure = trial.message;
      if (failure == null) {
        return;
      }
      if (System.nanoTime() > deadline) {
        Report.to(
            seat,
            mode,
            msg + ": still failing after " + timeout.toMillis() + "ms and " + attempt
                + " attempts: " + failure);
        return;
      }
      if (!sleep(interval)) {
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
    long deadline = System.nanoTime() + timeout.toNanos();
    Duration backoff = Duration.ofMillis(1);
    Duration cap = timeout.dividedBy(4);

    for (int attempt = 1; ; attempt++) {
      if (predicate.getAsBoolean()) {
        return;
      }
      if (System.nanoTime() > deadline) {
        Report.to(
            seat,
            mode,
            msg + ": still false after " + timeout.toMillis() + "ms and " + attempt
                + " attempts");
        return;
      }
      if (!sleep(backoff)) {
        return;
      }
      backoff = backoff.multipliedBy(2);
      if (!cap.isZero() && backoff.compareTo(cap) > 0) {
        backoff = cap;
      }
    }
  }

  /// Wait, answering false when the test's own thread was interrupted.
  private static boolean sleep(Duration duration) {
    try {
      Thread.sleep(Math.max(1, duration.toMillis()));
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
        Report.to(seat, mode, msg + ": still running: " + String.join(", ", names));
      }
    };
  }

  private static Set<Thread> liveThreads() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(thread -> !thread.isDaemon() && thread.isAlive())
        .collect(Collectors.toSet());
  }
}
