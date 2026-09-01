package dev.dokimi.assertion;

import dev.dokimi.assertion.matcher.Containment;
import dev.dokimi.assertion.matcher.Mode;
import dev.dokimi.assertion.matcher.Numbers;
import dev.dokimi.assertion.matcher.Sizes;
import dev.dokimi.assertion.matcher.Text;
import dev.dokimi.assertion.matcher.Values;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Several assertions about one value, without naming it each time.
///
/// Every method answers the receiver, so calls join with a dot:
///
/// ```java
/// Check.that(seat, reply.status())
///     .notEqual(0, "the status was set")
///     .equal(200, "the request succeeds");
/// ```
///
/// Two things follow from holding the value rather than passing it each time.
///
/// The chain fixes the value's type once, so `want` is held to it. A mismatch is a
/// compile error here, where the static form takes two `Object` parameters and can only
/// report one at run time. Java infers a common supertype for two arguments of a generic
/// method, so the static form cannot be tightened the same way.
///
/// An editor can list what applies. Typing a dot after `that(...)` offers this class's
/// members, which is what the static form asks a reader to already know.
///
/// The mode is fixed by whoever built the chain: one from [Check#that] stops the test at
/// the first failing method, so later methods in the chain do not run, and one from
/// [Soft#that] records every failure and runs them all. Where every property is worth
/// reporting at once, start from [Soft].
///
/// Not safe for concurrent use, which costs nothing: a chain is one expression.
///
/// @param <T> the type of the value under assertion
@NullMarked
public final class Assertion<T extends @Nullable Object> {

  /// Where a failing method reports.
  private final Seat seat;

  /// Whether a failure stops the test or is recorded.
  private final Mode mode;

  /// The value every method compares against.
  ///
  /// Held rather than copied, so a method sees any later mutation of a reference type.
  private final T got;

  /// Build a chain. Reached through [Check#that] or [Soft#that].
  ///
  /// @param seat where a failing method reports
  /// @param mode whether a failure stops the test or is recorded
  /// @param got the value every method compares against
  Assertion(Seat seat, Mode mode, T got) {
    this.seat = seat;
    this.mode = mode;
    this.got = got;
  }

  /// Fail when the value does not equal want.
  ///
  /// @param want the value it is supposed to equal, held to the chained value's type
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  /// @return this chain
  public Assertion<T> equal(T want, String msg, Option... options) {
    seat.helper();
    Values.equal(seat, mode, got, want, msg, options);
    return this;
  }

  /// Fail when the value equals want.
  ///
  /// @param want the value it must not equal, held to the chained value's type
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  /// @return this chain
  public Assertion<T> notEqual(T want, String msg, Option... options) {
    seat.helper();
    Values.notEqual(seat, mode, got, want, msg, options);
    return this;
  }

  /// Fail when the value is not null.
  ///
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> isNull(String msg) {
    seat.helper();
    Values.isNull(seat, mode, got, msg);
    return this;
  }

  /// Fail when the value is null.
  ///
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> isNotNull(String msg) {
    seat.helper();
    Values.isNotNull(seat, mode, got, msg);
    return this;
  }

  /// Fail when the value does not hold want many elements.
  ///
  /// @param want the length it is supposed to have
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> length(int want, String msg) {
    seat.helper();
    Sizes.length(seat, mode, got, want, msg);
    return this;
  }

  /// Fail when the value holds anything.
  ///
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> isEmpty(String msg) {
    seat.helper();
    Sizes.isEmpty(seat, mode, got, msg);
    return this;
  }

  /// Fail when the value holds nothing.
  ///
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> isNotEmpty(String msg) {
    seat.helper();
    Sizes.isNotEmpty(seat, mode, got, msg);
    return this;
  }

  /// Fail when the value does not hold needle.
  ///
  /// @param needle what it is supposed to hold
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  /// @return this chain
  public Assertion<T> contains(@Nullable Object needle, String msg, Option... options) {
    seat.helper();
    Containment.contains(seat, mode, got, needle, msg, options);
    return this;
  }

  /// Fail when the value holds needle.
  ///
  /// @param needle what it must not hold
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  /// @return this chain
  public Assertion<T> notContains(@Nullable Object needle, String msg, Option... options) {
    seat.helper();
    Containment.notContains(seat, mode, got, needle, msg, options);
    return this;
  }

  /// Fail when the value does not hold every needle, in the order given.
  ///
  /// @param needles the substrings it is supposed to hold, in order
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> containsInOrder(String[] needles, String msg) {
    seat.helper();
    Containment.containsInOrder(seat, mode, got, needles, msg);
    return this;
  }

  /// Fail when the value does not start with prefix.
  ///
  /// @param prefix what it is supposed to start with
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> hasPrefix(String prefix, String msg) {
    seat.helper();
    Text.hasPrefix(seat, mode, got, prefix, msg);
    return this;
  }

  /// Fail when the value does not end with suffix.
  ///
  /// @param suffix what it is supposed to end with
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> hasSuffix(String suffix, String msg) {
    seat.helper();
    Text.hasSuffix(seat, mode, got, suffix, msg);
    return this;
  }

  /// Fail when the value does not match pattern.
  ///
  /// @param pattern the regular expression it is supposed to match
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> matches(String pattern, String msg) {
    seat.helper();
    Text.matches(seat, mode, got, pattern, msg);
    return this;
  }

  /// Fail when the value is further from want than tolerance allows.
  ///
  /// @param want the number it is supposed to be near
  /// @param tolerance the largest acceptable difference
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> closeTo(double want, double tolerance, String msg) {
    seat.helper();
    Numbers.closeTo(seat, mode, got, want, tolerance, msg);
    return this;
  }

  /// Fail when the value falls outside low to high, both included.
  ///
  /// @param low the lowest acceptable value
  /// @param high the highest acceptable value
  /// @param msg the contract under test
  /// @return this chain
  public Assertion<T> inRange(double low, double high, String msg) {
    seat.helper();
    Numbers.inRange(seat, mode, got, low, high, msg);
    return this;
  }
}
