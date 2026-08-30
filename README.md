# dokimi-assert

Test assertions for Java and Kotlin, defined by a language-neutral
standard and held to it on every run.

[![CI](https://github.com/dokimasia/assert-java/actions/workflows/ci.yml/badge.svg)](https://github.com/dokimasia/assert-java/actions/workflows/ci.yml)
[![Licence](https://img.shields.io/badge/licence-MIT-blue)](LICENSE)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://adoptium.net/)

```kotlin
dependencies {
    testImplementation("dev.dokimi:assert-core:0.1.0")
    testImplementation("dev.dokimi:assert-kotlin:0.1.0") // coroutines
}
```

Java 17 and up. No runtime dependencies beyond JSpecify's annotations.

- [Getting started](#getting-started)
- [Two surfaces](#two-surfaces)
- [The assertions](#the-assertions)
- [Equality](#equality)
- [Kotlin](#kotlin)
- [The standard](#the-standard)

## Getting started

```java
class StoreTest {
  @RegisterExtension final SeatExtension seat = new SeatExtension();

  @Test
  void get() {
    var item = store.get("widget");

    Check.isNotNull(seat.get(), item, "get answers the stored item");
    Check.equal(seat.get(), item.name(), "widget", "and the item is the one stored");
  }
}
```

Every assertion takes the seat first and a message last. The message
states the contract under test and is the first line of the failure:

```text
AssertionFailed: and the item is the one stored: want "widget", got "gadget"
```

### What a seat is

The seat is where a failure goes. Assertions never call a test
framework and never throw on their own; they report to whatever seat
they are handed. That is what lets one assertion serve a real test, a
benchmark, and a test that checks the assertion itself.

| Seat | `Check` does | `Soft` does |
|---|---|---|
| `Collector`, from `SeatExtension` | throws | collects, thrown when the test ends |
| `Standard` | throws | throws |
| `Recorder` | collects | collects |

`SeatExtension` is a field rather than a parameter, because a parameter
resolver hands out a value JUnit then forgets: nothing would be left
holding the collector when the body ends. It is the only class here
that mentions JUnit, and it is optional.

## Two surfaces

`Check` stops at the first failure. `Soft` records and carries on, so
one run shows every property that failed.

```java
Check.equal(seat.get(), reply.status(), 200, "the request succeeds");

Soft.hasPrefix(seat.get(), reply.body(), "{", "the body is JSON");
Soft.length(seat.get(), reply.items(), 3, "every item comes back");
```

If both `Soft` calls fail, both are reported together:

```text
AssertionFailed: 2 failures:
  1. the body is JSON: "[1,2]" does not start with "{"
  2. every item comes back: expected length 3, got 2
```

## The assertions

Thirty-four on both surfaces, plus three for golden files and four on
the benchmark contract.

<!-- api-reference:start -->

Every assertion takes the seat first and the message last.
`Check` and `Soft` carry the same names and the same signatures;
only what happens on a failure differs.

**Equality** — Structural, and strict about types.

```java
Check.equal(Seat seat, Object got, Object want, String msg, Option... options)
Check.notEqual(Seat seat, Object got, Object want, String msg, Option... options)
```

**Truth and absence** — Java has one null, so this is simpler than the JavaScript column.

```java
Check.isTrue(Seat seat, boolean condition, String msg)
Check.isFalse(Seat seat, boolean condition, String msg)
Check.isNull(Seat seat, Object got, String msg)
Check.isNotNull(Seat seat, Object got, String msg)
```

**Size** — A CharSequence, a Collection, a Map or an array.

```java
Check.length(Seat seat, Object got, int want, String msg)
Check.isEmpty(Seat seat, Object got, String msg)
Check.isNotEmpty(Seat seat, Object got, String msg)
```

**Containment** — What holding means follows the haystack.

```java
Check.contains(Seat seat, Object haystack, Object needle, String msg, Option... options)
Check.notContains(Seat seat, Object haystack, Object needle, String msg, Option... options)
Check.containsInOrder(Seat seat, Object got, String[] needles, String msg)
```

**Text** — CharSequence.

```java
Check.hasPrefix(Seat seat, Object got, String prefix, String msg)
Check.hasSuffix(Seat seat, Object got, String suffix, String msg)
Check.matches(Seat seat, Object got, String pattern, String msg)
```

**Numbers** — Where exact equality is the wrong question.

```java
Check.closeTo(Seat seat, Object got, double want, double tolerance, String msg)
Check.inRange(Seat seat, Object got, double low, double high, String msg)
```

**Ordering** — Sorted, unique, and anything else that holds between neighbours.

```java
Check.pairwise(Seat seat, List<T> items, BiPredicate<T, T> predicate, String msg)
```

**Errors** — For code that hands an exception back. Matching follows the cause chain.

```java
Check.noError(Seat seat, Throwable error, String msg)
Check.hasError(Seat seat, Throwable error, String msg)
Check.errorIs(Seat seat, Throwable error, Object target, String msg)
Check.errorIsNot(Seat seat, Throwable error, Object target, String msg)
Check.errorAs(Seat seat, Throwable error, Class<E> want, String msg) -> @Nullable E
```

**Throwing** — `throwsException`, because `throws` is a keyword.

```java
Check.throwsException(Seat seat, Raises.Body body, String msg) -> @Nullable Throwable
Check.doesNotThrow(Seat seat, Raises.Body body, String msg)
```

**Cancellation** — Interruption is Java's cancellation: what sleep, wait and take respond to.

```java
Check.honoursCancellation(Seat seat, Behaviour.Cancellable body, String msg)
Check.honoursDeadline(seat: Seat, Behaviour.Cancellable body, String msg)
Check.completesWithin(seat: Seat, Duration within, Raises.Body body, String msg)
Check.nullHandleSafe(Seat seat, Consumer<Object> body, String msg)
```

**Retrying** — For a condition something outside the test makes true. Both spend real time.

```java
Check.eventually(seat: Seat, Duration timeout, Duration interval, Consumer<Seat> body, String msg)
Check.eventuallyTrue(seat: Seat, Duration timeout, BooleanSupplier predicate, String msg)
```

**Concurrency** — Diffs the live non-daemon threads either side of the scope.

```java
Check.noTaskLeaks(seat: Seat, String msg) -> Runnable
```

**Purity** — What observe answers defines what nothing means.

```java
Check.isPure(Seat seat, Callable<Object> observe, Raises.Body body, String msg, Option... options)
```

**Testing an assertion** — On Check only: Soft cannot drive a check to failure.

```java
Check.rejects(Seat seat, String msg, Consumer<Recorder> body) -> String
```

**Golden files** — recorded output, compared and rewritable.

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

**Benchmark ceilings** — chained onto one contract.

```java
Contract.maxLatency(Duration ceiling) -> Contract
Contract.maxMean(Duration ceiling) -> Contract
Contract.maxBytes(long ceiling) -> Contract
Contract.loop(int iterations, Raises.Body body) -> Contract
Contract.check() -> void
```

**Coroutines** — from `dokimi-assert-kotlin`, for the seven a Java signature
cannot reach.

```kotlin
Check.honoursCancellation(seat: Seat, msg: String, body: suspend () -> Unit)
Check.honoursDeadline(seat: Seat, msg: String, body: suspend () -> Unit)
Check.completesWithin(seat: Seat, within: Duration, msg: String, body: suspend () -> Unit)
Check.eventually(seat: Seat, timeout: Duration, interval: Duration, msg: String, body: suspend (Seat) -> Unit)
Check.eventuallyTrue(seat: Seat, timeout: Duration, msg: String, predicate: suspend () -> Boolean)
Check.noTaskLeaks(seat: Seat, msg: String, body: suspend (CoroutineScope) -> Unit)
```

Each one carries a doc comment: what it states, what every argument means,
the edge cases it decides, and a worked call.

<!-- api-reference:end -->

## Equality

`Object.equals` answers a different question in three places, and this
corrects all three:

| Expression | `equals` | Here |
|---|---|---|
| `Double.valueOf(NaN).equals(NaN)` | `true` | not equal, per IEEE 754 |
| `Double.valueOf(0.0).equals(-0.0)` | `false` | equal |
| `new int[]{1}.equals(new int[]{1})` | `false` | equal |
| `Integer.valueOf(1).equals(1L)` | `false` | not equal, and for the right reason |

Comparison is structural and reaches arrays, collections and maps,
including an array nested inside a list that otherwise compares by
value. Different classes never compare, and a cycle stops the walk.

Pass `Option.EQUATE_NANS` or `Option.EQUATE_EMPTY` to relax either for
one call. An option applies to the call it is passed to and nothing
else.

## Kotlin

Kotlin calls the Java artifact for thirty-four of the forty-one. The
other seven are concurrency-shaped, and a Kotlin `suspend` lambda
compiles to a method taking a hidden `Continuation`, so no Java method
can accept one. `dokimi-assert-kotlin` supplies those:

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

Cancellation there is a coroutine's own: a `Job` cancelled and a
`CancellationException` raised at the next suspension point. In the
Java artifact it is `Thread.interrupt`, which is what `sleep`, `wait`,
`take` and every blocking call in `java.util.concurrent` respond to.

## The standard

The assertions are defined in
[assert-spec](https://github.com/dokimasia/assert-spec), language-neutral
and implemented in several languages. This library vendors the
definition and holds itself to it:

- 70 corpus cases state what each assertion must report, run against
  both surfaces. They are the same cases every other implementation
  runs.
- A completeness gate checks every assertion is present under the name
  the naming table gives it.
- An overlay records what this language cannot supply, and what a
  check cannot see.

### Where Java differs

`bench.Contract.maxAllocs` is declared rather than implemented. The JVM
reports bytes allocated per thread and no count of allocations; JFR
samples allocation events rather than counting them. `maxBytes` is
implemented, and holds a ceiling on the same behaviour by weight:
`ThreadMXBean.getThreadAllocatedBytes` counts what a thread allocated
rather than what survived a collection, so the reading does not move
with the collector.

`noTaskLeaks` sees platform threads and executor threads. A leaked
virtual thread is not reported, because virtual threads appear in no
standard enumeration on any JVM version. The overlay records that as a
limit rather than leaving it to be discovered.

`throwsException`, because `throws` is a keyword. It takes a `Body`
rather than a `Runnable`, so a caller need not wrap a method that
declares a checked exception.

## Development

```sh
./gradlew build     # compile, test, document, jar
./gradlew test
./gradlew javadoc
```

Building needs JDK 23 or newer, because the doc comments are Markdown
and older javadoc reads `///` as an ordinary comment. The artifact
targets Java 17 regardless, and CI runs the tests on a real 17.

## Licence

MIT. See [LICENSE](LICENSE).
