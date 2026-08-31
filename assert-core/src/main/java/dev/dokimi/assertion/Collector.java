package dev.dokimi.assertion;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// A seat that throws on a check and collects what the soft surface records.
///
/// This is what a real test wants. An aborting assertion throws where it stands; a
/// recording one is kept until [#flush], which the caller invokes once the test body
/// is done, so several failing properties of one value are all reported at once.
///
/// JUnit runs no fixture after a test body with access to the instance, so flushing
/// is the caller's job: call it from an `@AfterEach`, or from a `finally`.///
/// Every method is safe to call from more than one thread. A test holds one seat
/// and hands it to every assertion in the body, and several of those run the
/// subject somewhere else.
@NullMarked
public final class Collector implements Seat, Reporter, Clocked {

  /// Return a collector holding nothing.
  public Collector() {}

  private final List<String> collected = new ArrayList<>();

  private final List<Failure> records = new ArrayList<>();
  private @Nullable Clock supplied;

  /// Take one record: throw it when aborting, keep it when recording.
  ///
  /// The record is kept either way, so a test can read the assertion's own
  /// fields rather than search its sentence for words.
  ///
  /// @param failure the record the assertion reported
  /// @param aborting whether it came from the aborting surface
  @Override
  public synchronized void report(Failure failure, boolean aborting) {
    records.add(failure);
    if (aborting) {
      fail(failure.render());
      return;
    }
    record(failure.render());
  }

  /// Every record that arrived, in call order.
  ///
  /// @return every record that arrived, as a fresh copy
  public synchronized List<Failure> failures() {
    return List.copyOf(records);
  }

  /// The clock this seat hands assertions.
  ///
  /// @return what [#withClock(Clock)] set, or the platform clock
  @Override
  public synchronized Clock clock() {
    return supplied == null ? new SystemClock() : supplied;
  }

  /// Make assertions reported here read the given clock.
  ///
  /// @param clock where those assertions read time
  /// @return the receiver, so the call chains onto the constructor
  public synchronized Collector withClock(Clock clock) {
    this.supplied = clock;
    return this;
  }

  @Override
  public synchronized void fail(String message) {
    throw new AssertionFailed(message);
  }

  @Override
  public synchronized void record(String message) {
    collected.add(message);
  }

  /// Every failure kept so far, in call order.
  ///
  /// @return the messages, as a copy
  public synchronized List<String> collected() {
    return List.copyOf(collected);
  }

  /// Throw one failure carrying everything collected.
  ///
  /// Returns when nothing was collected. Clears what it threw, so a seat reused across
  /// phases does not report a failure twice.
  public synchronized void flush() {
    if (collected.isEmpty()) {
      return;
    }

    List<String> throwing = List.copyOf(collected);
    collected.clear();
    if (throwing.size() == 1) {
      throw new AssertionFailed(throwing.get(0));
    }

    String listed =
        IntStream.range(0, throwing.size())
            .mapToObj(i -> "  " + (i + 1) + ". " + throwing.get(i))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    throw new AssertionFailed(throwing.size() + " failures:\n" + listed);
  }
}
