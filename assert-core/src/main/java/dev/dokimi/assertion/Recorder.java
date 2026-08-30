package dev.dokimi.assertion;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// A seat that collects every failure and throws none.
///
/// This is what lets an assertion be tested by reading what it reported rather than
/// suffering it. Nothing driven with a recorder can fail a test.
@NullMarked
public final class Recorder implements Seat {

  /// Return a recorder that has collected nothing.
  public Recorder() {}

  private @Nullable String fatal;
  private final List<String> recorded = new ArrayList<>();
  private int helpers;

  @Override
  public void helper() {
    helpers++;
  }

  @Override
  public void fail(String message) {
    if (fatal == null) {
      fatal = message;
    }
  }

  @Override
  public void record(String message) {
    recorded.add(message);
  }

  /// Whether any failure was recorded, through either path.
  ///
  /// @return true when something was reported
  public boolean failed() {
    return fatal != null || !recorded.isEmpty();
  }

  /// The first failure recorded, preferring the aborting path.
  ///
  /// Empty when nothing failed. Reading this rather than indexing a list keeps a test
  /// from throwing when the assertion under test wrongly reported nothing.
  ///
  /// @return the failure text, or an empty string
  public String message() {
    if (fatal != null) {
      return fatal;
    }
    return recorded.isEmpty() ? "" : recorded.get(0);
  }

  /// Every failure recorded through [#record], in call order.
  ///
  /// @return the messages, as a copy
  public List<String> messages() {
    return List.copyOf(recorded);
  }

  /// How many times [#helper] was called.
  ///
  /// @return the count
  public int helperCalls() {
    return helpers;
  }
}
