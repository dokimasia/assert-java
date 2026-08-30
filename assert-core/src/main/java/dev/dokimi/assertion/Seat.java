package dev.dokimi.assertion;

import org.jspecify.annotations.NullMarked;

/// Where an assertion reports, and what it may do about it.
///
/// An assertion never calls a test framework and never throws on its own. It reports
/// through a seat, which is what lets one assertion serve a real test, a benchmark and a
/// test that checks the assertion itself.
///
/// An interface rather than a base class, so anything with the three methods is a seat.
@NullMarked
public interface Seat {

  /// Mark this frame as the library's rather than the caller's.
  ///
  /// Named for the same reason Go's `testing.TB.Helper` is. A framework that can
  /// hide library frames from a stack trace does it here; one that cannot does nothing.
  default void helper() {}

  /// Report a failure that stops the test.
  ///
  /// @param message what was supposed to be true, and what was not
  void fail(String message);

  /// Report a failure the test may carry on past.
  ///
  /// @param message what was supposed to be true, and what was not
  void record(String message);
}
