package dev.dokimi.assertion;

import dev.dokimi.assertion.matcher.Behaviour;
import dev.dokimi.assertion.matcher.Containment;
import dev.dokimi.assertion.matcher.Errors;
import dev.dokimi.assertion.matcher.Mode;
import dev.dokimi.assertion.matcher.Numbers;
import dev.dokimi.assertion.matcher.Order;
import dev.dokimi.assertion.matcher.Raises;
import dev.dokimi.assertion.matcher.Sizes;
import dev.dokimi.assertion.matcher.Text;
import dev.dokimi.assertion.matcher.Values;
import dev.dokimi.assertion.matcher.Waiting;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Assertions that record a failure and let the test carry on.
///
/// ```java
/// Soft.hasPrefix(seat, id, "req_", "the id carries its prefix");
/// Soft.length(seat, reply.items(), 3, "every item comes back");
/// ```
///
/// Everything recorded is reported when the seat is flushed, so one run shows every
/// property that failed. The members, their signatures and their comparison rules are
/// those of [Check]; only what happens on a failure differs.
///
/// The members are grouped by family, in the order the other implementations of this
/// standard use: values, sizes, containment, text, numbers, errors, throwing, ordering,
/// behaviour and waiting.
@NullMarked
public final class Soft {

  /// Every failure on this surface is recorded, not thrown.
  private static final Mode MODE = Mode.SOFT;

  private Soft() {}

  /// Start a chain of assertions about one value.
  ///
  /// ```java
  /// Soft.that(seat, reply.status())
  ///     .notEqual(0, "the status was set")
  ///     .equal(200, "the request succeeds");
  /// ```
  ///
  /// Use it where several properties of one value are worth stating together. For a
  /// single property the static form reads better. The chain records every failure and runs them all.
  ///
  /// The chain holds the value's type, so `want` is held to it and a mismatch is a
  /// compile error. The static [#equal] takes two `Object` parameters and cannot be:
  /// Java infers a common supertype for two arguments of a generic method, so a
  /// mismatch there would still compile.
  ///
  /// @param <T> the type of the value under assertion
  /// @param seat where a failing method reports
  /// @param got the value every method in the chain compares against
  /// @return a chain over got
  public static <T extends @Nullable Object> Assertion<T> that(Seat seat, T got) {
    seat.helper();
    return new Assertion<>(seat, MODE, got);
  }

  /// Fail when got and want differ.
  ///
  /// Comparison is structural and reaches arrays, collections and maps. Values of
  /// different classes never compare, which is what makes an Integer unequal to a Long
  /// holding the same number. An absent collection does not equal an empty one, and NaN
  /// does not equal itself; pass an [Option] to relax either for this call alone.
  ///
  /// @param seat where the failure is reported
  /// @param got the value produced by the code under test
  /// @param want the value it is supposed to produce
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void equal(
      Seat seat, @Nullable Object got, @Nullable Object want, String msg, Option... options) {
    seat.helper();
    Values.equal(seat, MODE, got, want, msg, options);
  }

  /// Fail when got and want are equal.
  ///
  /// Comparison follows the rules of [#equal]. The failure shows the value the two
  /// shared, since printing one says everything.
  ///
  /// @param seat where the failure is reported
  /// @param got the value produced by the code under test
  /// @param want the value it must not equal
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void notEqual(
      Seat seat, @Nullable Object got, @Nullable Object want, String msg, Option... options) {
    seat.helper();
    Values.notEqual(seat, MODE, got, want, msg, options);
  }

  /// Fail when the condition does not hold.
  ///
  /// The failure carries the message alone: a bare false says nothing the message does
  /// not. Where a more specific assertion exists, it will say more on failure.
  ///
  /// @param seat where the failure is reported
  /// @param condition the condition that must hold
  /// @param msg the contract under test
  public static void isTrue(Seat seat, boolean condition, String msg) {
    seat.helper();
    Values.isTrue(seat, MODE, condition, msg);
  }

  /// Fail when the condition holds.
  ///
  /// @param seat where the failure is reported
  /// @param condition the condition that must not hold
  /// @param msg the contract under test
  public static void isFalse(Seat seat, boolean condition, String msg) {
    seat.helper();
    Values.isFalse(seat, MODE, condition, msg);
  }

  /// Fail when got is not null.
  ///
  /// @param seat where the failure is reported
  /// @param got the value that must be absent
  /// @param msg the contract under test
  public static void isNull(Seat seat, @Nullable Object got, String msg) {
    seat.helper();
    Values.isNull(seat, MODE, got, msg);
  }

