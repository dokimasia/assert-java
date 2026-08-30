package dev.dokimi.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The two surfaces, and how they differ.
///
/// The conformance run drives both against the corpus and asks only whether each case
/// passed or failed. What it cannot see is which path the failure took, which is the one
/// thing the two surfaces disagree about.
class SurfaceTest {

  @Test
  @DisplayName("check reports through the path that stops the test")
  void checkAborts() {
    Recorder seat = new Recorder();
    Check.equal(seat, 1, 2, "the count is right");

    assertTrue(seat.failed(), "the failure has to reach the seat");
    assertEquals(List.of(), seat.messages(), "check does not record, it fails");
  }

  @Test
  @DisplayName("soft reports through the path the test carries on past")
  void softRecords() {
    Recorder seat = new Recorder();
    Soft.equal(seat, 1, 2, "the count is right");

    assertTrue(seat.failed(), "the failure has to reach the seat");
    assertEquals(1, seat.messages().size(), "soft records rather than failing");
  }

  @Test
  @DisplayName("soft carries on, so one call reports every failure")
  void softCollectsAll() {
    Recorder seat = new Recorder();
    Soft.equal(seat, 1, 2, "the count is right");
    Soft.isTrue(seat, false, "the flag is set");
    Soft.hasPrefix(seat, "GET", "POST", "the method is right");

    assertEquals(3, seat.messages().size(), "three failing assertions, three reports");
  }

  @Test
  @DisplayName("both surfaces say the same thing about the same failure")
  void bothSaySoTheSame() {
    Recorder aborting = new Recorder();
    Check.equal(aborting, 1, 2, "the count is right");

    Recorder recording = new Recorder();
    Soft.equal(recording, 1, 2, "the count is right");

    assertEquals(aborting.message(), recording.message(), "only the path differs, not the text");
  }

  @Test
  @DisplayName("a passing assertion reaches the seat as a helper call and nothing else")
  void passingReportsNothing() {
    Recorder seat = new Recorder();
    Check.equal(seat, 1, 1, "the count is right");

    assertFalse(seat.failed(), seat.message());
    assertTrue(seat.helperCalls() > 0, "the frame is still marked as the library's");
  }

  @Test
  @DisplayName("rejects passes when the body fails, and hands back what it said")
  void rejectsAFailingBody() {
    Recorder seat = new Recorder();
    String reported =
        Check.rejects(
            seat, "an empty name is refused", trial -> Check.isNotEmpty(trial, "", "the name"));

    assertFalse(seat.failed(), seat.message());
    assertTrue(reported.contains("the name"), reported);
  }

  @Test
  @DisplayName("rejects reports a body that passes, because that is the failure")
  void rejectsAPassingBody() {
    Recorder seat = new Recorder();
    String reported =
        Check.rejects(
            seat, "an empty name is refused", trial -> Check.isNotEmpty(trial, "ada", "the name"));

    assertTrue(seat.failed(), "a body that reported nothing is what rejects is looking for");
    assertTrue(seat.message().contains("reported no failure"), seat.message());
    assertEquals("", reported, "there is no failure text to hand back");
  }

  @Test
  @DisplayName("rejects sees a soft assertion inside the body too")
  void rejectsASoftBody() {
    Recorder seat = new Recorder();
    Check.rejects(seat, "an empty name is refused", trial -> Soft.isNotEmpty(trial, "", "name"));

    assertFalse(seat.failed(), "recorded or thrown, the body failed either way");
  }

  @Test
  @DisplayName("the standard seat throws through both paths")
  void standardThrows() {
    Standard seat = new Standard();

    assertThrows(
        AssertionFailed.class, () -> Check.equal(seat, 1, 2, "the count is right"),
        "check stops the test");
    assertThrows(
        AssertionFailed.class, () -> Soft.equal(seat, 1, 2, "the count is right"),
        "a recorded failure needs somewhere to report, and a bare seat has no end");
  }

  @Test
  @DisplayName("the collector throws on check and holds what soft recorded")
  void collectorSplitsThePaths() {
    Collector seat = new Collector();

    Soft.equal(seat, 1, 2, "the count is right");
    Soft.isTrue(seat, false, "the flag is set");
    assertThrows(AssertionFailed.class, () -> Check.equal(seat, 1, 2, "the count is right"));

    AssertionFailed thrown = assertThrows(AssertionFailed.class, seat::flush);
    assertTrue(thrown.getMessage().contains("the count is right"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("the flag is set"), thrown.getMessage());
  }

  @Test
  @DisplayName("a collector with nothing recorded flushes quietly")
  void collectorFlushesQuietly() {
    Collector seat = new Collector();
    Soft.equal(seat, 1, 1, "the count is right");

    seat.flush();
  }
}
