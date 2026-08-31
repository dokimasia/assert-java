package dev.dokimi.assertion;

import org.jspecify.annotations.NullMarked;

/// A seat that takes the record rather than the sentence.
///
/// A seat implementing this receives the record; any other receives the
/// sentence rendered from it. [Recorder] and [Collector] implement it, so a
/// test can read an assertion's own fields rather than search its sentence for
/// words.
@NullMarked
public interface Reporter {

  /// Take one record.
  ///
  /// @param failure the record the assertion reported
  /// @param aborting whether it came from the aborting surface
  void report(Failure failure, boolean aborting);
}
