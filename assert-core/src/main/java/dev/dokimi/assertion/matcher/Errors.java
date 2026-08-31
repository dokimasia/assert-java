package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Assertions about an exception handed back rather than thrown.
///
/// Matching follows the chain of causes, which is what `Throwable.getCause` is for and
/// what lets a wrapped failure still be recognised.
@NullMarked
public final class Errors {

  /// How far down a cause chain to look before calling it cyclic.
  private static final int MAX_CAUSES = 100;

  private Errors() {}

  /// Answer every throwable in the chain, starting with the throwable itself.
  private static List<Throwable> chain(@Nullable Throwable error) {
    List<Throwable> found = new ArrayList<>();
    Throwable current = error;
    for (int depth = 0; depth < MAX_CAUSES && current != null; depth++) {
      // Identity, not equals: a chain that loops back on itself has to
      // stop, and two distinct throwables can compare equal.
      boolean seenBefore = false;
      for (Throwable seen : found) {
        if (seen == current) {
          seenBefore = true;
          break;
        }
      }
      if (seenBefore) {
        break;
      }
      found.add(current);
      current = current.getCause();
    }
    return found;
  }

  /// Whether target matches the error, or anything it wraps.
  private static boolean matches(@Nullable Throwable error, Object target) {
    return chain(error).stream()
        .anyMatch(
            link -> {
              if (link == target) {
                return true;
              }
              if (target instanceof Class<?> type) {
                return type.isInstance(link);
              }
              if (target instanceof Throwable other) {
                return link.getClass().equals(other.getClass())
                    && java.util.Objects.equals(link.getMessage(), other.getMessage());
              }
              return false;
            });
  }

  /// Fail when an error is present.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param error the error value, or null when there was none
  /// @param msg the contract under test
  public static void noError(Seat seat, Mode mode, @Nullable Throwable error, String msg) {
    seat.helper();
    if (error != null) {
      Report.failure(seat, mode, "err-absent", msg, Report.detail("got", error));
    }
  }

  /// Fail when no error is present.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param error the error value, or null when there was none
  /// @param msg the contract under test
  public static void hasError(Seat seat, Mode mode, @Nullable Throwable error, String msg) {
    seat.helper();
    if (error == null) {
      Report.failure(seat, mode, "err-present", msg);
    }
  }

  /// Fail when the error does not match target, through the chain of causes.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param error the error to inspect
  /// @param target the sentinel throwable or class it must match
  /// @param msg the contract under test
  public static void errorIs(
      Seat seat, Mode mode, @Nullable Throwable error, Object target, String msg) {
    seat.helper();
    if (!matches(error, target)) {
      Report.failure(seat, mode, "err-is", msg,
          Report.detail("want", target, "got", error));
    }
  }

  /// Fail when the error matches target.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param error the error to inspect
  /// @param target the sentinel throwable or class it must not match
  /// @param msg the contract under test
  public static void errorIsNot(
      Seat seat, Mode mode, @Nullable Throwable error, Object target, String msg) {
    seat.helper();
    if (matches(error, target)) {
      Report.failure(seat, mode, "err-is-not", msg, Report.detail("got", error));
    }
  }

  /// Fail when no error of the given class is in the chain.
  ///
  /// @param <E> the class of error looked for
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param error the error to inspect
  /// @param want the class to look for
  /// @param msg the contract under test
  /// @return the matching error, so its fields can be read, or null when nothing matched
  public static <E extends Throwable> @Nullable E errorAs(
      Seat seat, Mode mode, @Nullable Throwable error, Class<E> want, String msg) {
    seat.helper();
    for (Throwable link : chain(error)) {
      if (want.isInstance(link)) {
        return want.cast(link);
      }
    }
    Report.failure(seat, mode, "err-as", msg,
        Report.detail("want", want, "got", error));
    return null;
  }
}
