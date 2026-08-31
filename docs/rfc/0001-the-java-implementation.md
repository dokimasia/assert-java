---
rfc: 0001
title: The Java assertion library
author: Roy Klopper <roy.klopper@stealthscale.io>
status: Accepted
created: 2026-08-30
updated: 2026-08-30
discussion: none
supersedes: none
superseded-by: none
produces-adr: none
---

# RFC-0001: The Java assertion library

## Summary

`dev.dokimi:assert-core` implements the standardized assertion set for
the JVM, and `dev.dokimi:assert-kotlin` adds the six of them a Java
signature cannot express. Both are built from one repository, read the
same definition, run the same corpus against both surfaces, and declare
the one assertion the platform cannot supply and the one it supplies
only partly. This records what Java does differently from the other
implementations, and why each difference is forced rather than chosen.

## Motivation

The standard exists so the same test means the same thing in every
language. That only holds if each implementation is held to it, and if
the places it cannot comply are written down rather than quietly
skipped.

Java disagrees with the standard in three places at once. `Object.equals`
answers a different question from the one the standard asks, and it does
so for NaN, for signed zero and for arrays. The language reserves the
word the standard uses for the assertion about throwing. And Java's
cancellation is not a value passed down a call chain, so a signature
cannot carry it.

Kotlin adds a fourth: a suspending function compiles to a method taking
a hidden continuation, so a Java signature cannot accept one at all.

## Detailed design

### Two artifacts, one repository

```
dev.dokimi:assert-core     Check, Soft, seats, options, golden, bench
dev.dokimi:assert-kotlin   the six assertions that take a suspending body
```

Thirty-five of the forty-one assertions take a value, a class, a
duration or a plain body. A Kotlin caller uses the Java artifact for
every one of those, and reads them as ordinary Kotlin: `Check.equal`,
`Check.hasPrefix`, `Check.throwsException`.

The six that remain take work that suspends: the two cancellation
assertions, the timeout, the two retries and the leak check. A
suspending lambda compiles to a method with an extra `Continuation`
parameter, so there is no Java interface it can be passed as. Those get
Kotlin declarations of their own, in a second artifact so a Java-only
project does not pull the coroutines runtime in.

One repository rather than two, because the naming table, the corpus and
the overlay are shared: Java and Kotlin name every assertion identically,
and splitting the repository would mean keeping two copies of that in
step by hand.

`assert-core` has no runtime dependencies. JSpecify is annotations only,
so nothing is loaded at run time.

### The seat, and three of them

```java
public interface Seat {
  default void helper() {}
  void fail(String message);
  void record(String message);
}
```

An interface rather than a base class, so anything with the three
methods is a seat. `helper` has a default body because a framework that
cannot hide library frames from a stack trace should not have to say so.

Which seat a test holds decides what each surface does:

| Seat | `Check` | `Soft` |
|---|---|---|
| `Collector` | throws | collects, thrown when the test ends |
| `Standard` | throws | throws |
| `Recorder` | collects | collects |

`Standard.record` throws rather than dropping the failure: a recorded
failure needs somewhere to report at the end, and a bare seat has no end
to report at.

`Collector` is what a real test wants, and it is why the JUnit extension
exists. Something has to throw what was collected once the body is done,
and only the runner knows when that is.

`SeatExtension` implements `Seat` itself, so a call site passes the field
rather than reaching through it:

```java
class StoreTest {
  @RegisterExtension final SeatExtension seat = new SeatExtension();

  @Test
  void get() {
    Check.equal(seat, store.get("widget").name(), "widget", "get answers the stored item");
  }
}
```

It is a field rather than a parameter, because a parameter resolver hands
out a value JUnit then forgets: nothing would be left holding the
collector when the body ends. JUnit is a `compileOnly` dependency, so the
published artifact does not carry it, and this is the only class that
mentions it.

### Equality is written out, not inherited

`Object.equals` gets three things wrong for this purpose, and all three
are cases the corpus states:

