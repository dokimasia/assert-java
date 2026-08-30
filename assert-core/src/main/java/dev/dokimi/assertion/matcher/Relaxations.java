package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Option;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/// What the options in force allow.
///
/// @param equateEmpty whether an absent collection equals an empty one
/// @param equateNans whether NaN equals itself
@NullMarked
public record Relaxations(boolean equateEmpty, boolean equateNans) {

  /// Nothing relaxed, which is what the standard states.
  public static final Relaxations STRICT = new Relaxations(false, false);

  /// Answer what the given options turn on.
  ///
  /// @param options the options passed to one call
  /// @return the relaxations in force for that comparison
  public static Relaxations of(Option... options) {
    Set<Option> set = Set.of(options);
    return new Relaxations(set.contains(Option.EQUATE_EMPTY), set.contains(Option.EQUATE_NANS));
  }
}