  /// Fail when got is null.
  ///
  /// Use it before reading fields of a value that may be absent: the test stops here with
  /// your message rather than further down with a NullPointerException nobody wrote.
  ///
  /// @param seat where the failure is reported
  /// @param got the value that must be present
  /// @param msg the contract under test
  public static void isNotNull(Seat seat, @Nullable Object got, String msg) {
    seat.helper();
    Values.isNotNull(seat, MODE, got, msg);
  }

  /// Fail when got does not hold want entries.
  ///
  /// Answers for a CharSequence, a Collection, a Map and an array. A value with no length
  /// is itself the failure rather than a ClassCastException.
  ///
  /// @param seat where the failure is reported
  /// @param got the container to measure
  /// @param want how many entries it must hold
  /// @param msg the contract under test
  public static void length(Seat seat, @Nullable Object got, int want, String msg) {
    seat.helper();
    Sizes.length(seat, MODE, got, want, msg);
  }

  /// Fail when got holds anything.
  ///
  /// Empty is not absent. null has no length, so it fails here rather than passing.
  ///
  /// @param seat where the failure is reported
  /// @param got the container that must hold nothing
  /// @param msg the contract under test
  public static void isEmpty(Seat seat, @Nullable Object got, String msg) {
    seat.helper();
    Sizes.isEmpty(seat, MODE, got, msg);
  }

  /// Fail when got holds nothing.
  ///
  /// @param seat where the failure is reported
  /// @param got the container that must hold something
  /// @param msg the contract under test
  public static void isNotEmpty(Seat seat, @Nullable Object got, String msg) {
    seat.helper();
    Sizes.isNotEmpty(seat, MODE, got, msg);
  }

  /// Fail when haystack does not hold needle.
  ///
  /// What holding means follows the haystack. Text holds a substring, a Map holds a key,
  /// and a Collection or array holds an element compared by the rules of [#equal].
  ///
  /// @param seat where the failure is reported
  /// @param haystack the container or text to search
  /// @param needle the element, key or substring to find
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void contains(
      Seat seat,
      @Nullable Object haystack,
      @Nullable Object needle,
      String msg,
      Option... options) {
    seat.helper();
    Containment.contains(seat, MODE, haystack, needle, msg, options);
  }

  /// Fail when haystack holds needle.
  ///
  /// @param seat where the failure is reported
  /// @param haystack the container or text to search
  /// @param needle what must be absent
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void notContains(
      Seat seat,
      @Nullable Object haystack,
      @Nullable Object needle,
      String msg,
      Option... options) {
    seat.helper();
    Containment.notContains(seat, MODE, haystack, needle, msg, options);
  }

  /// Fail when got does not hold every needle, in order.
  ///
  /// Each needle is looked for after the previous one's match ends, so the same text
  /// cannot satisfy two needles. Anything may sit between them.
  ///
  /// @param seat where the failure is reported
  /// @param got the text to search
  /// @param needles the substrings, in the order they must appear
  /// @param msg the contract under test
  public static void containsInOrder(
      Seat seat, @Nullable Object got, String[] needles, String msg) {
    seat.helper();
    Containment.containsInOrder(seat, MODE, got, needles, msg);
  }

  /// Fail when got does not start with prefix.
  ///
  /// @param seat where the failure is reported
  /// @param got the text to inspect
  /// @param prefix what it must start with
  /// @param msg the contract under test
  public static void hasPrefix(Seat seat, @Nullable Object got, String prefix, String msg) {
    seat.helper();
    Text.hasPrefix(seat, MODE, got, prefix, msg);
  }

  /// Fail when got does not end with suffix.
  ///
  /// @param seat where the failure is reported
  /// @param got the text to inspect
  /// @param suffix what it must end with
  /// @param msg the contract under test
  public static void hasSuffix(Seat seat, @Nullable Object got, String suffix, String msg) {
    seat.helper();
    Text.hasSuffix(seat, MODE, got, suffix, msg);
  }

  /// Fail when got does not match the pattern.
  ///
  /// The pattern is searched rather than anchored: use `^` and `$` where you mean the
  /// whole value. A pattern that does not compile is reported as the failure.
  ///
  /// @param seat where the failure is reported
  /// @param got the text to match
  /// @param pattern a regular expression
  /// @param msg the contract under test
  public static void matches(Seat seat, @Nullable Object got, String pattern, String msg) {
    seat.helper();
    Text.matches(seat, MODE, got, pattern, msg);
  }

