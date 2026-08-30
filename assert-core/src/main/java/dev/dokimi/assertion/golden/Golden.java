package dev.dokimi.assertion.golden;

import dev.dokimi.assertion.Seat;
import dev.dokimi.assertion.matcher.Mode;
import dev.dokimi.assertion.matcher.Report;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Comparison against a recorded file.
///
/// A golden file holds output a test compares against, so a change to rendering shows up
/// as a diff rather than as a rewritten assertion. Set `DOKIMI_ASSERT_UPDATE_GOLDEN=1` to
/// rewrite the files, and read the diff before you do: an update accepts whatever the
/// code now does, which is the opposite of an assertion.
@NullMarked
public final class Golden {

  /// The variable that turns rewriting on.
  public static final String UPDATE_ENV = "DOKIMI_ASSERT_UPDATE_GOLDEN";

  /// Where [#match] looks for a file named rather than pathed.
  public static final String GOLDEN_DIR = "src/test/resources/testdata";

  private static final Pattern TIMESTAMP =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?");
  private static final Pattern HASH = Pattern.compile("\\b[0-9a-f]{32,128}\\b");
  private static final Pattern RUN_ID = Pattern.compile("\\brun_[0-9a-zA-Z]{16}\\b");

  private Golden() {}

  /// A replacement applied to both sides before they are compared.
  public interface Scrubber extends UnaryOperator<String> {}

  /// Whether this run may rewrite its golden files.
  ///
  /// @return true when the update variable is set to anything but 0
  public static boolean shouldUpdate() {
    String set = System.getenv(UPDATE_ENV);
    return set != null && !set.isEmpty() && !set.equals("0");
  }

  /// Replace ISO-8601 and RFC-3339 timestamps.
  ///
  /// @return a scrubber, to pass to a comparing call
  public static Scrubber scrubTimestamps() {
    return text -> TIMESTAMP.matcher(text).replaceAll("SCRUBBED_TIMESTAMP");
  }

  /// Replace hex digests between 32 and 128 characters.
  ///
  /// @return a scrubber, to pass to a comparing call
  public static Scrubber scrubHashes() {
    return text -> HASH.matcher(text).replaceAll("SCRUBBED_HASH");
  }

  /// Replace identifiers shaped like `run_` and sixteen characters.
  ///
  /// @return a scrubber, to pass to a comparing call
  public static Scrubber scrubRunIds() {
    return text -> RUN_ID.matcher(text).replaceAll("SCRUBBED_RUN_ID");
  }

  /// Replace the value of each named JSON field.
  ///
  /// Matches the field's text rather than parsing, so it works on output that is nearly
  /// JSON as well as output that is.
  ///
  /// @param fields the field names whose values are replaced
  /// @return a scrubber, to pass to a comparing call
  public static Scrubber scrubJsonFields(String... fields) {
    if (fields.length == 0) {
      return text -> text;
    }
    String names = String.join("|", List.of(fields).stream().map(Pattern::quote).toList());
    Pattern pattern = Pattern.compile("(\"(?:" + names + ")\"\\s*:\\s*)\"[^\"]*\"");
    return text -> pattern.matcher(text).replaceAll("$1\"SCRUBBED\"");
  }

  /// Compare got against the golden file at path.
  ///
  /// @param seat where the failure is reported
  /// @param path where the golden file lives
  /// @param got the output produced
  /// @param update whether a mismatch rewrites the file instead of failing
  /// @param scrubbers replacements applied to both sides before comparing
  public static void matchAt(
      Seat seat, Path path, String got, boolean update, Scrubber... scrubbers) {
    seat.helper();

    String recorded;
    try {
      recorded = Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException missing) {
      if (!update) {
        seat.fail(
            path + ": the golden file does not exist; set " + UPDATE_ENV + "=1 to create it");
        return;
      }
      write(seat, path, got);
      return;
    }

    String mine = scrub(got, scrubbers);
    String theirs = scrub(recorded, scrubbers);
    if (mine.equals(theirs)) {
      return;
    }
    if (update) {
      write(seat, path, got);
      return;
    }
    Report.to(
        seat,
        Mode.FATAL,
        path + ": output does not match the golden file; read the diff before setting "
            + UPDATE_ENV + "=1\n--- want\n" + theirs + "\n+++ got\n" + mine);
  }

