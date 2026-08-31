package dev.dokimi.assertion.matcher;

import dev.dokimi.assertion.Clock;
import dev.dokimi.assertion.Clocked;
import dev.dokimi.assertion.Failure;
import dev.dokimi.assertion.Reporter;
import dev.dokimi.assertion.Seat;
import dev.dokimi.assertion.SystemClock;
import dev.dokimi.assertion.Where;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/// Sending one failure to a seat, under a mode.
@NullMarked
public final class Report {

  private Report() {}

  /// Send one failure to the seat.
  ///
  /// This decides nothing about whether anything failed. A matcher calls it only once
  /// its own comparison has failed, so every call produces exactly one reported failure.
  /// Under [Mode#FATAL] it may not return.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param message the failure text, already formatted
  public static void to(Seat seat, Mode mode, String message) {
    seat.helper();
    if (mode == Mode.SOFT) {
      seat.record(message);
      return;
    }
    seat.fail(message);
  }

  /// Send one record to the seat.
  ///
  /// A seat implementing [Reporter] receives the record; any other receives the
  /// sentence rendered from it. The call site is read here, so a matcher does
  /// not have to walk the stack.
  ///
  /// This decides nothing about whether anything failed. A matcher calls it
  /// only once its own comparison has failed. Under [Mode#FATAL] it may not
  /// return.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param assertion the canonical id the definition names
  /// @param contract the caller's message, unchanged
  /// @param detail the values this assertion declares
  public static void failure(
      Seat seat, Mode mode, String assertion, String contract,
      Map<String, @Nullable Object> detail) {
    seat.helper();
    Failure held = new Failure(assertion, contract, detail, callSite());

    if (seat instanceof Reporter taking) {
      taking.report(held, mode != Mode.SOFT);
      return;
    }
    to(seat, mode, held.render());
  }

  /// Send one record carrying no detail.
  ///
  /// @param seat where the failure is reported
  /// @param mode whether the failure stops the test or is recorded
  /// @param assertion the canonical id the definition names
  /// @param contract the caller's message, unchanged
  public static void failure(Seat seat, Mode mode, String assertion, String contract) {
    seat.helper();
    failure(seat, mode, assertion, contract, Map.of());
  }

  /// Build the detail map from name and value pairs.
  ///
  /// [Map#of] refuses a null value, and a failure often reports one: an
  /// assertion that found nothing where it wanted something reports that
  /// nothing.
  ///
  /// @param pairs alternating names and values
  /// @return the detail, in the order given
  /// @throws IllegalArgumentException if the count is odd
  public static Map<String, @Nullable Object> detail(@Nullable Object... pairs) {
    if (pairs.length % 2 != 0) {
      throw new IllegalArgumentException("detail takes name and value pairs");
    }
    Map<String, @Nullable Object> built = new LinkedHashMap<>();
    for (int at = 0; at < pairs.length; at += 2) {
      built.put(String.valueOf(pairs[at]), pairs[at + 1]);
    }
    return built;
  }

  /// The clock a seat carries, or the platform clock.
  ///
  /// @param seat where the failure is reported, which is also where a test
  ///     supplies time
  /// @return what the seat carries, or [SystemClock] when it carries none
  public static Clock clockOf(Seat seat) {
    if (seat instanceof Clocked carrying) {
      return carrying.clock();
    }
    return new SystemClock();
  }

  /// Where this library's own classes were loaded from.
  ///
  /// Held once: reading a protection domain per frame per failure is work the
  /// answer never changes for.
  private static final @Nullable Object LIBRARY = codeSourceOf(Report.class);

  /// Read the call site the assertion was written on.
  ///
  /// The frames below this one are this library's until the caller's own class.
  /// Which frames are the library's is decided by where the class was loaded
  /// from, not by its package: a caller may write tests in this package, and a
  /// package-name test would then skip the caller's own frame.
  ///
  /// @return where the assertion was called, or null when no frame qualifies
  private static @Nullable Where callSite() {
    return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .walk(
            frames ->
                frames
                    .filter(frame -> !Objects.equals(codeSourceOf(frame.getDeclaringClass()),
                        LIBRARY))
                    .findFirst()
                    .map(frame -> new Where(frame.getFileName() == null ? "" : frame.getFileName(),
                        frame.getLineNumber()))
                    .orElse(null));
  }

  /// Where a class was loaded from, or null when it cannot be told.
  ///
  /// @param owner the class to locate
  /// @return its code source location, or null
  private static @Nullable Object codeSourceOf(Class<?> owner) {
    java.security.ProtectionDomain domain = owner.getProtectionDomain();
    java.security.CodeSource source = domain.getCodeSource();
    return source == null ? null : source.getLocation();
  }
}
