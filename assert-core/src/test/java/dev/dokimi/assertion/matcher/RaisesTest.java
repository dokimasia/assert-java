package dev.dokimi.assertion.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Recorder;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A call that throws, and one that does not.
class RaisesTest {

  private static final Mode ABORTS = Mode.FATAL;

  @Test
  @DisplayName("throwsException hands back what was thrown")
  void throwsExceptionReturnsTheThrowable() {
    IOException thrown = new IOException("disk gone");

    Recorder seat = new Recorder();
    Throwable caught =
        Raises.throwsException(
            seat,
            ABORTS,
            () -> {
              throw thrown;
            },
            "saving refuses a full disk");

    assertFalse(seat.failed(), seat.message());
    assertSame(thrown, caught, "the throwable is handed back so its type can be asserted on");
  }

  @Test
  @DisplayName("throwsException reports a body that returns")
  void throwsExceptionOnAQuietBody() {
    Recorder seat = new Recorder();
    Throwable caught = Raises.throwsException(seat, ABORTS, () -> {}, "saving refuses");

    assertTrue(seat.failed(), "a body that returns must be reported");
    assertNull(caught, "nothing was thrown, so there is nothing to hand back");
    assertTrue(seat.message().contains("returned without throwing"), seat.message());
  }

  @Test
  @DisplayName("throwsException catches a checked exception without a wrapper")
  void throwsExceptionCatchesChecked() {
    Recorder seat = new Recorder();
    Raises.throwsException(
        seat,
        ABORTS,
        () -> {
          throw new IOException("disk gone");
        },
        "saving refuses a full disk");

    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("throwsException counts an Error as thrown")
  void throwsExceptionCountsErrors() {
    Recorder seat = new Recorder();
    Throwable caught =
        Raises.throwsException(
            seat,
            ABORTS,
            () -> {
              throw new StackOverflowError();
            },
            "the recursion runs out of stack");

    assertFalse(seat.failed(), seat.message());
    assertTrue(caught instanceof StackOverflowError, "an Error is still a throw");
  }

  @Test
  @DisplayName("doesNotThrow passes a quiet body and reports a throwing one")
  void doesNotThrow() {
    Recorder passing = new Recorder();
    Raises.doesNotThrow(passing, ABORTS, () -> {}, "parsing accepts this input");
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Raises.doesNotThrow(
        failing,
        ABORTS,
        () -> {
          throw new IllegalArgumentException("bad input");
        },
        "parsing accepts this input");

    assertTrue(failing.failed(), "a throw must be reported");
    assertTrue(failing.message().contains("bad input"), failing.message());
  }
}