- It says NaN equals itself. IEEE 754 says it does not, and every other
  implementation of this standard agrees with IEEE 754.
- It says `0.0` and `-0.0` differ. Every other implementation treats them
  as the same number.
- It compares arrays by identity, so two arrays holding the same elements
  are unequal. That is true inside a list as much as at the top level,
  which is where it does the most damage.

So the comparison is written out. It walks arrays, collections and maps
structurally, refuses to compare values of different classes, and stops
on a cycle rather than overflowing the stack. `Integer` and `Long`
holding the same number are different values, which is what the
standard's rule about types means on a platform that has both.

Two relaxations are available per call: one that equates NaN with itself,
and one that equates an absent collection with an empty one.

### Cancellation is interruption

Go states cancellation with a `context.Context` in every signature.
Java has no such value. What it has is interruption: `Thread.interrupt`
is what `sleep`, `wait`, `take` and every blocking call in
`java.util.concurrent` respond to, so a subject that can be cancelled at
all responds to it.

`honoursCancellation` starts the subject on its own thread, interrupts it
at once, and requires it to stop within a second. The subject is handed a
supplier that answers whether it has been asked to stop, so a subject
doing computation rather than blocking can poll. One that ignores both
runs to completion and fails here.

`honoursDeadline` is the same mechanism with a different message. Java
carries no deadline in its signatures the way Go carries one in a
context, so a deadline of nothing is expressed the only way the platform
expresses it.

`nullHandleSafe` passes null in place of that supplier. Throwing an
exception of its own passes, because that is the subject declining;
dereferencing the missing handle is what fails, and that is what a caller
hits by accident.

### throwsException, because throws is a keyword

The standard states `throws` for a callable that raises. Java reserves
that word, so the naming table records `throwsException` as Java's name
for it. `nil` and `not-nil` become `isNull` and `isNotNull` for the same
kind of reason: Java has one absent value and calls it null.

The Java and Kotlin columns of the naming table are identical. Kotlin
calls the Java method under the Java name, and inventing a second name
for the same behaviour would make a test that reads the same in both
languages impossible.

### Doc comments are Markdown, so the build refuses old JDKs

Every doc comment in this library uses `///`, which javadoc renders as
Markdown from JDK 23 onwards. An older javadoc reads those lines as
ordinary comments and silently produces empty documentation, which is
worse than failing.

So the build checks the JDK it is running on and refuses below 23. The
published artifact is unaffected: `options.release` holds the compile to
the Java 17 API, and CI runs the tests on a real 17 to prove the artifact
works there rather than only compiles for it.

doclint runs with `Xdoclint:all` and `-Werror`, with the `missing` group
left on. A public member with no comment, a parameter with no `@param`
and a dead reference each fail the build.

### Java 17, and why not higher

The only thing a newer floor would have bought is seeing virtual threads
in `noTaskLeaks`, and it does not buy that. A live virtual thread appears
in none of `Thread.getAllStackTraces`, `ThreadGroup.enumerate` or
`ThreadMXBean.getAllThreadIds`, measured on JDK 26. `ThreadMXBean` counts
the carrier threads it runs on instead, which is noise rather than
signal.

So `noTaskLeaks` reads the live non-daemon threads either side of a
scope, which catches a leaked platform thread and a leaked executor
thread. It does not catch a leaked virtual thread, and the overlay
records that as a limit rather than leaving someone to discover it.

### Conformance lives in the test source set

Reading the definition, the naming table, the overlay and the corpus
means parsing JSON, and Java ships no JSON parser. Putting a parser in
the published artifact to satisfy a check that only runs during this
library's own build would make every consumer carry it.

So the vendored copy of the standard and the code that reads it are test
sources. Jackson is a test dependency. What ships has no dependencies at
all.

### What the corpus reaches

Eighty-seven cases across twenty-five assertions, run against both
surfaces. Seventeen of those cases name a behaviour rather than stating
a value, which is how a case reaches an assertion that takes a callable.

