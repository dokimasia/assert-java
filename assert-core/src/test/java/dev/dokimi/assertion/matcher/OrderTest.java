package dev.dokimi.assertion.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Recorder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// How neighbouring items relate.
class OrderTest {

  private static final Mode ABORTS = Mode.FATAL;
  private static final Mode RECORDS = Mode.SOFT;

  private static final BiPredicate<Integer, Integer> ASCENDING = (a, b) -> a <= b;

  @Test
  @DisplayName("a sorted sequence passes and an unsorted one is reported")
  void sortedAndUnsorted() {
    Recorder passing = new Recorder();
    Order.pairwise(passing, ABORTS, List.of(1, 2, 2, 5), ASCENDING, "the page is sorted");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Order.pairwise(failing, ABORTS, List.of(1, 5, 2), ASCENDING, "the page is sorted");
    assertTrue(failing.failed(), "an out-of-order pair must be reported");
  }

  @Test
  @DisplayName("the failure names the index where the order broke")
  void theIndexIsNamed() {
    Recorder seat = new Recorder();
    Order.pairwise(seat, ABORTS, List.of(1, 2, 9, 3), ASCENDING, "the page is sorted");

    assertTrue(seat.message().contains("index 2"), seat.message());
    assertTrue(seat.message().contains("9"), seat.message());
  }

  @Test
  @DisplayName("nought and one item pass, having no pair")
  void tooShortToHaveAPair() {
    Recorder empty = new Recorder();
    Order.pairwise(empty, ABORTS, List.<Integer>of(), ASCENDING, "the page is sorted");
    assertFalse(empty.failed(), empty.message());

    Recorder single = new Recorder();
    Order.pairwise(single, ABORTS, List.of(7), ASCENDING, "the page is sorted");
    assertFalse(single.failed(), single.message());
  }

  @Test
  @DisplayName("only the first failing pair is reported")
  void onlyTheFirstFailure() {
    Recorder seat = new Recorder();
    Order.pairwise(seat, RECORDS, List.of(9, 1, 8, 2), ASCENDING, "the page is sorted");

    assertEquals(1, seat.messages().size(), "one failure, not one per broken pair");
    assertTrue(seat.message().contains("index 0"), seat.message());
  }

  @Test
  @DisplayName("the predicate stops at the first failing pair")
  void thePredicateStops() {
    List<Integer> seen = new ArrayList<>();
    Recorder seat = new Recorder();

    Order.pairwise(
        seat,
        ABORTS,
        List.of(1, 9, 2, 3),
        (a, b) -> {
          seen.add(a);
          return a <= b;
        },
        "the page is sorted");

    assertEquals(List.of(1, 9), seen, "walking past a known failure only wastes time");
  }

  @Test
  @DisplayName("uniqueness and strict increase are the same assertion with another predicate")
  void otherRelations() {
    Recorder unique = new Recorder();
    Order.pairwise(
        unique, ABORTS, List.of("a", "b", "c"), (a, b) -> !a.equals(b), "no run of duplicates");
    assertFalse(unique.failed(), unique.message());

    Recorder strict = new Recorder();
    Order.pairwise(strict, ABORTS, List.of(1, 2, 2), (a, b) -> a < b, "strictly increasing");
    assertTrue(strict.failed(), "a repeat is not a strict increase");
  }
}
