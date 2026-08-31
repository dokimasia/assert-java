package dev.dokimi.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The record a failing assertion reports, and the sentence it renders to. */
class FailureTest {

  /** A record carrying the given detail, in the order stated. */
  private static Failure carrying(String assertion, Object... pairs) {
    Map<String, Object> detail = new LinkedHashMap<>();
    for (int at = 0; at < pairs.length; at += 2) {
      detail.put(String.valueOf(pairs[at]), pairs[at + 1]);
    }
    return new Failure(assertion, "the stated contract", detail, null);
  }

  @Nested
  @DisplayName("render")
  class Render {

    @Test
    @DisplayName("answers the contract alone when nothing is carried")
    void contractAlone() {
      assertEquals("the stated contract", carrying("true").render());
    }

    @Test
    @DisplayName("says want before got")
    void wantLeads() {
      assertEquals(
          "the stated contract: want 3, got 2",
          carrying("length", "got", 2, "want", 3).render());
    }

    @Test
    @DisplayName("puts a field it does not know after the ones it does")
    void unknownFollows() {
      assertEquals(
          "the stated contract: got 2, apple 3, zebra 1",
          carrying("made-up", "zebra", 1, "got", 2, "apple", 3).render());
    }
  }

  @Nested
  @DisplayName("a reported failure")
  class Reported {

    @Test
    @DisplayName("carries the call site the assertion was written on")
    void carriesTheCallSite() {
      Recorder seat = new Recorder();
      Check.equal(seat, 1, 2, "the values match");

      Where where = seat.failures().get(0).where();
      assertNotNull(where, "the record carries a call site");
      assertEquals("FailureTest.java", where.file(), "it names the file the caller wrote in");
      assertTrue(where.line() > 0, "it names a line");
    }
  }
}
