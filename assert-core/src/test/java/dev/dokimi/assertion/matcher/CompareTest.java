package dev.dokimi.assertion.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Structural equality, and the three things Object.equals gets wrong.
///
/// Written with JUnit rather than with this library: this is the comparison every
/// assertion reports through, so testing it with itself would let one bug hide another.
class CompareTest {

  private static final Relaxations STRICT = Relaxations.STRICT;

  @Test
  @DisplayName("NaN does not equal itself, though Double.equals says it does")
  void nanIsNotItself() {
    assertFalse(Compare.equal(Double.NaN, Double.NaN, STRICT));
    assertTrue(Compare.equal(Double.NaN, Double.NaN, Relaxations.of(Option.EQUATE_NANS)));
  }

  @Test
  @DisplayName("the two zeroes are equal, though Double.equals says they differ")
  void zeroesAreEqual() {
    assertTrue(Compare.equal(0.0, -0.0, STRICT));
  }

  @Test
  @DisplayName("arrays compare by their elements, not by identity")
  void arraysCompareDeeply() {
    assertTrue(Compare.equal(new int[] {1, 2}, new int[] {1, 2}, STRICT));
    assertFalse(Compare.equal(new int[] {1, 2}, new int[] {1, 3}, STRICT));
    assertTrue(Compare.equal(new String[] {"a"}, new String[] {"a"}, STRICT));
  }

  @Test
  @DisplayName("an array inside a collection compares deeply too")
  void arraysInsideCollections() {
    assertTrue(Compare.equal(List.of(new int[] {1}), List.of(new int[] {1}), STRICT));
  }

  @Test
  @DisplayName("values of different classes never compare")
  void differentClassesNeverCompare() {
    assertFalse(Compare.equal(1, 1L, STRICT));
    assertFalse(Compare.equal(1, 1.0, STRICT));
    assertFalse(Compare.equal(List.of(1), Set.of(1), STRICT));
  }

  @Test
  @DisplayName("nested structures compare all the way down")
  void nestedStructures() {
    assertTrue(Compare.equal(Map.of("a", List.of(1)), Map.of("a", List.of(1)), STRICT));
    assertFalse(Compare.equal(Map.of("a", List.of(1)), Map.of("a", List.of(2)), STRICT));
  }

  @Test
  @DisplayName("a set matches without regard to order")
  void setsAreUnordered() {
    assertTrue(Compare.equal(Set.of(1, 2), Set.of(2, 1), STRICT));
    assertFalse(Compare.equal(Set.of(1), Set.of(2), STRICT));
  }

  @Test
  @DisplayName("an absent collection is not an empty one, unless relaxed")
  void absentIsNotEmpty() {
    assertFalse(Compare.equal(null, List.of(), STRICT));
    assertTrue(Compare.equal(null, List.of(), Relaxations.of(Option.EQUATE_EMPTY)));
    assertFalse(Compare.equal(null, List.of(1), Relaxations.of(Option.EQUATE_EMPTY)));
  }

  @Test
  @DisplayName("a cycle stops the walk rather than overflowing the stack")
  void cyclesTerminate() {
    List<Object> a = new ArrayList<>();
    a.add(a);
    List<Object> b = new ArrayList<>();
    b.add(b);

    assertTrue(Compare.equal(a, b, STRICT));
  }
}
