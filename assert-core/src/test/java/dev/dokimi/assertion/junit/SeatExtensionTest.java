package dev.dokimi.assertion.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.AssertionFailed;
import dev.dokimi.assertion.Check;
import dev.dokimi.assertion.Collector;
import dev.dokimi.assertion.Soft;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** The extension, used the way a consumer would use it. */
class SeatExtensionTest {

  @RegisterExtension final SeatExtension seat = new SeatExtension();

  @Test
  @DisplayName("a passing test leaves the seat empty")
  void aPassingTestLeavesTheSeatEmpty() {
    Check.equal(seat.get(), 1, 1, "it holds");
    Soft.contains(seat.get(), List.of(1, 2), 1, "it is there");

    assertTrue(seat.get().collected().isEmpty());
  }

  @Test
  @DisplayName("check throws on the extension's seat")
  void checkThrows() {
    assertThrows(AssertionFailed.class, () -> Check.equal(seat.get(), 1, 2, "it holds"));
  }

  @Test
  @DisplayName("soft collects, and flushing reports every failure at once")
  void softCollects() {
    Collector held = seat.get();
    Soft.equal(held, 1, 2, "the first contract");
    Soft.equal(held, 3, 4, "the second contract");

    assertEquals(2, held.collected().size());

    // Flush here, or the extension would fail this test at the end.
    AssertionFailed thrown = assertThrows(AssertionFailed.class, held::flush);
    assertTrue(thrown.getMessage().startsWith("2 failures:"), thrown.getMessage());
  }

  @Test
  @DisplayName("each test gets a seat of its own")
  void seatsDoNotLeakBetweenTests() {
    assertTrue(seat.get().collected().isEmpty(), "a seat carried over from another test");
  }
}
