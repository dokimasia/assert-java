package dev.dokimi.assertion.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Recorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Numbers, and the boundaries a corpus file cannot state.
///
/// NaN is not a JSON value, so every case involving it lives here rather than in the
/// corpus, and NaN is where both of these assertions would otherwise pass anything: a
/// comparison against NaN is false, so a bare `diff > tolerance` lets it through.
class NumbersTest {

  private static final Mode ABORTS = Mode.FATAL;

  @Test
  @DisplayName("closeTo holds inside the tolerance and fails outside it")
  void closeTo() {
    Recorder passing = new Recorder();
    Numbers.closeTo(passing, ABORTS, 1.05, 1.0, 0.1, "the rate is about one");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Numbers.closeTo(failing, ABORTS, 1.5, 1.0, 0.1, "the rate is about one");
    assertTrue(failing.failed(), "a value outside the tolerance must be reported");
    assertTrue(named(failing, "close-to"), failing.message());
  }

  @Test
  @DisplayName("the tolerance is inclusive, so a difference exactly equal to it passes")
  void toleranceIsInclusive() {
    Recorder seat = new Recorder();
    Numbers.closeTo(seat, ABORTS, 1.5, 1.0, 0.5, "the rate is about one");

    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("NaN is outside every tolerance, whichever side it is on")
  void nanIsOutsideEveryTolerance() {
    Recorder value = new Recorder();
    Numbers.closeTo(value, ABORTS, Double.NaN, 1.0, 0.1, "the rate is about one");
    assertTrue(value.failed(), "a NaN reading is not close to anything");
    assertTrue(named(value, "close-to"), value.message());

    Recorder want = new Recorder();
    Numbers.closeTo(want, ABORTS, 1.0, Double.NaN, 0.1, "the rate is about one");
    assertTrue(want.failed(), "nothing is close to NaN");

    Recorder tolerance = new Recorder();
    Numbers.closeTo(tolerance, ABORTS, 1.0, 1.0, Double.NaN, "the rate is about one");
    assertTrue(tolerance.failed(), "a NaN tolerance admits nothing, not everything");
  }

  @Test
  @DisplayName("an infinite tolerance still rejects a NaN reading")
  void infiniteToleranceStillRejectsNan() {
    Recorder seat = new Recorder();
    Numbers.closeTo(seat, ABORTS, Double.NaN, 1.0, Double.POSITIVE_INFINITY, "the rate is finite");

    assertTrue(seat.failed(), "widening the tolerance does not make NaN a number");
  }

  @Test
  @DisplayName("inRange holds inside the interval and fails outside it")
  void inRange() {
    Recorder passing = new Recorder();
    Numbers.inRange(passing, ABORTS, 5, 1, 10, "the page size is sane");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Numbers.inRange(failing, ABORTS, 50, 1, 10, "the page size is sane");
    assertTrue(failing.failed(), "a value outside the range must be reported");
    assertEquals(1.0, failing.failures().get(0).detail().get("low"), failing.message());
    assertEquals(10.0, failing.failures().get(0).detail().get("high"), failing.message());
  }

  @Test
  @DisplayName("both bounds are inside the interval")
  void boundsAreInclusive() {
    Recorder low = new Recorder();
    Numbers.inRange(low, ABORTS, 1, 1, 10, "the page size is sane");
    assertFalse(low.failed(), low.message());

    Recorder high = new Recorder();
    Numbers.inRange(high, ABORTS, 10, 1, 10, "the page size is sane");
    assertFalse(high.failed(), high.message());
  }

  @Test
  @DisplayName("a range with the bounds the wrong way round says so")
  void invertedRange() {
    Recorder seat = new Recorder();
    Numbers.inRange(seat, ABORTS, 5, 10, 1, "the page size is sane");

    assertTrue(seat.failed(), "a range that can hold nothing is the mistake, not the value");
    assertTrue(named(seat, "in-range"), seat.message());
  }

  @Test
  @DisplayName("NaN is in no range")
  void nanIsInNoRange() {
    Recorder seat = new Recorder();
    Numbers.inRange(
        seat, ABORTS, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
        "the reading is a number");

    assertTrue(seat.failed(), "the widest range there is still does not hold NaN");
  }

  @Test
  @DisplayName("any Number counts, and anything else is reported as that")
  void requiresANumber() {
    Recorder integer = new Recorder();
    Numbers.inRange(integer, ABORTS, 5L, 1, 10, "the page size is sane");
    assertFalse(integer.failed(), integer.message());

    Recorder text = new Recorder();
    Numbers.closeTo(text, ABORTS, "5", 5.0, 0.1, "the rate is about five");
    assertTrue(text.failed(), "text is not a number, however it reads");
    assertTrue(named(text, "close-to"), text.message());
    assertEquals("5", text.failures().get(0).detail().get("got"), text.message());

    Recorder absent = new Recorder();
    Numbers.inRange(absent, ABORTS, null, 1, 10, "the page size is sane");
    assertTrue(absent.failed(), "null is not a number");
    assertTrue(named(absent, "in-range"), absent.message());
  }

  /** Whether the seat's first record names that assertion. */
  private static boolean named(Recorder seat, String assertion) {
    return !seat.failures().isEmpty()
        && seat.failures().get(0).assertion().equals(assertion);
  }

}
