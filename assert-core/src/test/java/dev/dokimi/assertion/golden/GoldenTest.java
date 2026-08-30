package dev.dokimi.assertion.golden;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dokimi.assertion.Recorder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Comparison against a recorded file. */
class GoldenTest {

  private static final boolean UPDATING = true;
  private static final boolean CHECKING = false;

  @TempDir Path dir;

  private Path written(String content) throws IOException {
    Path path = dir.resolve("golden.json");
    Files.writeString(path, content);
    return path;
  }

  @Test
  @DisplayName("a nested object is read whole, not truncated at its first brace")
  void nestedObjectsAreReadWhole() throws IOException {
    // A regular expression cannot find where a value ends. The earlier
    // one captured `{"a": 1` here and compared that fragment.
    Path path = written("{\n  \"items\": {\"a\": 1},\n  \"other\": 2\n}");

    Recorder passing = new Recorder();
    Golden.matchJsonField(passing, path, "items", "{\"a\": 1}", CHECKING);
    assertFalse(passing.failed(), passing.message());

    Recorder failing = new Recorder();
    Golden.matchJsonField(failing, path, "items", "{\"a\": 2}", CHECKING);
    assertTrue(failing.failed(), "a changed nested value must be reported");
  }

  @Test
  @DisplayName("an array of objects is read whole")
  void arraysOfObjectsAreReadWhole() throws IOException {
    Path path = written("{\n  \"items\": [{\"a\": 1}, {\"b\": 2}]\n}");

    Recorder seat = new Recorder();
    Golden.matchJsonField(seat, path, "items", "[{\"a\": 1}, {\"b\": 2}]", CHECKING);
    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("a brace inside a string is not a structural brace")
  void bracesInsideStringsAreText() throws IOException {
    Path path = written("{\n  \"items\": \"a } and a , inside\"\n}");

    Recorder seat = new Recorder();
    Golden.matchJsonField(seat, path, "items", "\"a } and a , inside\"", CHECKING);
    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("a scalar field still reads as itself")
  void scalarsStillWork() throws IOException {
    Path path = written("{\n  \"items\": [1, 2],\n  \"other\": 3\n}");

    Recorder seat = new Recorder();
    Golden.matchJsonField(seat, path, "items", "[1, 2]", CHECKING);
    assertFalse(seat.failed(), seat.message());
  }

  @Test
  @DisplayName("a missing field says how to add it")
  void missingFieldIsReported() throws IOException {
    Recorder seat = new Recorder();
    Golden.matchJsonField(seat, written("{\"other\": 1}"), "absent", "[1]", CHECKING);

    assertTrue(seat.message().contains(Golden.UPDATE_ENV), seat.message());
  }

  @Test
  @DisplayName("a missing file says how to create it")
  void missingFileIsReported() {
    Recorder seat = new Recorder();
    Golden.matchAt(seat, dir.resolve("absent.txt"), "content", CHECKING);

    assertTrue(seat.message().contains("does not exist"), seat.message());
  }

  @Test
  @DisplayName("an update writes the file, and a check then passes")
  void updateThenCheck() {
    Path path = dir.resolve("new.txt");

    Recorder updating = new Recorder();
    Golden.matchAt(updating, path, "recorded output", UPDATING);
    assertFalse(updating.failed(), updating.message());

    Recorder checking = new Recorder();
    Golden.matchAt(checking, path, "recorded output", CHECKING);
    assertFalse(checking.failed(), checking.message());
  }

  @Test
  @DisplayName("scrubbers replace what changes every run")
  void scrubbersWork() throws IOException {
    Path path = dir.resolve("scrubbed.txt");
    Files.writeString(path, "at SCRUBBED_TIMESTAMP");

    Recorder seat = new Recorder();
    Golden.matchAt(seat, path, "at 2026-08-30T11:22:33Z", CHECKING, Golden.scrubTimestamps());

    assertFalse(seat.failed(), seat.message());
  }
}
