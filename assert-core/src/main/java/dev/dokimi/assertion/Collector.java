package dev.dokimi.assertion;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

/// A seat that throws on a check and collects what the soft surface records.
///
/// This is what a real test wants. An aborting assertion throws where it stands; a
/// recording one is kept until [#flush], which the caller invokes once the test body
/// is done, so several failing properties of one value are all reported at once.
///
/// JUnit runs no fixture after a test body with access to the instance, so flushing
/// is the caller's job: call it from an `@AfterEach`, or from a `finally`.
@NullMarked
public final class Collector implements Seat {

  /// Return a collector holding nothing.
  public Collector() {}

  private final List<String> collected = new ArrayList<>();

  @Override
  public void fail(String message) {
    throw new AssertionFailed(message);
  }

  @Override
  public void record(String message) {
    collected.add(message);
  }

  /// Every failure kept so far, in call order.
  ///
  /// @return the messages, as a copy
  public List<String> collected() {
    return List.copyOf(collected);
  }

  /// Throw one failure carrying everything collected.
  ///
  /// Returns when nothing was collected. Clears what it threw, so a seat reused across
  /// phases does not report a failure twice.
  public void flush() {
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