The other sixteen want a class, a real duration or a real thread, and no
corpus file can hold one, so they are covered by tests here and by the
completeness gate that checks every name in the table exists.

Those tests drive each assertion twice: once with a subject that
satisfies it and once with one that does not. A one-sided test is
satisfied by an assertion that reports nothing whatever it is handed,
which is what three implementations of `honoursCancellation` turned out
to be.

### One assertion is declared, one is limited

`Contract.maxAllocs` states a ceiling on the number of allocations per
iteration. The JVM reports bytes allocated per thread through
`com.sun.management.ThreadMXBean` and no count of allocations. JFR
samples allocation events rather than counting them, so a per-iteration
total from it moves with the sampling rate. The overlay declares the
assertion absent, with that as the reason.

`maxBytes` is implemented, and holds a ceiling on the same behaviour by
weight rather than by count. The reading is exact: measuring a body that
allocates one ten-element long array gave 96.0 bytes per iteration on six
consecutive runs, which is the object header plus the array. This is
where Java and TypeScript diverge in opposite directions, since V8 can
only answer the same question as a heap-usage delta that moves with
whether the collector ran.

`noTaskLeaks` is the standard's first entry under `limits`: implemented,
and partial in a way that is written down. That is a different claim from
absence, and the overlay format distinguishes them so a reader can tell
which one they are looking at.

## Alternatives considered

### A. A separate repository for Kotlin

Rejected because thirty-five of the forty-one assertions would be
identical in both, and the naming table, corpus and overlay would have to
be kept in step by hand. Two artifacts from one repository gives a
Java-only project an artifact with no coroutines runtime in it, which was
the only thing separate repositories were for.

### B. A Java 21 floor, or a multi-release JAR

Rejected on the measurement above. Both were proposed to make
`noTaskLeaks` see virtual threads, and neither does, because no standard
enumeration contains them on any version. A higher floor with nothing
behind it only narrows who can use the library.

### C. Implement the allocation ceiling from JFR

Rejected because JFR samples allocations rather than counting them. A
ceiling checked against a sampled count passes or fails depending on the
sampling rate, which is an assertion that flakes. An assertion that is
absent and recorded is better than one that teaches people to rerun the
suite.

### D. HTML doc comments, so any JDK can build

Rejected because the comments are read far more often in an editor and on
a diff than in rendered javadoc, and HTML in a comment is noise in both.
Markdown costs a JDK 23 build tool, which contributors already have,
and the artifact still targets 17.

### E. Assert on `Object.equals` and document the differences

Rejected because the differences are exactly the cases the corpus tests.
An implementation that inherits `equals` fails the shared corpus on NaN,
on signed zero and on arrays, so documenting them would be documenting a
failure to conform.

## Drawbacks

Building needs JDK 23 or newer, though the artifact runs on 17. A
contributor on an older JDK gets a build that refuses to start, with a
message saying why.

Structural equality is 223 lines that `Object.equals` would have given
for free, and it has to be tested on its own rather than through the
assertions, because it is what they all report through.

Two artifacts mean a Kotlin user adds two dependencies, and has to know
that six assertions come from the second one.

One assertion of the forty-one is absent, and one is partial. A team
relying on allocation counts in Go cannot port those tests, and a team
leaking virtual threads is not told.

## Unresolved and future work

An adapter for a runner other than JUnit is not proposed here. What
JUnit gives and a bare runner does not is a hook that runs after the test
body, and any runner with one can have an adapter of a few dozen lines.

Should `getThreadAllocatedBytes` gain a companion that counts
allocations, `maxAllocs` becomes implementable and the divergence can be
withdrawn.

## References

- The standard, its corpus and the overlay format:
  <https://github.com/dokimasia/assert-spec>
- Markdown documentation comments, JEP 467:
  <https://openjdk.org/jeps/467>
- `ThreadMXBean.getThreadAllocatedBytes`:
  <https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/ThreadMXBean.html>
- JSpecify nullness annotations:
  <https://jspecify.dev/docs/start-here/>
