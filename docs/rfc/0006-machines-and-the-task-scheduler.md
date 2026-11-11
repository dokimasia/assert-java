---
rfc: 0006
title: Machines and the task scheduler of definition 16.4.0 in Java and Kotlin
author: Roy Klopper <roy.klopper@stealthscale.io>
status: Accepted
created: 2026-11-11
updated: 2026-11-11
discussion: none
supersedes: RFC-0002, in part
superseded-by: none
produces-adr: none
---

# RFC-0006: Machines and the task scheduler of definition 16.4.0 in Java and Kotlin

## Summary

`Stateful.steps` runs the steps of a machine in a case of a property, and
checks the history of the steps against the machine's spec. Its decisions
are choices of the case, so a failing sequence of steps replays and
shrinks. This proposal designs the machines and the task scheduler of the
JVM at definition 16.4.0. It takes over the section "Machines and the task
scheduler" of RFC-0002 and adds the rows of definitions 9.0.0 to 16.4.0:

| Change | Java API |
|---|---|
| The mean number of steps of a concurrent section | `Stateful.concurrentMean` |
| A task parks until a condition is true | `Scheduler.await` |
| A task sleeps on the clock of its scheduler | `Scheduler.clock` |
| The body moves the clock of the scheduler | `Scheduler.advance` |
| A run or an advance fails at a limit of releases | `Stateful.maxReleases` |
| An action that every case keeps | `Action.keep` |
| The steps of a stored case | store entries of format 2, through RFC-0004 |

The package depends on the property engine of RFC-0004 and on the histories
of RFC-0005.

## Motivation

The definition pins the machines with 29 machines vectors at 16.4.0, over
13 machine subjects. Five of the subjects spawn tasks of their own on a
scheduler of the case: a flusher that waits for a write, a ticker that
sleeps on the clock of its scheduler, and a task that spins until a flag is
set. A Java task runs on a thread of its own, so a task can block in a wait
or a sleep, and the scheduler releases another task meanwhile.

## Detailed design

### Components

| Component | Package | Responsibility |
|---|---|---|
| Machines | `dev.dokimi.assertion.stateful` | `Machine` and `Action`, as builders |
| Steps | `dev.dokimi.assertion.stateful` | `Stateful.steps`, its options and its six parts |
| The scheduler | `dev.dokimi.assertion.stateful` | `Scheduler`, its strategies, its clock and its limit |
| Traces | `dev.dokimi.assertion.stateful` | The step entries that a case of `Prop.draws` and a stored case follow |
| Kotlin | `dev.dokimi.assertion.kotlin` | The scheduler of coroutines |

### Machines and steps

```java
public final class Action<S> {
  public static <S> Action<S> of(String name, Action.Run run);
  public static <S, I> Action<S> withInput(String name, Action.Input<S, I> input, Action.RunWith<I> run);
  public Action<S> weight(int weight);
  public Action<S> drain();
  public Action<S> keep();
  public Action<S> enabled(Predicate<S> enabled);
  public String name();
}

public final class Machine<S> {
  public static <S> Machine<S> of(List<Action<S>> actions);
  public Machine<S> spec(Spec<S> spec);
  public Machine<S> invariant(BiConsumer<Case, S> invariant);
  public Machine<S> settle(BiConsumer<Case, S> settle);
}

public final class Stateful {
  public static <S> void steps(Case c, Machine<S> machine, Stateful.Option... options);
  public static Stateful.Option mean(int n);
  public static Stateful.Option max(int n);
  public static Stateful.Option swarm(boolean on);
  public static Stateful.Option clients(int n);
  public static Stateful.Option concurrent(int n);
  public static Stateful.Option concurrentMean(int n);
  public static Stateful.Option repeat(int n);
  public static Stateful.Option tasks(Scheduler scheduler);
}
```

- `steps` runs the six parts of the definition in order: the swarm, the
  setup, the sequential steps on client 0, a concurrent section for two
  clients or more, the drain and the settle.
- `Action.keep` keeps an action in every case without a choice of the
  swarm. Each kept action counts as kept before the first choice, so each
  choice of such a machine is one coin of 1 in 2, and no choice takes the
  bounds [1, 1].
- A section runs its clients on threads by default, and a case then runs
  up to `repeat` times, because threads do not replay. Under `tasks`, the
  clients run as tasks of the scheduler, whose releases are choices of the
  case.

### The task scheduler

```java
public final class Scheduler implements Executor {
  public Scheduler(Case c, Strategy strategy, Scheduler.Option... options);
  public void spawn(Runnable task);
  public void yield();
  public void await(BooleanSupplier condition);
  public Clock clock();
  public void run();
  public void advance(Duration duration);
  @Override public void execute(Runnable task);
}

public final class Stateful {
  public static Strategy uniform();
  public static Strategy pct(int depth);
  public static Scheduler.Option maxReleases(int n);
}
```

- **Threads.** Each task runs on a platform thread of its own, and one task
  runs at a time. A task passes control back to the scheduler at `yield`,
  at a parking `await` or `sleep`, and when it returns. The thread of the
  task then waits on a `Semaphore` of its own until its next release.
