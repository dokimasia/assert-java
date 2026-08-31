package dev.dokimi.assertion.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Recorder;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Errors, and the chain of causes each assertion follows.
///
/// Every assertion is driven twice, with an error that satisfies it and one that does
/// not. An assertion that reports nothing whatever it is handed passes a one-sided test.
class ErrorsTest {

  private static final Mode ABORTS = Mode.FATAL;

  /// An error with a name of its own, so a class match is not a match on Exception.
  private static final class Refused extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Refused(String message) {
      super(message);
    }
  }

  @Test
  @DisplayName("noError passes on null and reports an error")
  void noError() {
    Recorder passing = new Recorder();
    Errors.noError(passing, ABORTS, null, "the call succeeds");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Errors.noError(failing, ABORTS, new IOException("disk gone"), "the call succeeds");
    assertTrue(failing.failed(), "an error must be reported");
    assertTrue(failing.message().contains("disk gone"), failing.message());
  }

  @Test
  @DisplayName("hasError passes on an error and reports null")
  void hasError() {
    Recorder passing = new Recorder();
    Errors.hasError(passing, ABORTS, new IOException("disk gone"), "the call refuses");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Errors.hasError(failing, ABORTS, null, "the call refuses");
    assertTrue(failing.failed(), "the absence of an error must be reported");
  }

  @Test
  @DisplayName("errorIs matches the same instance")
  void errorIsSameInstance() {
    Refused sentinel = new Refused("no room");

    Recorder passing = new Recorder();
    Errors.errorIs(passing, ABORTS, sentinel, sentinel, "the call refuses for the stated reason");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Errors.errorIs(
        failing, ABORTS, new Refused("some other reason"), sentinel,
        "the call refuses for the stated reason");
    assertTrue(failing.failed(), "a throwable target matches on class and message, not class alone");
  }

  @Test
  @DisplayName("a throwable target matches a rebuilt error with the same class and message")
  void errorIsMatchesARebuiltError() {
    // A sentinel that crosses a serialisation boundary comes back as a
    // different object, and matching on identity alone would miss it.
    Recorder seat = new Recorder();
    Errors.errorIs(
        seat, ABORTS, new Refused("no room"), new Refused("no room"), "the reason is the same");

    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("errorIs matches by class, and rejects a different class")
  void errorIsByClass() {
    Recorder passing = new Recorder();
    Errors.errorIs(passing, ABORTS, new Refused("no room"), Refused.class, "the call refuses");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Errors.errorIs(
        failing, ABORTS, new IOException("disk gone"), Refused.class, "the call refuses");
    assertTrue(failing.failed(), "a different class must be reported");
    assertTrue(named(failing, "err-is"), failing.message());
  }

  @Test
  @DisplayName("errorIs reaches a wrapped cause")
  void errorIsThroughTheChain() {
    Refused root = new Refused("no room");
    Throwable wrapped = new IllegalStateException("while saving", root);

    Recorder passing = new Recorder();
    Errors.errorIs(passing, ABORTS, wrapped, root, "the reason survives wrapping");
    assertFalse(passing.failed(), passing.message());

    // The other direction is not a match: root does not wrap wrapped.
    Recorder failing = new Recorder();
    Errors.errorIs(failing, ABORTS, root, wrapped, "the reason survives wrapping");
    assertTrue(failing.failed(), "a cause is not its own wrapper");
  }

  @Test
  @DisplayName("errorIsNot is the mirror of errorIs")
  void errorIsNot() {
    Recorder passing = new Recorder();
    Errors.errorIsNot(
        passing, ABORTS, new IOException("disk gone"), Refused.class, "the call fails otherwise");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Errors.errorIsNot(
        failing, ABORTS, new Refused("no room"), Refused.class, "the call fails otherwise");
    assertTrue(failing.failed(), "a match must be reported");
    assertTrue(named(failing, "err-is-not"), failing.message());
  }

  @Test
  @DisplayName("errorAs hands back the typed error so its fields can be read")
  void errorAsReturnsTheError() {
    Throwable wrapped = new IllegalStateException("while saving", new Refused("no room"));

    Recorder passing = new Recorder();
    Refused found = Errors.errorAs(passing, ABORTS, wrapped, Refused.class, "the reason is typed");
    assertFalse(passing.failed(), passing.message());
    assertNotNull(found);
    assertEquals("no room", found.getMessage());
  }

  @Test
  @DisplayName("errorAs answers null and reports when nothing in the chain matches")
  void errorAsFindsNothing() {
    Recorder failing = new Recorder();
    Refused found =
        Errors.errorAs(
            failing, ABORTS, new IOException("disk gone"), Refused.class, "the reason is typed");

    assertNull(found, "nothing matched, so there is nothing to hand back");
    assertTrue(failing.failed(), "a chain with no match must be reported");
    assertTrue(failing.message().contains("Refused"), failing.message());
  }

  @Test
  @DisplayName("errorAs on a null error reports rather than throwing")
  void errorAsOnNull() {
    Recorder seat = new Recorder();
    assertNull(Errors.errorAs(seat, ABORTS, null, Refused.class, "the reason is typed"));
    assertTrue(seat.failed(), "no error is no match");
  }

  @Test
  @DisplayName("a chain that loops back on itself stops rather than spinning")
  void cyclicChainTerminates() {
    Throwable first = new IllegalStateException("first");
    Throwable second = new IllegalStateException("second");
    first.initCause(second);
    second.initCause(first);

    Recorder seat = new Recorder();
    Errors.errorIs(seat, ABORTS, first, NoSuchElementException.class, "the chain is walked once");

    assertTrue(seat.failed(), "nothing in the loop matches, so it must be reported");
  }

  @Test
  @DisplayName("a target that is neither a class nor a throwable never matches")
  void unusableTarget() {
    Recorder seat = new Recorder();
    Errors.errorIs(seat, ABORTS, new Refused("no room"), "Refused", "the target is a throwable");

    assertTrue(seat.failed(), "a string is not something an error can be");
  }

  /** Whether the seat's first record names that assertion. */
  private static boolean named(Recorder seat, String assertion) {
    return !seat.failures().isEmpty()
        && seat.failures().get(0).assertion().equals(assertion);
  }

}
