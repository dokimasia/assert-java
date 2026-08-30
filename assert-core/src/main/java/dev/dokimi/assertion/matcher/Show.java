package dev.dokimi.assertion.matcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Rendering a value into a failure message.
///
/// A failure is read by someone who cannot see the value, so the text has to carry it.
/// `String.valueOf` on an array gives an identity hash, which says nothing at all.
@NullMarked
public final class Show {

  /// How much of a value a failure message will carry.
  private static final int MAX_LENGTH = 200;

  private Show() {}

  /// Answer a short, readable rendering of any value.
  ///
  /// Quotes strings so an empty one is visible, renders an array by its elements rather
  /// than its identity, and shows the entries of a collection or map. Long values are cut,
  /// with the cut marked.
  ///
  /// @param value anything an assertion was handed
  /// @return the value as it should appear in a failure
  public static String value(@Nullable Object value) {
    String rendered = render(value);
    return rendered.length() <= MAX_LENGTH ? rendered : rendered.substring(0, MAX_LENGTH) + "…";
  }

  private static String render(@Nullable Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String text) {
      return "\"" + text + "\"";
    }
    if (value instanceof Character c) {
      return "'" + c + "'";
    }
    if (value.getClass().isArray()) {
      return renderArray(value);
    }
    if (value instanceof Map<?, ?> map) {
      return map.entrySet().stream()
          .map(e -> render(e.getKey()) + "=" + render(e.getValue()))
          .collect(Collectors.joining(", ", "{", "}"));
    }
    if (value instanceof Collection<?> items) {
      return items.stream().map(Show::render).collect(Collectors.joining(", ", "[", "]"));
    }
    if (value instanceof Throwable t) {
      return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
    return String.valueOf(value);
  }

  private static String renderArray(Object array) {
    if (array instanceof Object[] items) {
      return Arrays.stream(items).map(Show::render).collect(Collectors.joining(", ", "[", "]"));
    }
    // A primitive array has no Object[] view, and each has its own
    // deepToString-free spelling.
    return Arrays.deepToString(new Object[] {array})
        .replaceFirst("^\\[", "")
        .replaceFirst("\\]$", "");
  }
}