- **Parked tasks.** A task that awaits a false condition or sleeps for a
  positive duration parks. A parked task remains parked after `run` or
  `advance` returns, and becomes ready at a later call. The scheduler
  checks its parked tasks at the start of each call, after each turn and
  after each move of its clock. The sleeps join the ready tasks first, by
  deadline and then in the order of the sleeps, and the waits follow in
  their order. A task that yields joins before them.
- **Conditions.** The scheduler calls each condition on the thread of the
  call, between two turns. A condition that throws fails the case, as a
  task that throws does.
- **The clock.** `clock()` returns a `Clock` of the library. Its `now`
  starts at `2000-01-01T00:00:00Z`, and only `advance` moves it. Its
  `sleep` parks the running task until the clock has passed the duration.
  A sleep of zero returns at once.
- **Outside a task.** On the thread of the body, `await` returns at once
  when its condition is true. It fails the test when its condition is
  false. A sleep of a positive duration there fails the test too. Only the
  body can advance the clock.
- **Advance.** `advance` releases the ready tasks as `run` does. It then
  moves the clock to the earliest deadline of a parked sleep, or to its end
  time. It continues until the clock reads the end time and no task is
  ready.
- **The release limit.** The limit of `maxReleases` is 1,000,000 by
  default. A call that has released the limit while a task is still ready
  ends every task and fails the case with the message of the definition.
- **The end of a case.** The scheduler registers a cleanup of its case. The
  cleanup ends every task that has not returned. It releases each parked
  thread with an `Error` of the engine, and the error unwinds the task
  without a return from its wait or its sleep.
- **Executors.** `execute` spawns its task, so a subject that takes an
  `Executor` runs on the scheduler without a change.
- **The keyword.** A Java caller writes `scheduler.yield()` with its
  qualifier, because an unqualified `yield()` does not compile since Java
  14.

### Kotlin

`assert-kotlin` declares a `Scheduler` that extends `CoroutineDispatcher`
and implements `Delay`, as the test dispatchers of kotlinx-coroutines-test
do. A task is a coroutine on it.
`kotlinx.coroutines.yield()` ends the turn of a task. `delay` parks the
task on the clock of the scheduler. The naming table declines `await` for
Kotlin. A coroutine there parks on a `Channel` or a `Mutex`, at a point
that the scheduler sees.

### Invariants

- One task runs at a time, and only inside `run` or `advance`.
- A parked task is either sleeping or waiting, and it is not ready.
- The clock never moves backwards.
- One call releases at most the limit of its scheduler.
- Every thread of a task has ended when the case ends.

### Bounds

| Bound | Default |
|---|---|
| Mean sequential steps of a case | 30 |
| Most sequential steps, and most steps of the drain | 100 |
| Most steps of a concurrent section | 16 |
| Runs of a case whose section runs on threads | 4 |
| Releases of one call of the scheduler | 1,000,000 |
| Threads of a scheduler | one per task that has not returned |

### Failures

| Event | Result |
|---|---|
| A task throws | The call ends every task and throws the error on the thread of the call |
| A condition throws | The same as a task that throws |
| `await` outside a task with a false condition | Fails the test with the message of the definition |
| A sleep of a positive duration outside a task | Fails the test with the message of the definition |
| A task calls `run` or `advance` of its scheduler | Fails the test with the message of the definition |
| A call at the release limit while a task is ready | Ends every task and fails the case |
| `maxReleases(0)` | Throws an `IllegalArgumentException` |
| A step entry of a trace that the machine cannot take | The call ends with a fault at the entry |

## Alternatives considered

### A. Virtual threads for the tasks

**Why not:** the artifact targets Java 17, which has no virtual threads.
One task runs at a time, so a platform thread per task costs only its
stack.

### B. A continuation of each task

Each task would be a state machine that the scheduler resumes, as the
scheduler of Rust polls a future.

**Why not:** Java has no continuation in its public API. A subject would
have to be written as callbacks, and a subject that blocks in a library
call could not run as a task.

### C. A sleep on the controlled clock of the case

**Why not:** RFC-0056 of the definition rejects it. The cases of a run on
more than one worker share that clock, and a replay of a case would read
other instants.

## Drawbacks

- A thread per task costs a stack per task, which a subject of thousands
  of tasks feels.
- A subject that waits on a monitor or a lock of its own blocks its thread
  without passing control back, and the run then waits for the test
  timeout. The task should use `await` there.
- `Scheduler.await` is no `Object.wait`, and a reader of the code needs
  the naming table to tell them apart.
- kotlinx-coroutines marks `Delay` with `@InternalCoroutinesApi`, so a
  release of the coroutines library can change the interface that the
  Kotlin scheduler implements.

## References

| What | Where |
|---|---|
| Machines, simulation and campaigns | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0012-machines-simulation-and-campaigns.md> |
| The mean number of steps of a concurrent section | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0054-the-mean-number-of-steps-of-a-concurrent-section.md> |
| Tasks that wait and sleep under the task scheduler | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0056-tasks-that-wait-and-sleep.md> |
| An action that every case keeps | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0059-an-action-that-every-case-keeps.md> |
| The steps of a stored case | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0060-the-steps-of-a-stored-case.md> |
| The JVM design of definition 8.0.0 | [RFC-0002](0002-definition-8-in-java-and-kotlin.md) |
