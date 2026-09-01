package dev.dokimi.assertion.bench;

import dev.dokimi.assertion.Seat;
import dev.dokimi.assertion.matcher.Mode;
import dev.dokimi.assertion.matcher.Raises;
import dev.dokimi.assertion.matcher.Report;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Performance ceilings, stated as a contract a benchmark must meet.
///
/// A benchmark that only prints numbers tells you what happened; a ceiling tells you
/// whether it was acceptable. State the ceiling once and the run fails when it is
/// crossed, the same way any other assertion does.
///
/// ```java
/// new Contract(seat, "get stays quick")
///     .maxLatency(Duration.ofMillis(2))
///     .maxBytes(512)
///     .loop(10_000, () -> store.get(id))
///     .check();
/// ```
///
/// The standard also states a ceiling on the number of allocations per iteration. The
/// JVM reports bytes allocated per thread and no count of allocations, so that one is
/// declared in the standard's overlay rather than implemented here.
@NullMarked
public final class Contract {

  /// How many samples a p99 needs before it means anything.
  private static final int P99_MINIMUM = 100;

  private final Seat seat;
  private final String msg;
  private @Nullable Duration maxLatency;
  private @Nullable Duration maxMean;
  private @Nullable Long maxBytes;
  private @Nullable Measurement measurement;

  /// What one run of the body cost.
  ///
  /// @param iterations how many times the body ran
  /// @param latencies every sample, sorted
  /// @param bytesPerIteration bytes allocated per iteration, or null when not measured
  private record Measurement(
      int iterations, List<Duration> latencies, @Nullable Long bytesPerIteration) {}

  /// Return a contract that states no ceilings yet.
  ///
  /// @param seat where a crossed ceiling is reported
  /// @param msg the contract under test
  public Contract(Seat seat, String msg) {
    this.seat = seat;
    this.msg = msg;
  }

  /// State the highest acceptable p99 latency per iteration.
  ///
  /// The p99 rather than the mean, because the tail is what a caller waits for. With
  /// fewer than a hundred iterations it is the slowest one.
  ///
  /// @param ceiling the highest acceptable p99 latency
  /// @return the contract, so ceilings can be chained
  public Contract maxLatency(Duration ceiling) {
    this.maxLatency = ceiling;
    return this;
  }

  /// State the highest acceptable mean latency per iteration.
  ///
  /// Use it beside [#maxLatency] rather than instead of it: a mean that holds while the
  /// tail grows is the regression a mean alone misses.
  ///
  /// @param ceiling the highest acceptable mean latency
  /// @return the contract, so ceilings can be chained
  public Contract maxMean(Duration ceiling) {
    this.maxMean = ceiling;
    return this;
  }

  /// State the most bytes the body may allocate per iteration.
  ///
  /// Read from `ThreadMXBean.getThreadAllocatedBytes`, which counts what this thread
  /// allocated rather than what survived a collection, so the reading does not move with
  /// whether the collector happened to run.
  ///
  /// @param ceiling the highest acceptable bytes per iteration
  /// @return the contract, so ceilings can be chained
  public Contract maxBytes(long ceiling) {
    this.maxBytes = ceiling;
    return this;
  }

  /// Measure a body whose input is built fresh each iteration.
  ///
  /// A benchmark whose operation consumes its input builds a new one every time,
  /// and [#loop(int, Raises.Body)] would state what the build and the operation
  /// cost together. This builds every input first, outside the measurement, and
  /// measures only the bodies.
  ///
  /// Every input exists at once, so a large fixture and a long run cost that much
  /// memory. A body needing no input wants [#loop(int, Raises.Body)], which holds
  /// nothing.
  ///
  /// @param <T> what the setup builds and the body consumes
  /// @param iterations how many times to run the body
  /// @param setup builds one input, outside the measurement
  /// @param body the measured work, given what setup built
  /// @return the contract, so the call chains into check
  public <T extends @Nullable Object> Contract measuring(
      int iterations, Supplier<T> setup, Consumer<T> body) {
    // Built first and all at once, so no counter is read between one
    // iteration's setup and the next iteration's work.
    List<T> inputs = new ArrayList<>(iterations);
    for (int i = 0; i < iterations; i++) {
      inputs.add(setup.get());
    }

    List<Duration> latencies = new ArrayList<>(iterations);
    ThreadMXBean threads = allocationCounter();
    long id = Thread.currentThread().getId();
    long allocatedBefore = threads == null ? 0 : threads.getThreadAllocatedBytes(id);

    for (T input : inputs) {
      long started = System.nanoTime();
      body.accept(input);
      latencies.add(Duration.ofNanos(System.nanoTime() - started));
    }

    Long bytes = null;
    if (threads != null && iterations > 0) {
      bytes = (threads.getThreadAllocatedBytes(id) - allocatedBefore) / iterations;
    }
    Collections.sort(latencies);
    this.measurement = new Measurement(iterations, latencies, bytes);
    return this;
  }

