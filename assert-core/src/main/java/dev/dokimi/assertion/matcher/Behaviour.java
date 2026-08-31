package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Option;
import dev.dokimi.assertion.Seat;
import java.time.Instant;
import dev.dokimi.assertion.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// How a subject behaves, rather than what it answers.
///
/// Go states cancellation with a `context.Context` in every signature. Java's equivalent
/// is interruption: `Thread.interrupt` is what `sleep`, `wait`, `take` and every blocking
/// call in `java.util.concurrent` respond to, so a subject that can be cancelled at all
/// responds to it.
@NullMarked
public final class Behaviour {

  /// How long to wait for an interrupted subject to stop before calling it unresponsive.
  private static final Duration NOTICE = Duration.ofSeconds(1);

  private Behaviour() {}

  /// Work that takes a cancellation handle, which may be absent.
  ///
  /// Named rather than a `Consumer<Object>`, so a caller can see from the signature
  /// what they are handed and that it may be null.
  @FunctionalInterface
  public interface Handled {
    /// Run the work.
    ///
    /// @param handle the cancellation handle, or null
    /// @throws Throwable whatever the work throws
    void run(@Nullable Supplier<Boolean> handle) throws Throwable;
  }

  /// Work that takes a cancellation handle and may throw.
  @FunctionalInterface
  public interface Cancellable {
    /// Run the work.
    ///
    /// @param cancelled whether the caller has asked the work to stop
    /// @throws Throwable whatever the work throws
    void run(Supplier<Boolean> cancelled) throws Throwable;
  }

  /// Fail when a subject told to stop does not.
  ///
  /// The subject is started on its own thread and interrupted at once, so this asks
  /// whether it checks at all rather than how quickly it notices. A subject that ignores
  /// interruption runs to completion and fails here.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param body the work under test
  /// @param msg the contract under test
  public static void honoursCancellation(Seat seat, Mode mode, Cancellable body, String msg) {
    seat.helper();
    drive(seat, mode, body, msg, "honours-cancellation");
  }

  /// Fail when a subject given no time does not stop.
  ///
  /// Java has no deadline in its signatures the way Go has one in a context, so the
  /// deadline is expressed the only way the platform expresses it: the subject is
  /// interrupted before it starts, and must stop.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param body the work under test
  /// @param msg the contract under test
  public static void honoursDeadline(Seat seat, Mode mode, Cancellable body, String msg) {
    seat.helper();
    drive(seat, mode, body, msg, "honours-deadline");
  }

  /// Run the body against a handle that says to stop, and report when it does
  /// not say it stopped for that reason.
  ///
  /// The subject is asked whether it reports a cancellation, not merely whether
  /// it returns: one that never reads the handle and answers success has done
  /// the work it was told to abandon. It runs on a thread of its own so a
  /// subject that blocks forever is reported rather than hanging the test,
  /// which a direct call cannot do.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param body the subject under test
  /// @param msg the contract under test
  /// @param assertion the canonical id being driven
  private static void drive(
      Seat seat, Mode mode, Cancellable body, String msg, String assertion) {

    AtomicReference<@Nullable Throwable> raised = new AtomicReference<>();
    AtomicBoolean returned = new AtomicBoolean();

    Thread worker =
        new Thread(
            () -> {
              try {
                body.run(() -> true);
                returned.set(true);
              } catch (Throwable thrown) {
                raised.set(thrown);
              }
            });
    worker.setDaemon(true);
    worker.start();
    worker.interrupt();

    try {
      worker.join(NOTICE.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return;
    }

    if (worker.isAlive()) {
      Report.failure(
          seat, mode, assertion, msg, Report.detail("got", "did not stop within " + NOTICE));
      return;
    }
    Throwable thrown = raised.get();
    if (thrown != null) {
      if (!stopped(thrown)) {
        Report.failure(seat, mode, assertion, msg, Report.detail("got", thrown));
      }
      return;
    }
    if (returned.get()) {
      Report.failure(
          seat,
          mode,
          assertion,
          msg,
          Report.detail("got", "returned as if it had done the work"));
    }
  }

  /// Whether a throwable is how this platform says a caller gave up or ran out
  /// of time.
  ///
  /// @param raised what the subject threw
  /// @return whether it names a stop
  private static boolean stopped(Throwable raised) {
    for (Throwable at = raised; at != null; at = at.getCause()) {
      if (at instanceof InterruptedException
          || at instanceof java.util.concurrent.CancellationException
          || at instanceof java.util.concurrent.TimeoutException) {
        return true;
      }
      if (at == at.getCause()) {
        break;
      }
    }
    return false;
  }

  /// Fail when the body takes longer than the given duration.
  ///
  /// The body is measured, not interrupted: one that runs long runs to completion and
  /// then fails. This spends real time, up to however long the body takes.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param within the ceiling
  /// @param body the work under test
  /// @param msg the contract under test
  public static void completesWithin(
      Seat seat, Mode mode, Duration within, Raises.Body body, String msg) {
    seat.helper();
    Clock clock = Report.clockOf(seat);
    Instant started = clock.now();
    try {
      body.run();
    } catch (Throwable ignored) {
      // Failing quickly is still finishing, so the measurement stands.
      // Which failure is acceptable is another assertion's question.
    }
    Duration elapsed = Duration.between(started, clock.now());

    if (elapsed.compareTo(within) > 0) {
      // Milliseconds is the granularity this assertion is about.
      Report.failure(seat, mode, "completes-within", msg,
          Report.detail("want", within.toMillis(), "got", elapsed.toMillis()));
    }
  }

  /// Fail when a subject given no cancellation handle crashes.
  ///
  /// Throwing an exception of its own is fine and is usually right. What fails here is
  /// dereferencing the missing handle, which is what a caller does by accident.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param body called with a null handle
  /// @param msg the contract under test
  public static void nullHandleSafe(Seat seat, Mode mode, Handled body, String msg) {
    seat.helper();
    try {
      body.run(null);
    } catch (NullPointerException crashed) {
      Report.failure(seat, mode, "nil-context-safe", msg, Report.detail("got", crashed));
    } catch (Throwable ignored) {
      // An exception of its own is the subject declining, not crashing.
    }
  }

  /// Fail when the body changes what observe reads.
  ///
  /// What observe answers defines what nothing means: whatever it leaves out, the body is
  /// free to change. Answer a copy, because a projection sharing memory with the subject
  /// reads the same object twice and passes whatever the body did.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param observe read before and after the body
  /// @param body the call that must change nothing observed
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void isPure(
      Seat seat,
      Mode mode,
      Callable<@Nullable Object> observe,
      Raises.Body body,
      String msg,
      Option... options) {
    seat.helper();
    try {
      Object before = observe.call();
      body.run();
      Object after = observe.call();

      if (!Compare.equal(after, before, Relaxations.of(options))) {
        Report.failure(seat, mode, "pure", msg, Report.detail("want", before, "got", after));
      }
    } catch (Throwable thrown) {
      Report.failure(seat, mode, "pure", msg, Report.detail("want", null, "got", thrown));
    }
  }
}
