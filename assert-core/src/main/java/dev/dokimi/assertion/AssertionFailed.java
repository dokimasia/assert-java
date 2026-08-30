package dev.dokimi.assertion;

import org.jspecify.annotations.NullMarked;

/// Thrown by every seat that stops a test.
///
/// An unchecked exception, because an assertion failing is not a condition a caller
/// recovers from: it is the test reporting its result.
@NullMarked
public final class AssertionFailed extends AssertionError {

  private static final long serialVersionUID = 1L;

  /// Return a failure carrying message.
  ///
  /// @param message what was supposed to be true, and what was not
  public AssertionFailed(String message) {
    super(message);
  }
}
