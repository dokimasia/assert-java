package dev.dokimi.assertion;

import org.jspecify.annotations.NullMarked;

/// A relaxation a caller applies to one comparison.
///
/// An option applies to the call it is passed to and to nothing else. There is no
/// global setting, because a comparison rule changed in one place and read in another
/// is how two tests come to mean different things by the same assertion.
@NullMarked
public enum Option {

  /// Treat an absent collection as equal to an empty one.
  ///
  /// Off by default, because empty is not absent: a reply carrying no items and a reply
  /// that carried none are different answers.
  EQUATE_EMPTY,

  /// Treat NaN as equal to itself.
  ///
  /// Off by default, because IEEE 754 says NaN equals nothing, including NaN.
  EQUATE_NANS
}
