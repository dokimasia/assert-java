package dev.dokimi.assertion.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Option;
import dev.dokimi.assertion.Recorder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// How a subject behaves, rather than what it answers.
///
/// Each assertion is driven with a subject that honours it and one that does not, and
/// the honouring subject records that it ran. Both halves are needed: an assertion that
/// arranges cancellation before the subject starts reports nothing whatever it is
/// handed, and reads "it did not finish" as "it stopped when told". That bug has shipped
/// in three languages, each time under a test that only drove the passing case.
class BehaviourTest {

  private static final Mode ABORTS = Mode.FATAL;

  /// Longer than the second the assertion waits, so an unresponsive subject outlives it.
  private static final long IGNORES_THE_ASK_MS = 1_500;

  /// A bound on the polling loop, so a broken assertion fails rather than hanging.
  private static final long GIVES_UP_MS = 5_000;

  /// A subject that polls the handle and stops when it is told to.
  private static Behaviour.Cancellable stopsWhenTold(AtomicBoolean ran) {
    return cancelled -> {
      ran.set(true);
      long until = System.nanoTime() + Duration.ofMillis(GIVES_UP_MS).toNanos();
      while (!cancelled.get() && System.nanoTime() < until) {
        Thread.onSpinWait();
      }
    };
  }

  /// A subject that never looks at the handle and runs to completion.
  private static Behaviour.Cancellable ignoresTheAsk(AtomicBoolean ran) {
    return cancelled -> {
      ran.set(true);
      long until = System.nanoTime() + Duration.ofMillis(IGNORES_THE_ASK_MS).toNanos();
      while (System.nanoTime() < until) {
        Thread.onSpinWait();
      }
    };
  }

  @Test
  @DisplayName("honoursCancellation passes a subject that stops, and it ran")
  void cancellationIsHonoured() {
    AtomicBoolean ran = new AtomicBoolean();

    Recorder seat = new Recorder();
    Behaviour.honoursCancellation(seat, ABORTS, stopsWhenTold(ran), "the worker stops when told");

    assertFalse(seat.failed(), seat.message());
    assertTrue(ran.get(), "the subject has to run, or passing means nothing");
  }

  @Test
  @DisplayName("honoursCancellation reports a subject that ignores the ask")
  void cancellationIsIgnored() {
    AtomicBoolean ran = new AtomicBoolean();

    Recorder seat = new Recorder();
    Behaviour.honoursCancellation(seat, ABORTS, ignoresTheAsk(ran), "the worker stops when told");

    assertTrue(ran.get(), "the subject has to run");
    assertTrue(seat.failed(), "a subject that never checks must be reported");
    assertTrue(seat.message().contains("did not stop"), seat.message());
  }

  @Test
  @DisplayName("honoursDeadline passes a subject that stops, and it ran")
  void deadlineIsHonoured() {
    AtomicBoolean ran = new AtomicBoolean();

    Recorder seat = new Recorder();
    Behaviour.honoursDeadline(seat, ABORTS, stopsWhenTold(ran), "the worker respects no time");

    assertFalse(seat.failed(), seat.message());
    assertTrue(ran.get(), "the subject has to run, or passing means nothing");
  }

  @Test
  @DisplayName("honoursDeadline reports a subject that runs on regardless")
  void deadlineIsIgnored() {
    AtomicBoolean ran = new AtomicBoolean();

    Recorder seat = new Recorder();
    Behaviour.honoursDeadline(seat, ABORTS, ignoresTheAsk(ran), "the worker respects no time");

    assertTrue(ran.get(), "the subject has to run");
    assertTrue(seat.failed(), "a subject given no time that runs anyway must be reported");
  }

  @Test
  @DisplayName("a subject that blocks on sleep is stopped by the interrupt")
  void interruptionReachesABlockedSubject() {
    AtomicBoolean ran = new AtomicBoolean();

    Recorder seat = new Recorder();
    Behaviour.honoursCancellation(
        seat,
        ABORTS,
        cancelled -> {
          ran.set(true);
          Thread.sleep(GIVES_UP_MS);
        },
        "the worker stops while waiting");

    assertFalse(seat.failed(), seat.message());
    assertTrue(ran.get(), "the subject has to run");
  }

  @Test
  @DisplayName("completesWithin passes a quick body and reports a slow one")
  void completesWithin() {
    Recorder passing = new Recorder();
    Behaviour.completesWithin(passing, ABORTS, Duration.ofSeconds(10), () -> {}, "get stays quick");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Behaviour.completesWithin(
        failing, ABORTS, Duration.ofMillis(1), () -> Thread.sleep(60), "get stays quick");

    assertTrue(failing.failed(), "a body over the ceiling must be reported");
    assertTrue(failing.message().contains("want at most 1ms"), failing.message());
  }

