package dev.dokimi.assertion;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// A seat that collects every failure and throws none.
///
/// This is what lets an assertion be tested by reading what it reported rather than
/// suffering it. Nothing driven with a recorder can fail a test.///
/// Every method is safe to call from more than one thread. A test holds one seat
/// and hands it to every assertion in the body, and several of those run the
/// subject somewhere else.
@NullMarked
public final class Recorder implements Seat, Reporter, Clocked {

  /// Return a recorder that has collected nothing.
  public Recorder() {}

  private @Nullable String fatal;
  private final List<String> recorded = new ArrayList<>();
  private final List<Failure> records = new ArrayList<>();
  private @Nullable Clock supplied;

  /// Record one failure as the record it is.
  ///
  /// This is what lets a test read the assertion's own fields rather than
  /// search its sentence for words. The rendered sentence is kept too, so
  /// [#message()] answers what it always did.
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
  /// A message passed straight to fail or record leaves none, so an assertion
  /// that did not report a record is visible here.
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
  public synchronized Recorder withClock(Clock clock) {
    this.supplied = clock;
    return this;
  }
  private int helpers;

  @Override
  public synchronized void helper() {
    helpers++;
  }

  @Override
  public synchronized void fail(String message) {
    if (fatal == null) {
      fatal = message;
    }
  }

  @Override
  public synchronized void record(String message) {
    recorded.add(message);
  }

  /// Whether any failure was recorded, through either path.
  ///
  /// @return true when something was reported
  public synchronized boolean failed() {
    return fatal != null || !recorded.isEmpty();
  }

  /// The first failure recorded, preferring the aborting path.
  ///
  /// Empty when nothing failed. Reading this rather than indexing a list keeps a test
  /// from throwing when the assertion under test wrongly reported nothing.
  ///
  /// @return the failure text, or an empty string
  public synchronized String message() {
    if (fatal != null) {
      return fatal;
    }
    return recorded.isEmpty() ? "" : recorded.get(0);
  }

  /// Every failure recorded through [#record], in call order.
  ///
  /// @return the messages, as a copy
  public synchronized List<String> messages() {
    return List.copyOf(recorded);
  }

  /// How many times [#helper] was called.
  ///
  /// @return the count
  public synchronized int helperCalls() {
    return helpers;
  }
}
