package dev.dokimi.assertion;

import org.jspecify.annotations.NullMarked;

/// A seat that carries a clock.
///
/// A seat implementing this supplies the time its assertions read. A seat that
/// does not gets [SystemClock], which is what every assertion read before a clock
/// existed.
@NullMarked
public interface Clocked {

  /// Answer the clock assertions reported here read.
  ///
  /// @return where those assertions read time
  Clock clock();
}
