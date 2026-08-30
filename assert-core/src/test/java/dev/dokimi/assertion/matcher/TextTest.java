package dev.dokimi.assertion.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Recorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Text, and the three cases no corpus file can hold.
///
/// The corpus drives prefix, suffix and pattern matching against text. What it cannot
/// state is a subject that is not text at all, a pattern that does not compile, or a
/// value that only happens to be a CharSequence.
class TextTest {

  private static final Mode ABORTS = Mode.FATAL;

  @Test
  @DisplayName("hasPrefix and hasSuffix hold and fail on text")
  void prefixAndSuffix() {
    Recorder passing = new Recorder();
    Text.hasPrefix(passing, ABORTS, "GET /users", "GET ", "the log line names the method");
    Text.hasSuffix(passing, ABORTS, "GET /users", "/users", "the log line names the path");
    assertFalse(passing.failed(), passing.message());

    Recorder wrongPrefix = new Recorder();
    Text.hasPrefix(wrongPrefix, ABORTS, "GET /users", "POST ", "the log line names the method");
    assertTrue(wrongPrefix.failed(), "a prefix that is not there must be reported");
    assertTrue(wrongPrefix.message().contains("does not start with"), wrongPrefix.message());

    Recorder wrongSuffix = new Recorder();
    Text.hasSuffix(wrongSuffix, ABORTS, "GET /users", "/orders", "the log line names the path");
    assertTrue(wrongSuffix.failed(), "a suffix that is not there must be reported");
    assertTrue(wrongSuffix.message().contains("does not end with"), wrongSuffix.message());
  }

  @Test
  @DisplayName("a subject that is not text is reported as that, not as a failing comparison")
  void notTextAtAll() {
    Recorder number = new Recorder();
    Text.hasPrefix(number, ABORTS, 42, "4", "the log line names the method");
    assertTrue(number.failed(), "a number has no prefix");
    assertTrue(number.message().contains("requires text"), number.message());
    assertTrue(number.message().contains("Integer"), number.message());

    Recorder absent = new Recorder();
    Text.hasSuffix(absent, ABORTS, null, "s", "the log line names the path");
    assertTrue(absent.failed(), "null has no suffix");
    assertTrue(absent.message().contains("requires text"), absent.message());
    assertTrue(absent.message().contains("null"), absent.message());
  }

  @Test
  @DisplayName("any CharSequence counts, not only String")
  void anyCharSequence() {
    Recorder seat = new Recorder();
    Text.hasPrefix(seat, ABORTS, new StringBuilder("GET /users"), "GET ", "the method is named");

    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("matches searches rather than anchoring")
  void matchesSearches() {
    Recorder searching = new Recorder();
    Text.matches(searching, ABORTS, "GET /users 200", "users", "the line names the collection");
    assertFalse(searching.failed(), searching.message());

    Recorder anchored = new Recorder();
    Text.matches(anchored, ABORTS, "GET /users 200", "^users", "the line starts with the path");
    assertTrue(anchored.failed(), "an anchor is honoured where one is written");
  }

  @Test
  @DisplayName("a pattern that does not compile is reported as a broken pattern")
  void brokenPattern() {
    Recorder seat = new Recorder();
    Text.matches(seat, ABORTS, "GET /users", "[unclosed", "the line names the collection");

    assertTrue(seat.failed(), "a pattern that cannot compile has to say so");
    assertTrue(seat.message().contains("does not compile"), seat.message());
    // Without this the message reads as a subject that failed to match,
    // and the typo goes looking for a bug in the code under test.
    assertFalse(seat.message().contains("does not match"), seat.message());
  }
}