  /// Fail when got is further than tolerance from want.
  ///
  /// The tolerance is an absolute difference and the bound is inclusive. This is the
  /// assertion for a floating value, where exact equality is the wrong question. NaN is
  /// outside every tolerance.
  ///
  /// @param seat where the failure is reported
  /// @param got the number produced
  /// @param want the number it should be near
  /// @param tolerance the largest acceptable absolute difference
  /// @param msg the contract under test
  public static void closeTo(
      Seat seat, @Nullable Object got, double want, double tolerance, String msg) {
    seat.helper();
    Numbers.closeTo(seat, MODE, got, want, tolerance, msg);
  }

  /// Fail when got falls outside low to high.
  ///
  /// The interval is closed, so both bounds pass. A range with low above high can hold
  /// nothing, and says so rather than reporting the value. NaN is in no range.
  ///
  /// @param seat where the failure is reported
  /// @param got the number to place
  /// @param low the lowest acceptable value
  /// @param high the highest acceptable value
  /// @param msg the contract under test
  public static void inRange(
      Seat seat, @Nullable Object got, double low, double high, String msg) {
    seat.helper();
    Numbers.inRange(seat, MODE, got, low, high, msg);
  }

  /// Fail when an error is present.
  ///
  /// For code that hands an exception back rather than throwing it. Where the code
  /// throws, use [#throwsException] or [#doesNotThrow].
  ///
  /// @param seat where the failure is reported
  /// @param error the error value, or null when there was none
  /// @param msg the contract under test
  public static void noError(Seat seat, @Nullable Throwable error, String msg) {
    seat.helper();
    Errors.noError(seat, MODE, error, msg);
  }

  /// Fail when no error is present.
  ///
  /// @param seat where the failure is reported
  /// @param error the error value, or null when there was none
  /// @param msg the contract under test
  public static void hasError(Seat seat, @Nullable Throwable error, String msg) {
    seat.helper();
    Errors.hasError(seat, MODE, error, msg);
  }

  /// Fail when the error does not match target.
  ///
  /// Matching follows the chain of causes, so a wrapped exception still matches. target
  /// may be a throwable or a class.
  ///
  /// @param seat where the failure is reported
  /// @param error the error to inspect
  /// @param target the sentinel throwable or class
  /// @param msg the contract under test
  public static void errorIs(Seat seat, @Nullable Throwable error, Object target, String msg) {
    seat.helper();
    Errors.errorIs(seat, MODE, error, target, msg);
  }

  /// Fail when the error matches target.
  ///
  /// @param seat where the failure is reported
  /// @param error the error to inspect
  /// @param target the sentinel throwable or class
  /// @param msg the contract under test
  public static void errorIsNot(Seat seat, @Nullable Throwable error, Object target, String msg) {
    seat.helper();
    Errors.errorIsNot(seat, MODE, error, target, msg);
  }

  /// Fail when no error of the given class is in the chain.
  ///
  /// Use it to read fields off a specific type rather than parsing its message.
  ///
  /// @param <E> the class of error looked for
  /// @param seat where the failure is reported
  /// @param error the error to inspect
  /// @param want the class to look for
  /// @param msg the contract under test
  /// @return the matching error, so its fields can be read, or null when nothing matched
  public static <E extends Throwable> @Nullable E errorAs(
      Seat seat, @Nullable Throwable error, Class<E> want, String msg) {
    seat.helper();
    return Errors.errorAs(seat, MODE, error, want, msg);
  }

  /// Fail when the body does not throw.
  ///
  /// throws is a Java keyword, which is why this is not called that. Any Throwable
  /// counts, including an Error; where the type matters, assert on what is returned.
  ///
  /// @param seat where the failure is reported
  /// @param body the work under test
  /// @param msg the contract under test
  /// @return what the body threw, or null when it returned
  public static @Nullable Throwable throwsException(Seat seat, Raises.Body body, String msg) {
    seat.helper();
    return Raises.throwsException(seat, MODE, body, msg);
  }

  /// Fail when the body throws.
  ///
  /// @param seat where the failure is reported
  /// @param body the work under test
  /// @param msg the contract under test
  public static void doesNotThrow(Seat seat, Raises.Body body, String msg) {
    seat.helper();
    Raises.doesNotThrow(seat, MODE, body, msg);
  }

  /// Fail when an adjacent pair does not satisfy the predicate.
  ///
  /// Nought or one item passes, since neither has a pair. One assertion rather than
  /// sorted, unique and strictly increasing, because each of those is a relation between
  /// neighbours.
  ///
  /// @param <T> what the sequence holds
  /// @param seat where the failure is reported
  /// @param items the sequence to walk
  /// @param predicate called with the earlier item and the later one
  /// @param msg the contract under test
  public static <T> void pairwise(
      Seat seat, List<T> items, BiPredicate<T, T> predicate, String msg) {
    seat.helper();
    Order.pairwise(seat, MODE, items, predicate, msg);
  }