  @Test
  @DisplayName("completesWithin measures a body that throws, because failing fast is finishing")
  void completesWithinMeasuresAThrow() {
    Recorder seat = new Recorder();
    Behaviour.completesWithin(
        seat,
        ABORTS,
        Duration.ofSeconds(10),
        () -> {
          throw new IllegalStateException("refused");
        },
        "get stays quick");

    assertFalse(seat.failed(), "which failure it was is another assertion's question");
  }

  @Test
  @DisplayName("nullHandleSafe passes a subject that declines and reports one that crashes")
  void nullHandleSafe() {
    Recorder returning = new Recorder();
    Behaviour.nullHandleSafe(returning, ABORTS, handle -> {}, "get survives a missing handle");
    assertFalse(returning.failed(), returning.message());

    Recorder declining = new Recorder();
    Behaviour.nullHandleSafe(
        declining,
        ABORTS,
        handle -> {
          throw new IllegalArgumentException("a handle is required");
        },
        "get survives a missing handle");
    assertFalse(declining.failed(), "an exception of its own is declining, not crashing");

    Recorder crashing = new Recorder();
    Behaviour.nullHandleSafe(
        crashing,
        ABORTS,
        handle -> {
          Supplier<Boolean> cancelled = handle;
          cancelled.get();
        },
        "get survives a missing handle");

    assertTrue(crashing.failed(), "dereferencing the missing handle must be reported");
    assertTrue(crashing.message().contains("missing handle"), crashing.message());
  }

  @Test
  @DisplayName("nullHandleSafe reports a crash inside a checked exception too")
  void nullHandleSafeWidensToThrowable() {
    Recorder seat = new Recorder();
    Behaviour.nullHandleSafe(
        seat,
        ABORTS,
        handle -> {
          throw new java.io.IOException("declined");
        },
        "get survives a missing handle");

    assertFalse(seat.failed(), "a checked exception is still the subject declining");
  }

  @Test
  @DisplayName("isPure passes an untouched projection and reports a changed one")
  void isPure() {
    List<String> store = new ArrayList<>(List.of("a", "b"));

    Recorder passing = new Recorder();
    Behaviour.isPure(passing, ABORTS, () -> List.copyOf(store), () -> store.size(), "count reads");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Behaviour.isPure(
        failing, ABORTS, () -> List.copyOf(store), () -> store.add("c"), "count reads");

    assertTrue(failing.failed(), "a changed projection must be reported");
    assertTrue(failing.message().contains("observable state changed"), failing.message());
  }

  @Test
  @DisplayName("a projection sharing memory with the subject reads the same object twice")
  void aLiveProjectionSeesNothing() {
    // What the docblock warns about, pinned so it stays a known limit rather
    // than something someone rediscovers. Answering `store` rather than a copy
    // reads one object either side of the body, so it compares equal to itself
    // whatever the body did.
    List<String> store = new ArrayList<>(List.of("a"));

    Recorder seat = new Recorder();
    Behaviour.isPure(seat, ABORTS, () -> store, () -> store.add("b"), "count reads");

    assertFalse(seat.failed(), "a live projection cannot see the change, which is why it is wrong");
    assertTrue(store.contains("b"), "the body did change the store");
  }

  @Test
  @DisplayName("isPure reports an observation that throws rather than passing")
  void isPureWhenObservingThrows() {
    Recorder seat = new Recorder();
    Behaviour.isPure(
        seat,
        ABORTS,
        () -> {
          throw new IllegalStateException("store is closed");
        },
        () -> {},
        "count reads");

    assertTrue(seat.failed(), "an observation that cannot be made proves nothing");
    assertTrue(seat.message().contains("observing threw"), seat.message());
  }

  @Test
  @DisplayName("isPure takes the same relaxations as equality")
  void isPureTakesOptions() {
    // Read as a primitive and boxed on the way out, which is what a real
    // projection does, so the two readings are distinct objects and the
    // comparison is the one the relaxation governs.
    double[] readings = {Double.NaN};

    Recorder strict = new Recorder();
    Behaviour.isPure(strict, ABORTS, () -> readings[0], () -> {}, "the gauge is only read");
    assertTrue(strict.failed(), "NaN does not equal itself, so an untouched NaN reads as a change");

    Recorder relaxed = new Recorder();
    Behaviour.isPure(
        relaxed, ABORTS, () -> readings[0], () -> {}, "the gauge is only read", Option.EQUATE_NANS);
    assertFalse(relaxed.failed(), relaxed.message());
  }
}
