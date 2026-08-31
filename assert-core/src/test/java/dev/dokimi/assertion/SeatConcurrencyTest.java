package dev.dokimi.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seats, driven from more than one thread.
 *
 * <p>A test holds one seat and hands it to every assertion in the body, and
 * several of those run the subject somewhere else: one retries a body, one
 * watches for work that outlives its scope, one gives a subject a handle and
 * waits. A seat that lost a failure because two arrived at once would report the
 * wrong answer and no test would see why.
 */
class SeatConcurrencyTest {

  /** How many threads report at once. */
  private static final int WRITERS = 8;

  /** How many failures each of them reports. */
  private static final int EACH = 2_000;

  /** Run body on every writer thread, and wait for all of them. */
  private static void inParallel(Runnable body) throws InterruptedException {
    CountDownLatch ready = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(WRITERS);

    IntStream.range(0, WRITERS)
        .forEach(
            worker ->
                new Thread(
                        () -> {
                          try {
                            // Start together, so the threads contend
                            // rather than run one after another.
                            ready.await();
                            body.run();
                          } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                          } finally {
                            done.countDown();
                          }
                        })
                    .start());

    ready.countDown();
    if (!done.await(30, TimeUnit.SECONDS)) {
      throw new IllegalStateException("the writers did not finish");
    }
  }

  @Test
  @DisplayName("a Recorder keeps every failure reported from many threads")
  void recorderKeepsEveryFailure() throws InterruptedException {
    Recorder seat = new Recorder();
    inParallel(
        () -> {
          for (int at = 0; at < EACH; at++) {
            seat.record("a failure");
          }
        });

    assertEquals(WRITERS * EACH, seat.messages().size());
  }

  @Test
  @DisplayName("a Recorder counts every helper mark from many threads")
  void recorderCountsEveryMark() throws InterruptedException {
    Recorder seat = new Recorder();
    inParallel(
        () -> {
          for (int at = 0; at < EACH; at++) {
            seat.helper();
          }
        });

    assertEquals(WRITERS * EACH, seat.helperCalls());
  }

  @Test
  @DisplayName("a Collector keeps every failure recorded from many threads")
  void collectorKeepsEveryFailure() throws InterruptedException {
    Collector seat = new Collector();
    inParallel(
        () -> {
          for (int at = 0; at < EACH; at++) {
            seat.record("a failure");
          }
        });

    assertEquals(WRITERS * EACH, seat.collected().size());
  }
}
