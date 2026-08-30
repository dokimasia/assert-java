package dev.dokimi.assertion.matcher;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Structural equality, as the standard defines it.
///
/// `Object.equals` answers a different question in three places this has to
/// correct. It says NaN equals itself, which IEEE 754 denies. It says 0.0 and -0.0 differ,
/// which every other implementation of this standard treats as equal. And it compares
/// arrays by identity, so two arrays holding the same elements are unequal, including when
/// they sit inside a list that otherwise compares by value.
@NullMarked
public final class Compare {

  /// How deep a comparison walks before calling the value cyclic.
  private static final int MAX_DEPTH = 100;

  private Compare() {}

  /// Whether two values are structurally equal.
  ///
  /// Reaches inside arrays, collections and maps. Values of different classes never
  /// compare, which is what makes an Integer unequal to a Long holding the same number.
  ///
  /// @param got the value produced by the code under test
  /// @param want the value it is supposed to produce
  /// @param relax the relaxations in force for this comparison
  /// @return true when the two are equal under those rules
  public static boolean equal(@Nullable Object got, @Nullable Object want, Relaxations relax) {
    return walk(got, want, relax, 0, new IdentityHashMap<>());
  }

  private static boolean walk(
      @Nullable Object a,
      @Nullable Object b,
      Relaxations relax,
      int depth,
      Map<Object, Object> seen) {

    if (absentAgainstEmpty(a, b, relax)) {
      return true;
    }
    if (a == null || b == null) {
      return a == b;
    }
    if (depth > MAX_DEPTH || a == b) {
      return true;
    }
    if (!a.getClass().equals(b.getClass())) {
      return false;
    }

    Double numeric = asDouble(a);
    if (numeric != null) {
      return equalNumbers(numeric, asDouble(b), relax);
    }
    if (a.getClass().isArray()) {
      return guard(a, seen, () -> equalArrays(a, b, relax, depth, seen));
    }
    if (a instanceof Map<?, ?> map) {
      return guard(a, seen, () -> equalMaps(map, (Map<?, ?>) b, relax, depth, seen));
    }
    if (a instanceof Set<?> set) {
      return guard(a, seen, () -> equalSets(set, (Set<?>) b, relax, depth, seen));
    }
    if (a instanceof Collection<?> items) {
      return guard(a, seen, () -> equalCollections(items, (Collection<?>) b, relax, depth, seen));
    }
    return a.equals(b);
  }

  /// Run body unless a is already being compared, which means a cycle.
  private static boolean guard(Object a, Map<Object, Object> seen, Supplier body) {
    if (seen.containsKey(a)) {
      return true;
    }
    seen.put(a, a);
    try {
      return body.get();
    } finally {
      seen.remove(a);
    }
  }

  /// A body that answers whether two values matched.
  @FunctionalInterface
  private interface Supplier {
    boolean get();
  }

  /// Answer a floating value for any number, or null when not one.
  private static @Nullable Double asDouble(Object value) {
    if (value instanceof Double d) {
      return d;
    }
    if (value instanceof Float f) {
      return (double) f;
    }
    return null;
  }

  /// Compare two floating values.
  ///
  /// Double.equals says NaN equals itself and that 0.0 differs from -0.0. Both are
  /// wrong here, so this uses the numeric comparison and names NaN explicitly.
  private static boolean equalNumbers(double a, @Nullable Double b, Relaxations relax) {
    if (b == null) {
      return false;
    }
    if (Double.isNaN(a) && Double.isNaN(b)) {
      return relax.equateNans();
    }
    return a == b;
  }

  /// Whether one side is absent and the other an empty collection.
  private static boolean absentAgainstEmpty(
      @Nullable Object a, @Nullable Object b, Relaxations relax) {
    if (!relax.equateEmpty()) {
      return false;
    }
    if (a == null) {
      return isEmptyCollection(b);
    }
    if (b == null) {
      return isEmptyCollection(a);
    }
    return false;
  }

  private static boolean isEmptyCollection(@Nullable Object value) {
    if (value instanceof Collection<?> items) {
      return items.isEmpty();
    }
    if (value instanceof Map<?, ?> map) {
      return map.isEmpty();
    }
    return value != null && value.getClass().isArray() && Array.getLength(value) == 0;
  }

  private static boolean equalArrays(
      Object a, Object b, Relaxations relax, int depth, Map<Object, Object> seen) {
    int length = Array.getLength(a);
    if (length != Array.getLength(b)) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      if (!walk(Array.get(a, i), Array.get(b, i), relax, depth + 1, seen)) {
        return false;
      }
    }
    return true;
  }

  private static boolean equalCollections(
      Collection<?> a, Collection<?> b, Relaxations relax, int depth, Map<Object, Object> seen) {
    if (a.size() != b.size()) {
      return false;
    }
    Iterator<?> theirs = b.iterator();
    for (Object mine : a) {
      if (!walk(mine, theirs.next(), relax, depth + 1, seen)) {
        return false;
      }
    }
    return true;
  }

  /// Compare two sets.
  ///
  /// A set is unordered, so each member needs a partner somewhere in the other set
  /// rather than a counterpart at the same position. Members compare structurally, which is
  /// why this cannot just ask `contains`.
  private static boolean equalSets(
      Set<?> a, Set<?> b, Relaxations relax, int depth, Map<Object, Object> seen) {
    if (a.size() != b.size()) {
      return false;
    }
    java.util.List<Object> spare = new java.util.ArrayList<>(b);
    for (Object mine : a) {
      int at = -1;
      for (int i = 0; i < spare.size(); i++) {
        if (walk(mine, spare.get(i), relax, depth + 1, seen)) {
          at = i;
          break;
        }
      }
      if (at < 0) {
        return false;
      }
      spare.remove(at);
    }
    return true;
  }

  /// Compare two maps: same size, same keys, equal values.
  ///
  /// Keys are matched the way the map itself looks them up. A key only structurally
  /// equal to another map's key is a different key, and pretending otherwise would disagree
  /// with every read the subject does.
  private static boolean equalMaps(
      Map<?, ?> a, Map<?, ?> b, Relaxations relax, int depth, Map<Object, Object> seen) {
    if (a.size() != b.size()) {
      return false;
    }
    for (Map.Entry<?, ?> entry : a.entrySet()) {
      if (!b.containsKey(entry.getKey())) {
        return false;
      }
      if (!walk(entry.getValue(), b.get(entry.getKey()), relax, depth + 1, seen)) {
        return false;
      }
    }
    return true;
  }
}
