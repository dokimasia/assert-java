package dev.dokimi.assertion.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Recorder;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Retrying, and the check that nothing was left running.
class WaitingTest {

  private static final Mode ABORTS = Mode.FATAL;

  private static final Duration BRIEF = Duration.ofMillis(200);
  private static final Duration TICK = Duration.ofMillis(1);

  @Test
  @DisplayName("eventually passes once the body stops failing")
  void eventuallyPasses() {
    AtomicInteger attempts = new AtomicInteger();

    Recorder seat = new Recorder();
    Waiting.eventually(
        seat,
        ABORTS,
        BRIEF,
        TICK,
        trial -> {
          if (attempts.incrementAndGet() < 3) {
            trial.fail("not ready yet");
          }
        },
        "the queue drains");

    assertFalse(seat.failed(), seat.message());
    assertTrue(attempts.get() >= 3, "the body has to be retried, not called once");
  }

  @Test
  @DisplayName("eventually carries the last attempt's own reason, not a bare timeout")
  void eventuallyCarriesTheReason() {
    Recorder seat = new Recorder();
    Waiting.eventually(
        seat, ABORTS, BRIEF, TICK, trial -> trial.fail("queue still holds 4"), "the queue drains");

    assertTrue(seat.failed(), "a body that never passes must be reported");
    assertTrue(seat.message().contains("queue still holds 4"), seat.message());
    assertTrue(seat.message().contains("attempts"), seat.message());
  }

  @Test
  @DisplayName("eventually runs the body once however short the timeout")
  void eventuallyRunsAtLeastOnce() {
    AtomicInteger attempts = new AtomicInteger();

    Recorder seat = new Recorder();
    Waiting.eventually(
        seat, ABORTS, Duration.ZERO, TICK, trial -> attempts.incrementAndGet(), "the queue drains");

    assertFalse(seat.failed(), seat.message());
    assertTrue(attempts.get() == 1, "a zero timeout still gets one attempt");
  }

  @Test
  @DisplayName("a body that records rather than fails still counts as a failed attempt")
  void recordingCountsAsFailing() {
    Recorder seat = new Recorder();
    Waiting.eventually(
        seat,
        ABORTS,
        Duration.ZERO,
        TICK,
        trial -> trial.record("queue still holds 4"),
        "the queue drains");

    assertTrue(seat.failed(), "a soft assertion inside the body is still the attempt failing");
  }

  @Test
  @DisplayName("eventuallyTrue passes once the predicate flips and reports one that never does")
  void eventuallyTrue() {
    AtomicInteger calls = new AtomicInteger();

    Recorder passing = new Recorder();
    Waiting.eventuallyTrue(
        passing, ABORTS, BRIEF, () -> calls.incrementAndGet() >= 3, "the file appears");
    assertFalse(passing.failed(), passing.message());
    assertTrue(calls.get() >= 3, "the predicate has to be retried");

    Recorder failing = new Recorder();
    Waiting.eventuallyTrue(failing, ABORTS, BRIEF, () -> false, "the file appears");
    assertTrue(failing.failed(), "a predicate that never holds must be reported");
    assertTrue(failing.message().contains("still false"), failing.message());
  }

  @Test
  @DisplayName("noTaskLeaks passes a scope that starts nothing")
  void noLeak() {
    Recorder seat = new Recorder();
    Runnable ended = Waiting.noTaskLeaks(seat, ABORTS, "handle starts nothing of its own");
    ended.run();

    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("noTaskLeaks names a thread still running when the scope ends")
  void leakIsNamed() throws InterruptedException {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);

    Recorder seat = new Recorder();
    Runnable ended = Waiting.noTaskLeaks(seat, ABORTS, "handle starts nothing of its own");

    Thread leaked =
        new Thread(
            () -> {
              started.countDown();
              try {
                release.await();
              } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
              }
            },
            "leaked-worker");
    leaked.setDaemon(false);
    leaked.start();
    started.await();

    try {
      ended.run();
      assertTrue(seat.failed(), "work outliving the scope must be reported");
      assertTrue(seat.message().contains("leaked-worker"), seat.message());
    } finally {
      release.countDown();
      leaked.join(Duration.ofSeconds(5).toMillis());
    }
  }

  @Test
  @DisplayName("a thread that finishes inside the scope is not a leak")
  void finishedWorkIsNotALeak() throws InterruptedException {
    Recorder seat = new Recorder();
    Runnable ended = Waiting.noTaskLeaks(seat, ABORTS, "handle cleans up after itself");

    Thread worker = new Thread(() -> {}, "tidy-worker");
    worker.setDaemon(false);
    worker.start();
    worker.join();

    ended.run();
    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("a daemon thread is not counted, because a carrier thread is one")
  void daemonsAreNotCounted() throws InterruptedException {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);

    Recorder seat = new Recorder();
    Runnable ended = Waiting.noTaskLeaks(seat, ABORTS, "handle starts nothing of its own");

    Thread carrier =
        new Thread(
            () -> {
              started.countDown();
              try {
                release.await();
              } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
              }
            },
            "carrier-lookalike");
    carrier.setDaemon(true);
    carrier.start();
    started.await();

    try {
      ended.run();
      assertFalse(seat.failed(), seat.message());
    } finally {
      release.countDown();
      carrier.join(Duration.ofSeconds(5).toMillis());
    }
  }
}
