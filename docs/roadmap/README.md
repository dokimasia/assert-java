# Roadmap

What we are building, in what order, and what has to be true first.
Milestone numbers are permanent. The order is the order of this table, and
it changes.

| Order | Milestone | Status | Depends on |
|---|---|---|---|
| 1 | [0001 The foundation](#0001-the-foundation) | In progress | none |
| 2 | [0002 Histories](#0002-histories) | Planned | 0001 |
| 3 | [0003 The property engine](#0003-the-property-engine) | Planned | 0001, 0002 |
| 4 | [0004 Machines and the task scheduler](#0004-machines-and-the-task-scheduler) | Planned | 0002, 0003 |
| 5 | [0005 Trees of files](#0005-trees-of-files) | Planned | 0001 |
| 6 | [0006 Kotlin](#0006-kotlin) | Planned | 0003, 0004 |
| 7 | [0007 Definition 16.5.0](#0007-definition-1650) | Planned | 0001 to 0006 |

The milestones build each package once, at its final path, bottom-up by
the imports of the packages. `Case.history` returns a `History`, so the
histories come before the property engine. The machines need both. The
trees of files import the foundation alone.

The completeness gate checks every Java name of the vendored copy, so it
fails until milestone 0007 adds the last row. Each milestone instead passes
the driver of its vector family, the coverage of JaCoCo and the build on
JDK 17 and JDK 25.

## 0001 The foundation

- **Design:** RFC-0003.
- **Scope:** the library vendors 16.5.0 with `spec sync --commit` of the
  commit of 16.5.0, and every call record states it.
- **Packages:** `json`, `literal`, `record`, `matcher`, `pattern`, the seats
  and the JUnit extension.
- **Done when:** every corpus case of 16.5.0 outside the four subsystems
  passes on both surfaces, with its call record. JaCoCo reports 100% of the
  lines and branches of `assert-core`.

## 0002 Histories

- **Design:** RFC-0005.
- **Packages:** `history` and `history.check`.
- **Done when:** the 112 history vectors of 16.5.0 pass. The specs of the
  package cover the faults of a spec and of a history that no vector
  states.

## 0003 The property engine

- **Design:** RFC-0004.
- **Packages:** `prop`, `prop.engine`, `prop.engine.generator` and
  `prop.shape`.
- **Done when:** the 452 property vectors of 16.5.0 pass, on one worker and
  on four. The store of a failing property replays in a second run of the
  test.

## 0004 Machines and the task scheduler

- **Design:** RFC-0006.
- **Packages:** `stateful`.
- **Done when:** the 36 machines vectors of 16.5.0 pass on the scheduler of
  Java. The specs cover each failure of the scheduler that no vector
  states, such as a wait outside a task, and every thread of a task has
  ended when its case ends.

## 0005 Trees of files

- **Design:** RFC-0007.
- **Packages:** `files`, and `Golden.matchTree`.
- **Done when:** the 57 file vectors of 16.5.0 pass on Linux and macOS. On
  Windows the vectors that state a mode skip, as the definition states.

## 0006 Kotlin

- **Design:** RFC-0004 and RFC-0006 for the type reader and the scheduler
  of coroutines. RFC-0001 for the assertions with a suspending body.
- **Packages:** `dev.dokimi.assertion.kotlin` of `assert-kotlin`.
- **Done when:** the Kotlin column of the naming table has an
  implementation or a declination for every row. The machines vectors pass
  on the scheduler of coroutines.

## 0007 Definition 16.5.0

- **Scope:** the last rows of the naming table of 16.5.0, which milestone
  0001 vendors.
- **Done when:** the completeness gate and the corpus test pass, and CI
  passes on Linux, macOS and Windows.
