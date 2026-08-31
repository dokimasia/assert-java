package dev.dokimi.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The clock a seat carries, and what a test can do with it. */
class ClockTest {

  /** The instant a controlled clock starts at, chosen so a reading cannot pass by accident. */
  private static final Instant EPOCH = Instant.parse("2000-01-01T00:00:00Z");

  @Nested
  @DisplayName("Controlled")
  class ControlledClock {

    @Test
    @DisplayName("reads the start until something advances it")
    void readsTheStart() {
      Controlled clock = new Controlled(EPOCH);

      assertEquals(EPOCH, clock.now());
      clock.advance(Duration.ofHours(1));
      assertEquals(EPOCH.plus(Duration.ofHours(1)), clock.now());
    }

    @Test
    @DisplayName("does not move time backwards")
    void neverGoesBack() {
      Controlled clock = new Controlled(EPOCH);
      clock.advance(Duration.ofHours(-1));

      assertEquals(EPOCH, clock.now());
    }

    @Test
    @DisplayName("releases a sleeper only once the clock passes the duration")
    void sleepWaitsForTheClock() throws InterruptedException {
      Controlled clock = new Controlled(EPOCH);
      CountDownLatch done = new CountDownLatch(1);

      Thread sleeper =
          new Thread(
              () -> {
                try {
                  clock.sleep(Duration.ofMinutes(1));
                  done.countDown();
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                }
              });
      sleeper.start();

      clock.advance(Duration.ofSeconds(30));
      assertFalse(
          done.await(50, TimeUnit.MILLISECONDS),
          "it does not return before the clock reaches the duration");

      // Advancing well past the duration releases the sleeper whichever
      // side of the first advance it started on, which keeps this from
      // turning on thread scheduling.
      clock.advance(Duration.ofHours(1));
      assertTrue(
          done.await(1, TimeUnit.SECONDS), "it returns once the clock passes the duration");
      sleeper.join(TimeUnit.SECONDS.toMillis(1));
    }
  }

  @Nested
  @DisplayName("a seat's clock")
  class SeatClock {

    @Test
    @DisplayName("is the platform clock by default")
    void platformByDefault() {
      assertInstanceOf(SystemClock.class, new Recorder().clock());
    }

    @Test
    @DisplayName("is what withClock supplied")
    void suppliedByWithClock() {
      Recorder seat = new Recorder().withClock(new Controlled(EPOCH));

      assertEquals(EPOCH, seat.clock().now());
    }
  }

  @Nested
  @DisplayName("eventually against a controlled clock")
  class EventuallyOnAControlledClock {

    @Test
    @DisplayName("gives up without spending real time")
    void givesUpAtOnce() {
      Recorder seat = new Recorder().withClock(new Controlled(EPOCH));

      long started = System.nanoTime();
      Check.eventually(
          seat,
          Duration.ofHours(1),
          Duration.ofMinutes(1),
          trial -> trial.fail("never settles"),
          "the body settles");
      Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

      assertTrue(seat.failed(), "a body that never settles is reported");
      assertTrue(
          elapsed.compareTo(Duration.ofSeconds(5)) < 0,
          "an hour of controlled time costs no real waiting, spent " + elapsed);
    }

    @Test
    @DisplayName("stops once the body settles")
    void stopsOnceItSettles() {
      Recorder seat = new Recorder().withClock(new Controlled(EPOCH));
      AtomicInteger attempts = new AtomicInteger();

      Check.eventually(
          seat,
          Duration.ofHours(1),
          Duration.ofMinutes(1),
          trial -> {
            if (attempts.incrementAndGet() < 3) {
              trial.fail("not yet");
            }
          },
          "the body settles");

      assertFalse(seat.failed(), "a body that settles is not reported");
      assertEquals(3, attempts.get(), "it stops once the body comes right");
    }
  }
}
