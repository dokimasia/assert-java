# dokimi-assert

Test assertions for Java and Kotlin. A language-neutral standard defines
what each assertion checks and what its failure reports, and this
repository runs the standard's cases against the library.

[![CI](https://github.com/dokimasia/assert-java/actions/workflows/ci.yml/badge.svg)](https://github.com/dokimasia/assert-java/actions/workflows/ci.yml)
[![Licence](https://img.shields.io/badge/licence-MIT-blue)](LICENSE)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://adoptium.net/)

The artifacts are not published on Maven Central. Install them into your
local Maven repository from a clone of this repository:

```sh
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation("dev.dokimi:assert-core:0.1.0")
    testImplementation("dev.dokimi:assert-kotlin:0.1.0") // the assertions whose body suspends
}
```

`assert-core` needs Java 17 or later, and its only dependency is JSpecify's
annotations. `assert-kotlin` adds kotlinx-coroutines-core 1.11.0 and the
Kotlin standard library.

- [Getting started](#getting-started)
- [Two surfaces](#two-surfaces)
- [Chains](#chains)
- [The assertions](#the-assertions)
- [Equality](#equality)
- [Kotlin](#kotlin)
- [The standard](#the-standard)
- [Development](#development)

## Getting started

```java
import dev.dokimi.assertion.Check;
import dev.dokimi.assertion.junit.SeatExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TrimTest {
  @RegisterExtension final SeatExtension seat = new SeatExtension();

  @Test
  void trimRemovesThePadding() {
    var got = "  widget  ".strip();

    Check.isNotEmpty(seat, got, "the trimmed name is not empty");
    Check.equal(seat, got, "widget", "trim removes the padding");
  }
}
```

Each assertion takes the seat first and the message last. The message is
the contract under test, and the failure starts with it:

```text
dev.dokimi.assertion.AssertionFailed: trim removes the padding: want "widget", got "gadget"
```

The order differs in four places:

- `Check.rejects` takes the message before its body.
- An `Option`, such as `Option.EQUATE_NANS`, comes after the message.
- A golden comparison takes no message, and a benchmark contract takes it
  in its constructor.
- The functions of `assert-kotlin` take the message before the body, so
  the body can be a trailing lambda.

### Seats

An assertion reports each failure to the `Seat` that the test passes,
instead of calling a test framework or throwing. What a failure does
depends on the seat:

| Seat | A failure on `Check` | A failure on `Soft` |
|---|---|---|
| `Collector`, and the `SeatExtension` around one | Throws `AssertionFailed` | Is collected, and `flush` throws every collected failure |
| `Standard` | Throws | Throws |
| `Recorder` | Is kept | Is kept |

- `SeatExtension` is a JUnit extension and a `Seat`, so a test passes it
  to each assertion. It flushes its `Collector` after each test, so the
  failures of `Soft` fail the test that recorded them.
- Register the extension as a field. JUnit does not keep a resolved
  parameter after the test body ends, and the flush needs the collector
  then.
- `SeatExtension` is the only class of the library that refers to JUnit.
  On another framework, construct a `Collector` and call `flush` where the
  test ends.
- `Standard` is the seat of code outside a test framework. It throws on
  `Soft` too, because nothing flushes it at the end of a test.
- `Recorder` is the seat of a test of an assertion. `failed`, `message` and
  `failures` return what the assertion reported, and nothing that runs on
  it throws.

## Two surfaces

`Check` stops at the first failure. `Soft` records the failure and lets
the test continue, so one run reports every failure. `Soft` has every
assertion of `Check` except `rejects`.

```java
record Reply(int status, String body, List<Integer> items) {}

@Test
void theReplyListsEveryItem() {
  var reply = new Reply(200, "[1,2]", List.of(1, 2));

  Check.equal(seat, reply.status(), 200, "the request succeeds");
  Soft.hasPrefix(seat, reply.body(), "{", "the body is JSON");
  Soft.length(seat, reply.items(), 3, "every item comes back");
}
```

When both `Soft` calls fail, the extension throws once, after the test
body:

```text
dev.dokimi.assertion.AssertionFailed: 2 failures:
  1. the body is JSON: got "[1,2]", prefix "{"
  2. every item comes back: want 3, got 2
```

## Chains

`that` starts a chain about one value. Each method of the chain makes one
assertion about that value:

```java
Check.that(seat, reply.status())
    .notEqual(0, "the status was set")
    .equal(200, "the request succeeds");
```

The chain has the type of the value, so a `want` of another type fails to
compile:

```java
Check.that(seat, reply.body()).equal(200, "...");  // does not compile
Check.equal(seat, reply.body(), 200, "...");       // compiles, fails at run time
```

The static `equal` takes two `Object` parameters. Java infers a common
supertype for the two arguments of a generic method, so a stricter
declaration would still accept `equal(seat, "1", 1, ...)`.

A chain from `Check` stops at the first failing method. A chain from `Soft`
runs every method and reports each failure. The chain has 15 methods:
`equal`, `notEqual`, `isNull`, `isNotNull`, `length`, `isEmpty`,
`isNotEmpty`, `contains`, `notContains`, `containsInOrder`, `hasPrefix`,
`hasSuffix`, `matches`, `closeTo` and `inRange`.

## The assertions

`Check` has 34 assertions, and `Soft` has 33, because only `Check` has
`rejects`. Three more compare output with a golden file, and a benchmark
contract states three ceilings.

<!-- api-reference:start -->

Each signature in this section keeps the seat and the message. `Soft` has
the signature of each assertion of `Check` except `rejects`.

**Equality.** The comparison is structural and strict about types.

```java
Check.equal(Seat seat, Object got, Object want, String msg, Option... options)
Check.notEqual(Seat seat, Object got, Object want, String msg, Option... options)
```

**Truth and absence.** Java has one null value.

```java
Check.isTrue(Seat seat, boolean condition, String msg)
Check.isFalse(Seat seat, boolean condition, String msg)
Check.isNull(Seat seat, Object got, String msg)
Check.isNotNull(Seat seat, Object got, String msg)
```

**Size.** Each takes a `CharSequence`, a `Collection`, a `Map` or an
array. `null` has no length and fails all three.

```java
Check.length(Seat seat, Object got, int want, String msg)
Check.isEmpty(Seat seat, Object got, String msg)
Check.isNotEmpty(Seat seat, Object got, String msg)
```

**Containment.** Text contains a substring, and a `Map` contains a key. A
`Collection` or an array contains an element, by the rules of `equal`.

```java
Check.contains(Seat seat, Object haystack, Object needle, String msg, Option... options)
Check.notContains(Seat seat, Object haystack, Object needle, String msg, Option... options)
Check.containsInOrder(Seat seat, Object got, String[] needles, String msg)
```

**Text.** Each takes a `CharSequence`. `matches` searches with a Java
regular expression and does not anchor it, so a test that means the whole
value uses `^` and `$`.

```java
Check.hasPrefix(Seat seat, Object got, String prefix, String msg)
Check.hasSuffix(Seat seat, Object got, String suffix, String msg)
Check.matches(Seat seat, Object got, String pattern, String msg)
```

**Numbers.** The tolerance is an absolute difference, and both bounds of a
range pass. NaN is outside every tolerance and every range.

```java
Check.closeTo(Seat seat, Object got, double want, double tolerance, String msg)
Check.inRange(Seat seat, Object got, double low, double high, String msg)
```

**Errors.** These suit code that returns an exception instead of throwing
it. `errorIs`, `errorIsNot` and `errorAs` search the chain of causes, and
the target of `errorIs` is a throwable or a class.

```java
Check.noError(Seat seat, Throwable error, String msg)
Check.hasError(Seat seat, Throwable error, String msg)
Check.errorIs(Seat seat, Throwable error, Object target, String msg)
Check.errorIsNot(Seat seat, Throwable error, Object target, String msg)
Check.errorAs(Seat seat, Throwable error, Class<E> want, String msg) -> @Nullable E
```

**Throwing.** The assertion is `throwsException`, because `throws` is a
keyword.

```java
Check.throwsException(Seat seat, Raises.Body body, String msg) -> @Nullable Throwable
Check.doesNotThrow(Seat seat, Raises.Body body, String msg)
```

**Ordering.** `pairwise` covers sorted, unique and strictly increasing,
because each of those is a relation between neighbours.

```java
Check.pairwise(Seat seat, List<T> items, BiPredicate<T, T> predicate, String msg)
```

**Cancellation.** In Java, a subject is cancelled by an interrupt of its
thread. `sleep`, `wait` and `take` throw `InterruptedException` when the
interrupt arrives. `completesWithin` measures the body and does not
interrupt it, so a slow body runs to completion and then fails.

```java
Check.honoursCancellation(Seat seat, Behaviour.Cancellable body, String msg)
Check.honoursDeadline(Seat seat, Behaviour.Cancellable body, String msg)
Check.completesWithin(Seat seat, Duration within, Raises.Body body, String msg)
```

**Purity and a missing handle.** `isPure` fails when the body changes what
`observe` returns. `nullHandleSafe` passes the body a `null` handle and
fails when the body dereferences it.

```java
Check.isPure(Seat seat, Callable<Object> observe, Raises.Body body, String msg, Option... options)
Check.nullHandleSafe(Seat seat, Behaviour.Handled body, String msg)
```

**Retrying.** These suit a condition that something outside the test
makes true. Both read the seat's clock. On the platform clock they spend real time. On
a `Controlled` clock, which `Collector.withClock` and `Recorder.withClock`
set, they advance the clock between attempts and do not spend real time.

```java
Check.eventually(Seat seat, Duration timeout, Duration interval, Consumer<Seat> body, String msg)
Check.eventuallyTrue(Seat seat, Duration timeout, BooleanSupplier predicate, String msg)
```

**Concurrency.** `noTaskLeaks` returns a `Runnable` that the test runs
where the scope ends. It compares the live non-daemon threads before and
after the scope, so it does not see a daemon thread or a virtual thread.

```java
Check.noTaskLeaks(Seat seat, String msg) -> Runnable
```

**Testing an assertion.** `rejects` runs the body on a `Recorder` and
returns the text of the body's failure. It fails when the body does not
fail. Only `Check` has it, because `Soft` does not stop.

```java
Check.rejects(Seat seat, String msg, Consumer<Recorder> body) -> String
```

**Golden files.** Each comparison reads a recorded file and takes no
message. `match` resolves a name against `src/test/resources/testdata`.
`shouldUpdate` returns `true` when `DOKIMI_ASSERT_UPDATE_GOLDEN` is set to
anything other than the empty string and `0`, and an update rewrites the
file instead of failing.

```java
Golden.shouldUpdate() -> boolean
Golden.scrubTimestamps() -> Scrubber
Golden.scrubHashes() -> Scrubber
Golden.scrubRunIds() -> Scrubber
Golden.scrubJsonFields(String... fields) -> Scrubber
Golden.matchAt(Seat seat, Path path, String got, boolean update, Scrubber... scrubbers)
Golden.match(Seat seat, String name, String got, boolean update, Scrubber... scrubbers)
Golden.matchJsonField(Seat seat, Path path, String field, String got, boolean update, Scrubber... scrubbers)
```

**Benchmark ceilings.** The ceilings chain onto one contract, and `check`
reports each ceiling that the run crossed. `measuring` builds the input of
each iteration with `setup`, outside the measurement.

```java
new Contract(Seat seat, String msg)
Contract.maxLatency(Duration ceiling) -> Contract
Contract.maxMean(Duration ceiling) -> Contract
Contract.maxBytes(long ceiling) -> Contract
Contract.loop(int iterations, Raises.Body body) -> Contract
Contract.measuring(int iterations, Supplier<T> setup, Consumer<T> body) -> Contract
Contract.check() -> void
```

**Coroutines.** `dev.dokimi:assert-kotlin` has the six assertions whose
body suspends. Each is a `suspend` function of the object
`dev.dokimi.assertion.kotlin.Check`.

```kotlin
suspend fun honoursCancellation(seat: Seat, msg: String, body: suspend () -> Unit)
suspend fun honoursDeadline(seat: Seat, msg: String, body: suspend () -> Unit)
suspend fun completesWithin(seat: Seat, within: Duration, msg: String, body: suspend () -> Unit)
suspend fun eventually(seat: Seat, timeout: Duration, interval: Duration, msg: String, body: suspend (Seat) -> Unit)
suspend fun eventuallyTrue(seat: Seat, timeout: Duration, msg: String, predicate: suspend () -> Boolean)
suspend fun noTaskLeaks(seat: Seat, msg: String, body: suspend (CoroutineScope) -> Unit)
```

Read the doc comment of an assertion for its edge cases and the meaning of
each argument.

<!-- api-reference:end -->

## Equality

`equal` differs from `Object.equals` in three cases:

| Expression | `Object.equals` | `equal` |
|---|---|---|
| `Double.valueOf(NaN).equals(NaN)` | `true` | Not equal, as IEEE 754 defines NaN |
| `Double.valueOf(0.0).equals(-0.0)` | `false` | Equal |
| `new int[]{1}.equals(new int[]{1})` | `false` | Equal |

The comparison is structural. It descends into arrays, collections and
maps, also into an array inside a list. Values of different classes never
compare, so `equal(seat, 1, 1L, msg)` fails because an `Integer` is not a
`Long`. The comparison stops at a cycle.

`Option.EQUATE_NANS` makes NaN equal to NaN, and `Option.EQUATE_EMPTY`
makes an absent collection equal to an empty one. An option applies to the
call that it is passed to, and to no other call.

## Kotlin

Kotlin calls `assert-core` for 34 of the 42 assertions. A Kotlin `suspend`
lambda compiles to a method with a hidden `Continuation` parameter, so no
Java method can accept one. `dev.dokimi:assert-kotlin` has the six
assertions whose body suspends:

```kotlin
class WorkerTest {
    @Test
    fun `it stops when told`() = runTest {
        Check.honoursCancellation(seat, "the worker stops when told") {
            worker.serve()
        }
    }
}
```

Cancellation in `assert-kotlin` is a coroutine's own. The assertion
cancels a `Job`, and the subject receives a `CancellationException` at its
next suspension point. In `assert-core`, cancellation is
`Thread.interrupt`. `sleep`, `wait`, `take` and the blocking calls of
`java.util.concurrent` throw `InterruptedException` when it arrives.

Neither artifact has `maxAllocs` or `Contract.maxAllocs`, for the reason
that [Where Java differs](#where-java-differs) gives. They are the other
two of the 42.

## The standard

[assert-spec](https://github.com/dokimasia/assert-spec) defines the
assertions. This library vendors definition 1.1.0 in
`assert-core/src/test/resources/dev/dokimi/assertion/conformance/spec/`,
and its tests check the library against it:

- `CorpusTest` runs the 87 corpus cases of 1.1.0 on `Check` and `Soft`. The
  cases cover 25 of the 42 assertions.
- `DefinitionTest` checks that each assertion is implemented or declared
  absent in the overlay, under the name that the naming table gives it.
- The overlay, `overlay.json`, contains two blocked assertions, one limit
  and one replaced name of the definition's surface.

Later versions of the definition add assertions, properties and history
checks. [RFC-0002](docs/rfc/0002-definition-8-in-java-and-kotlin.md)
proposes definition 8.0 for Java and Kotlin.

### Where Java differs

- `Check.maxAllocs` and `Contract.maxAllocs` do not exist, and both are
  blocked in the overlay. The JVM reports the bytes that each thread
  allocates and no count of allocations. JFR samples allocation events
  instead of counting them.
- `Contract.maxBytes` sets a ceiling on the same behaviour by weight. It
  reads `ThreadMXBean.getThreadAllocatedBytes`. That count includes the
  memory that a collection has freed since, so the reading does not change
  with the collector.
- `noTaskLeaks` compares the live non-daemon threads. No standard
  enumeration on any JVM version contains virtual threads, so a leaked
  virtual thread is not reported. The overlay contains that limit.
- `throwsException` takes a `Raises.Body` instead of a `Runnable`, so a
  caller does not wrap a method that declares a checked exception.
- `Contract` has no `excluding`. `Contract.measuring` takes the setup as a
  closure of its own. The harness runs that closure outside the
  measurement.

## Development

```sh
./gradlew build                 # compile, test, document and jar both artifacts
./gradlew test
./gradlew javadoc
./gradlew publishToMavenLocal   # install both artifacts into the local Maven repository
./gradlew centralBundle         # both artifacts as one Maven Central bundle
```

Building needs JDK 23 or later, because the doc comments are Markdown and
an older javadoc reads `///` as an ordinary comment. The artifacts target
Java 17. CI runs the tests on Java 17 too.

Pushing a `v*` tag builds a signed bundle and uploads it to the Central
Portal, which validates it and waits for a person to release it. The build
signs only when `SIGNING_KEY` and `SIGNING_PASSWORD` are set, so an
ordinary build does not need a key.

The design of the library is in
[RFC-0001](docs/rfc/0001-the-java-implementation.md). Every RFC and its
status are in [docs/rfc](docs/rfc/README.md).

## Licence

MIT. See [LICENSE](LICENSE).
