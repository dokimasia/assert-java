package dev.dokimi.assertion;

import org.jspecify.annotations.NullMarked;

/// A seat that throws on any failure.
///
/// The seat to construct outside a test framework. Its [#record] throws too: a
/// recorded failure needs somewhere to report at the end, and a bare seat has no end to
/// report at. Throwing early beats dropping it.
@NullMarked
public final class Standard implements Seat {

  /// Return a seat that throws on every failure.
  public Standard() {}

  @Override
  public void fail(String message) {
    throw new AssertionFailed(message);
  }

  @Override
  public void record(String message) {
    throw new AssertionFailed(message);
  }
}
