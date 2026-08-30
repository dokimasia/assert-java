package dev.dokimi.assertion.matcher;

import org.jspecify.annotations.NullMarked;

/// Whether a failure stops the test or is recorded.
@NullMarked
public enum Mode {
  /// Stop the test at this failure.
  FATAL,
  /// Record the failure and carry on.
  SOFT
}
