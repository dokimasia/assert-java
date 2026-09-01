package dev.dokimi.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Several assertions about one value, joined with a dot.
///
/// What the chain adds over the static form is that the value's type is fixed once, so
/// `want` is held to it. That half is checked by the compiler and cannot be driven from
/// here: a case proving it would not build. `AssertionTypingTest` states the calls that
/// do compile, and the ones that must not are named in this class's documentation.
class AssertionTest {

  @Test
  @DisplayName("every method answers the receiver, so the chain continues")
  void methodsChain() {
    Recorder seat = new Recorder();
    Assertion<Integer> chain = Soft.that(seat, 200);

    assertSame(chain, chain.equal(200, "the status is right"));
    assertSame(chain, chain.notEqual(0, "the status was set"));
    assertSame(chain, chain.isNotNull("a status came back"));
  }

  @Test
  @DisplayName("a chain of assertions that hold reports nothing")
  void holdingChainReportsNothing() {
    Recorder seat = new Recorder();
    Check.that(seat, "widget-1").hasPrefix("widget", "the id is a widget's").length(8, "the id is whole");

    assertFalse(seat.failed(), () -> "a holding chain reports nothing: " + seat.message());
  }

  @Test
  @DisplayName("an aborting chain stops at the first failing method")
  void abortingChainStopsAtTheFirstFailure() {
    Standard seat = new Standard();

    AssertionFailed thrown =
        assertThrows(
            AssertionFailed.class,
            () ->
                Check.that(seat, 500)
                    .equal(200, "the request succeeds")
                    .equal(201, "and it created something"));

    assertTrue(
        thrown.getMessage().contains("the request succeeds"),
        () -> "the first failing method is the one that throws: " + thrown.getMessage());
    assertFalse(
        thrown.getMessage().contains("created something"),
        "the method after a failure does not run");
  }

  @Test
  @DisplayName("a recording chain runs every method and reports each failure")
  void recordingChainRunsOn() {
    Recorder seat = new Recorder();
    Soft.that(seat, 500).equal(200, "the request succeeds").equal(201, "and it created something");

    assertEquals(2, seat.messages().size(), "both failing methods report");
  }

  @Test
  @DisplayName("the chain holds the value, so a later mutation is what a method sees")
  void theChainHoldsTheValue() {
    List<String> items = new java.util.ArrayList<>();
    Recorder seat = new Recorder();
    Assertion<List<String>> chain = Soft.that(seat, items);

    items.add("widget");
    chain.length(1, "the item was added");

    assertFalse(seat.failed(), () -> "it reads the list as it now is: " + seat.message());
  }

  @Test
  @DisplayName("a chain reports the same failure the static form does")
  void theChainAgreesWithTheStaticForm() {
    Recorder chained = new Recorder();
    Recorder statics = new Recorder();

    Check.that(chained, 1).equal(2, "the count is right");
    Check.equal(statics, 1, 2, "the count is right");

    assertEquals(statics.message(), chained.message(), "one comparison, one wording");
  }
}
