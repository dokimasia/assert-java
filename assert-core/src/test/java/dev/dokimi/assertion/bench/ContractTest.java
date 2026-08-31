package dev.dokimi.assertion.bench;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.dokimi.assertion.Recorder;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Performance ceilings, and what happens when one is crossed.
///
/// Every ceiling is stated twice: once loose enough that any machine meets it, and once
/// tight enough that none does. Pinning a real number instead would fail on a busy
/// machine and teach people to rerun the suite.
class ContractTest {

  /// Held in a field so the allocation is really made rather than optimised away.
  @SuppressWarnings("unused")
  private static volatile Object sink;

  /// Enough samples for the p99 to be a percentile rather than the slowest reading.
  private static final int SAMPLES = 100;

  /// Whether this JVM counts bytes allocated per thread.
  private static boolean measuresAllocation() {
    return ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
        && bean.isThreadAllocatedMemorySupported()
        && bean.isThreadAllocatedMemoryEnabled();
  }

  @Test
  @DisplayName("a run inside every ceiling reports nothing")
  void withinTheCeilings() {
    Recorder seat = new Recorder();
    new Contract(seat, "get stays quick")
        .maxLatency(Duration.ofSeconds(10))
        .maxMean(Duration.ofSeconds(10))
        .loop(SAMPLES, () -> {})
        .check();

    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("a crossed latency ceiling names the p99 and how many iterations ran")
  void latencyCrossed() {
    Recorder seat = new Recorder();
    new Contract(seat, "get stays quick")
        .maxLatency(Duration.ofNanos(1))
        .loop(SAMPLES, () -> Thread.sleep(1))
        .check();

    assertTrue(seat.failed(), "a p99 over the ceiling must be reported");
    assertEquals("bench-max-latency", seat.failures().get(0).assertion(), seat.message());
  }

  @Test
  @DisplayName("a crossed mean ceiling is reported separately from the tail")
  void meanCrossed() {
    Recorder seat = new Recorder();
    new Contract(seat, "get stays quick")
        .maxMean(Duration.ofNanos(1))
        .loop(SAMPLES, () -> Thread.sleep(1))
        .check();

    assertTrue(seat.failed(), "a mean over the ceiling must be reported");
    assertEquals("bench-max-mean", seat.failures().get(0).assertion(), seat.message());
  }

  @Test
  @DisplayName("a body that allocates nothing meets a byte ceiling")
  void bytesWithin() {
    assumeTrue(measuresAllocation(), "this JVM does not count bytes allocated per thread");

    Recorder seat = new Recorder();
    new Contract(seat, "get allocates little").maxBytes(1_000_000).loop(1_000, () -> {}).check();

    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("a crossed byte ceiling names what was allocated per iteration")
  void bytesCrossed() {
    assumeTrue(measuresAllocation(), "this JVM does not count bytes allocated per thread");

    Recorder seat = new Recorder();
    new Contract(seat, "get allocates little")
        .maxBytes(8)
        .loop(1_000, () -> sink = new byte[4_096])
        .check();

    assertTrue(seat.failed(), "an allocation over the ceiling must be reported");
    assertTrue(named(seat, "bench-max-bytes"), seat.message());
    assertEquals(8L, seat.failures().get(0).detail().get("want"), seat.message());
  }

  @Test
  @DisplayName("checking without running says nothing was measured")
  void nothingMeasured() {
    Recorder seat = new Recorder();
    new Contract(seat, "get stays quick").maxLatency(Duration.ofSeconds(10)).check();

    assertTrue(seat.failed(), "a contract nobody ran proves nothing");
    assertTrue(seat.message().contains("nothing was measured"), seat.message());
  }

  @Test
  @DisplayName("a body that throws is reported rather than timed")
  void bodyThrows() {
    Recorder seat = new Recorder();
    new Contract(seat, "get stays quick")
        .maxLatency(Duration.ofSeconds(10))
        .loop(
            10,
            () -> {
              throw new IllegalStateException("store is closed");
            })
        .check();

    assertTrue(seat.failed(), "a body that cannot run cannot be measured");
    assertTrue(seat.message().contains("the body threw"), seat.message());
  }

  @Test
  @DisplayName("a ceiling nobody stated is not checked")
  void unstatedCeilings() {
    Recorder seat = new Recorder();
    new Contract(seat, "get runs").loop(10, () -> Thread.sleep(1)).check();

    assertFalse(seat.failed(), "a contract with no ceiling has nothing to cross");
  }

  /** Whether the seat's first record names that assertion. */
  private static boolean named(Recorder seat, String assertion) {
    return !seat.failures().isEmpty()
        && seat.failures().get(0).assertion().equals(assertion);
  }

}