  /// Fail when a subject told to stop does not.
  ///
  /// The subject runs on its own thread and is interrupted at once, so this asks whether
  /// it checks at all rather than how quickly it notices. Interruption is Java's
  /// cancellation: it is what sleep, wait, take and every blocking call responds to.
  ///
  /// @param seat where the failure is reported
  /// @param body the work under test
  /// @param msg the contract under test
  public static void honoursCancellation(Seat seat, Behaviour.Cancellable body, String msg) {
    seat.helper();
    Behaviour.honoursCancellation(seat, MODE, body, msg);
  }

  /// Fail when a subject given no time does not stop.
  ///
  /// Java carries no deadline in its signatures the way Go carries one in a context, so
  /// the deadline is expressed the only way the platform expresses it.
  ///
  /// @param seat where the failure is reported
  /// @param body the work under test
  /// @param msg the contract under test
  public static void honoursDeadline(Seat seat, Behaviour.Cancellable body, String msg) {
    seat.helper();
    Behaviour.honoursDeadline(seat, MODE, body, msg);
  }

  /// Fail when the body takes longer than the given duration.
  ///
  /// The body is measured, not interrupted: one that runs long runs to completion and
  /// then fails. This spends real time.
  ///
  /// @param seat where the failure is reported
  /// @param within the ceiling
  /// @param body the work under test
  /// @param msg the contract under test
  public static void completesWithin(Seat seat, Duration within, Raises.Body body, String msg) {
    seat.helper();
    Behaviour.completesWithin(seat, MODE, within, body, msg);
  }

  /// Fail when the body changes what observe reads.
  ///
  /// What observe answers defines what nothing means: whatever it leaves out, the body is
  /// free to change. Answer a copy, because a projection sharing memory with the subject
  /// reads the same object twice and passes whatever the body did.
  ///
  /// @param seat where the failure is reported
  /// @param observe read before and after the body
  /// @param body the call that must change nothing observed
  /// @param msg the contract under test
  /// @param options relaxations for this call alone
  public static void isPure(
      Seat seat,
      Callable<@Nullable Object> observe,
      Raises.Body body,
      String msg,
      Option... options) {
    seat.helper();
    Behaviour.isPure(seat, MODE, observe, body, msg, options);
  }

  /// Fail when a subject given no cancellation handle crashes.
  ///
  /// Throwing an exception of its own is fine and is usually right. What fails here is
  /// dereferencing the missing handle.
  ///
  /// @param seat where the failure is reported
  /// @param body called with a null handle, which it must not dereference
  /// @param msg the contract under test
  public static void nullHandleSafe(Seat seat, Behaviour.Handled body, String msg) {
    seat.helper();
    Behaviour.nullHandleSafe(seat, MODE, body, msg);
  }

  /// Fail when a body of assertions never passes in time.
  ///
  /// The body is handed a seat of its own, so assertions inside it record an attempt
  /// rather than ending the test. It runs at least once however short the timeout, and
  /// the failure carries the last attempt's own reason. This spends real time.
  ///
  /// @param seat where the failure is reported
  /// @param timeout how long to keep retrying
  /// @param interval how long to wait between attempts
  /// @param body states the condition as assertions, against the seat it is handed
  /// @param msg the contract under test
  public static void eventually(
      Seat seat, Duration timeout, Duration interval, Consumer<Seat> body, String msg) {
    seat.helper();
    Waiting.eventually(seat, MODE, timeout, interval, body, msg);
  }

  /// Fail when a predicate never becomes true in time.
  ///
  /// Retried with a backoff that doubles. A predicate carries no reason, so the failure
  /// says only that the wait ran out; where the reason matters, use [#eventually].
  ///
  /// @param seat where the failure is reported
  /// @param timeout how long to keep retrying
  /// @param predicate must eventually answer true
  /// @param msg the contract under test
  public static void eventuallyTrue(
      Seat seat, Duration timeout, BooleanSupplier predicate, String msg) {
    seat.helper();
    Waiting.eventuallyTrue(seat, MODE, timeout, predicate, msg);
  }

  /// Answer a callable that fails when work started in the scope outlives it.
  ///
  /// Reads the live non-daemon threads either side of the scope. A leaked virtual thread
  /// is not reported: virtual threads appear in no standard enumeration on any JVM
  /// version, which the standard's overlay records as a limit.
  ///
  /// @param seat where the failure is reported
  /// @param msg the contract under test
  /// @return a callable to invoke where the scope ends
  public static Runnable noTaskLeaks(Seat seat, String msg) {
    seat.helper();
    return Waiting.noTaskLeaks(seat, MODE, msg);
  }
}