  /// Compare got against the golden file of the given name.
  ///
  /// The name is resolved against the conventional directory, so a test names its file
  /// rather than repeating a path.
  ///
  /// @param seat where the failure is reported
  /// @param name the golden file's name
  /// @param got the output produced
  /// @param update whether a mismatch rewrites the file instead of failing
  /// @param scrubbers replacements applied to both sides before comparing
  public static void match(
      Seat seat, String name, String got, boolean update, Scrubber... scrubbers) {
    seat.helper();
    matchAt(seat, Path.of(GOLDEN_DIR, name), got, update, scrubbers);
  }

  /// Compare got against one named field of the JSON object at path.
  ///
  /// Use it where one golden file holds several independent values, one per field, so a
  /// failure shows that value's diff rather than the whole file's, and two tests updating
  /// different fields do not overwrite each other.
  ///
  /// Matching is textual, on the field's raw value, so this needs no JSON parser and the
  /// library keeps its zero runtime dependencies.
  ///
  /// @param seat where the failure is reported
  /// @param path where the golden file lives
  /// @param field the field to compare
  /// @param got the value as JSON text
  /// @param update whether a mismatch rewrites the file instead of failing
  /// @param scrubbers replacements applied to both sides before comparing
  public static void matchJsonField(
      Seat seat, Path path, String field, String got, boolean update, Scrubber... scrubbers) {
    seat.helper();

    String recorded;
    try {
      recorded = Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException missing) {
      if (!update) {
        seat.fail(
            path + ": the golden file does not exist; set " + UPDATE_ENV + "=1 to create it");
        return;
      }
      write(seat, path, "{\n  \"" + field + "\": " + got + "\n}\n");
      return;
    }

    String theirRaw = rawValueOf(recorded, field);
    if (theirRaw == null) {
      if (!update) {
        seat.fail(
            path + ": the golden file has no field \"" + field + "\"; set " + UPDATE_ENV
                + "=1 to add it");
        return;
      }
      write(seat, path, recorded.replaceFirst("\\}\\s*$", "  ,\"" + field + "\": " + got + "\n}\n"));
      return;
    }

    String mine = scrub(got.trim(), scrubbers);
    String theirs = scrub(theirRaw.trim(), scrubbers);
    if (mine.equals(theirs)) {
      return;
    }
    if (update) {
      write(seat, path, recorded.replace(theirRaw, got.trim()));
      return;
    }
    Report.to(
        seat,
        Mode.FATAL,
        path + ": field \"" + field + "\" does not match the golden file; read the diff "
            + "before setting " + UPDATE_ENV + "=1\n--- want\n" + theirs + "\n+++ got\n" + mine);
  }

  /// Answer a field's raw JSON value, or null when the field is absent.
  ///
  /// Scans rather than matching a pattern. A regular expression cannot find where a
  /// value ends: it has to stop at a brace, and a nested object or an array of objects
  /// carries braces of its own. The earlier pattern captured `{"a": 1` from
  /// `{"items": {"a": 1}}`, then compared that fragment and reported whatever it liked.
  ///
  /// This tracks nesting depth and ignores anything inside a string, which is what
  /// separates a structural brace from one in the text.
  private static @Nullable String rawValueOf(String document, String field) {
    String key = "\"" + field + "\"";
    int at = document.indexOf(key);
    if (at < 0) {
      return null;
    }

    int colon = document.indexOf(':', at + key.length());
    if (colon < 0) {
      return null;
    }

    int start = colon + 1;
    while (start < document.length() && Character.isWhitespace(document.charAt(start))) {
      start++;
    }

    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = start; i < document.length(); i++) {
      char c = document.charAt(i);

      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\' && inString) {
        escaped = true;
        continue;
      }
      if (c == '"') {
        inString = !inString;
        continue;
      }
      if (inString) {
        continue;
      }

      if (c == '{' || c == '[') {
        depth++;
      } else if (c == '}' || c == ']') {
        if (depth == 0) {
          return document.substring(start, i).trim();
        }
        depth--;
      } else if (c == ',' && depth == 0) {
        return document.substring(start, i).trim();
      }
    }
    return document.substring(start).trim();
  }

  private static String scrub(String text, Scrubber[] scrubbers) {
    String scrubbed = text;
    for (Scrubber scrubber : scrubbers) {
      scrubbed = scrubber.apply(scrubbed);
    }
    return scrubbed;
  }

  /// Write content to target, reporting a failure rather than throwing.
  private static void write(Seat seat, Path target, String content) {
    try {
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(target, content, StandardCharsets.UTF_8);
    } catch (IOException unwritable) {
      seat.fail(target + ": the golden file could not be written: " + unwritable.getMessage());
    }
  }
}