  /// Run the body the given number of times, timing each and weighing the whole.
  ///
  /// @param iterations how many times to run the body
  /// @param body the work to measure
  /// @return the contract, so the call chains into check
  public Contract loop(int iterations, Raises.Body body) {
    List<Duration> latencies = new ArrayList<>(iterations);
    ThreadMXBean threads = allocationCounter();
    long id = Thread.currentThread().getId();
    long allocatedBefore = threads == null ? 0 : threads.getThreadAllocatedBytes(id);

    for (int i = 0; i < iterations; i++) {
      long started = System.nanoTime();
      try {
        body.run();
      } catch (Throwable thrown) {
        Report.to(seat, Mode.FATAL, msg + ": the body threw " + thrown);
        return this;
      }
      latencies.add(Duration.ofNanos(System.nanoTime() - started));
    }

    Long bytes = null;
    if (threads != null && iterations > 0) {
      bytes = (threads.getThreadAllocatedBytes(id) - allocatedBefore) / iterations;
    }
    Collections.sort(latencies);
    this.measurement = new Measurement(iterations, latencies, bytes);
    return this;
  }

  /// Answer the allocation counter, or null where the JVM does not supply one.
  private static @Nullable ThreadMXBean allocationCounter() {
    if (ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
        && bean.isThreadAllocatedMemorySupported()
        && bean.isThreadAllocatedMemoryEnabled()) {
      return bean;
    }
    return null;
  }

  /// Report every ceiling the run crossed.
  public void check() {
    seat.helper();
    Measurement run = measurement;
    if (run == null) {
      Report.to(seat, Mode.FATAL, msg + ": nothing was measured");
      return;
    }

    Duration mean = mean(run.latencies());
    Duration p99 = percentile(run.latencies());

    if (maxLatency != null && p99.compareTo(maxLatency) > 0) {
      crossed("bench-max-latency", p99.toNanos() / 1_000_000.0, maxLatency.toMillis());
    }
    if (maxMean != null && mean.compareTo(maxMean) > 0) {
      crossed("bench-max-mean", mean.toNanos() / 1_000_000.0, maxMean.toMillis());
    }
    Long bytes = run.bytesPerIteration();
    if (maxBytes != null && bytes != null && bytes > maxBytes) {
      Report.failure(seat, Mode.FATAL, "bench-max-bytes", msg,
          Report.detail("want", maxBytes, "got", bytes));
    }
  }

  /// Report one crossed latency ceiling.
  ///
  /// Milliseconds to three places is the granularity these are about, and a
  /// nanosecond reading carries far more digits than that.
  ///
  /// @param assertion the canonical id of the ceiling that was crossed
  /// @param measured what the run cost, in milliseconds
  /// @param ceiling what it was allowed, in milliseconds
  private void crossed(String assertion, double measured, long ceiling) {
    Report.failure(seat, Mode.FATAL, assertion, msg,
        Report.detail("want", ceiling, "got", Double.parseDouble(String.format("%.3f", measured))));
  }

  private static Duration mean(List<Duration> sorted) {
    if (sorted.isEmpty()) {
      return Duration.ZERO;
    }
    long total = sorted.stream().mapToLong(Duration::toNanos).sum();
    return Duration.ofNanos(total / sorted.size());
  }

  /// Answer the p99, or the slowest sample when there are too few to mean anything.
  ///
  /// With ten samples the 99th percentile is the tenth, and calling that a p99 would
  /// dress one reading up as a distribution.
  private static Duration percentile(List<Duration> sorted) {
    if (sorted.isEmpty()) {
      return Duration.ZERO;
    }
    if (sorted.size() < P99_MINIMUM) {
      return sorted.get(sorted.size() - 1);
    }
    int at = (int) Math.ceil(0.99 * sorted.size()) - 1;
    return sorted.get(Math.min(at, sorted.size() - 1));
  }
}
