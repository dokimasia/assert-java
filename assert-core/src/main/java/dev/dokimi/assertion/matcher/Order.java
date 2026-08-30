package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Seat;
import java.util.List;
import java.util.function.BiPredicate;
import org.jspecify.annotations.NullMarked;

/// How neighbouring items relate.
///
/// One assertion rather than sorted, unique and strictly increasing, because each of
/// those is a relation that has to hold between every adjacent pair and nothing more.
@NullMarked
public final class Order {

  private Order() {}

  /// Fail when an adjacent pair does not satisfy the predicate.
  ///
  /// Nought or one item passes, since neither has a pair. The failure names the index
  /// where it broke.
  ///
  /// @param <T> what the sequence holds
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param items the sequence to walk
  /// @param predicate called with the earlier item and the later one
  /// @param msg the contract under test
  public static <T> void pairwise(
      Seat seat, Mode mode, List<T> items, BiPredicate<T, T> predicate, String msg) {
    seat.helper();
    for (int i = 1; i < items.size(); i++) {
      T earlier = items.get(i - 1);
      T later = items.get(i);
      if (!predicate.test(earlier, later)) {
        Report.to(
            seat,
            mode,
            msg + ": the pair at index " + (i - 1) + " fails: "
                + Show.value(earlier) + " then " + Show.value(later));
        return;
      }
    }
  }
}
