package dev.dokimi.assertion.junit;

import dev.dokimi.assertion.Collector;
import org.jspecify.annotations.NullMarked;

/// A seat that flushes itself when the test body ends.
///
/// The recording surface needs someone to report at the end of a test, because that is
/// what lets a failing assertion be seen without stopping the ones after it. A seat has
/// no end of test to report at, so something that runs after the body has to flush it.
///
/// ```java
/// class StoreTest {
///   @RegisterExtension final SeatExtension seat = new SeatExtension();
///
///   @Test
///   void get() {
///     Check.isNotNull(seat.get(), store.get(id), "get answers the stored item");
///     Soft.equal(seat.get(), store.size(), 1, "and nothing else was added");
///   }
/// }
/// ```
///
/// A field rather than a parameter, because a parameter resolver hands out a value JUnit
/// then forgets: nothing would be left holding the collector when the body ends.
///
/// This is the only class in the library that mentions JUnit, and it is optional. A
/// project on another framework constructs a [Collector] and calls `flush` where its own
/// framework ends a test.
@NullMarked
public final class SeatExtension
    implements org.junit.jupiter.api.extension.AfterTestExecutionCallback {

  private Collector collector = new Collector();

  /// Return an extension holding a fresh seat.
  public SeatExtension() {}

  /// The seat to pass to an assertion.
  ///
  /// @return the collector this extension flushes
  public Collector get() {
    return collector;
  }

  /// Flush what the test recorded, then start a fresh seat.
  ///
  /// Flushing here rather than in an `@AfterEach` puts the failure on the test rather
  /// than on a callback, which reads as a passing test with a problem after it.
  ///
  /// @param context the test that just ran, which this does not need
  @Override
  public void afterTestExecution(org.junit.jupiter.api.extension.ExtensionContext context) {
    Collector finished = collector;
    collector = new Collector();
    finished.flush();
  }
}
