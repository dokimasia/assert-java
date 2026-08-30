package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Option;
import dev.dokimi.assertion.Seat;
import java.time.Duration;
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
    drive(seat, mode, body, msg, "an interrupted subject");
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
    drive(seat, mode, body, msg, "a subject given no time");
  }

  private static void drive(
      Seat seat, Mode mode, Cancellable body, String msg, String subject) {

    Thread worker =
        new Thread(
            () -> {
              try {
                body.run(() -> Thread.currentThread().isInterrupted());
              } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
              } catch (Throwable ignored) {
                // Any other throwable still means the subject stopped,
                // which is what is being asked. Which throwable it was
                // is a question for another assertion.
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
      Report.to(seat, mode, msg + ": " + subject + " did not stop within " + NOTICE);
    }
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
    long started = System.nanoTime();
    try {
      body.run();
    } catch (Throwable ignored) {
      // Failing quickly is still finishing, so the measurement stands.
      // Which failure is acceptable is another assertion's question.
    }
    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

    if (elapsed.compareTo(within) > 0) {
      Report.to(
          seat,
          mode,
          msg + ": took " + elapsed.toMillis() + "ms, want at most " + within.toMillis() + "ms");
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
      Report.to(seat, mode, msg + ": a missing handle caused " + Show.value(crashed));
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
        Report.to(
            seat,
            mode,
            msg + ": observable state changed: was " + Show.value(before)
                + ", now " + Show.value(after));
      }
    } catch (Throwable thrown) {
      Report.to(seat, mode, msg + ": observing threw " + Show.value(thrown));
    }
  }
}
