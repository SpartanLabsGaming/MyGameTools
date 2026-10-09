# Final implementation: #77, "World Systems Stage 2: wrap ZoneIndex as a WorldSystem"

## What was built

[#77](https://github.com/SpartanLabsGaming/MyGameTools/issues/77) was implemented by
[PR #134](https://github.com/SpartanLabsGaming/MyGameTools/pull/134), *"feat(world): bind
WorldSystems to their World and make ZoneIndex the zone system"*, from branch
`feature/77-zone-world-system`. It merged into `master` on 2026-10-09 (UTC) as merge commit
`0b99f9e`, carrying all three units of [`architecture.md`](architecture.md):
`world-system-binding`, `zone-world-system` and `tiled-map-result-lookups`.

The sections below are the as-built records the architecture and the three unit plans carried,
moved here verbatim and grouped by source document and section. Each source keeps a one-line
pointer where a record was removed.

## From `architecture.md`

### §11.1 Owned by the unit plans (the implementer applies them, in the unit's own commits)

*(As built 2026-10-08: the QA pass added `MissingWorldSystemException` to the core row, and the
installed-systems bullet now states the binding check — unit 1 §4.6.)*

## From `plan-world-system-binding.md`

### §4.1 Changed: `MAIN/WorldSystem.kt`

> **As built (2026-10-08):** the QA pass corrected the binding wording in the KDoc of `WorldSystem`
> and `WorldSystem.world`. A system is bound by the first `World.installSystem` call whose checks
> pass, before `onInstalled()` runs, and keeps that binding even if the install is rolled back; the
> KDoc had said "first successful install".

### §4.2 New: `MAIN/AbstractWorldSystem.kt`

> **As built (2026-10-08):** the same binding-wording correction landed in the KDoc of
> `AbstractWorldSystem`, `AbstractWorldSystem.world` and `boundWorldOrNull`. And by the user's
> decision C39 (architecture §1.2), `world`'s runtime message now matches it:
> `"<class>.world was read before any World.installSystem call for this system passed its checks"`,
> where `<class>` is the simple name, or the Java name for an anonymous class, as above.

### §4.3 Changed: `MAIN/World.kt`

> **As built (2026-10-08):** KDoc changes from the QA pass in `World`:
> - `installSystem` documents `@throws MissingWorldSystemException` (the required-peer pattern's
>   failure) and states that an exception from `onInstalled()` is rethrown unchanged after the
>   roll-back. `uninstallSystem` and `stepSystems` state the same for their hooks in a sentence,
>   because a KDoc `@throws` needs a concrete type.
> - The Roll-back paragraph documents the single `WARN` line the roll-back logs.
> - The reified `systemOf` gained `@param T`, and both forms say a miss costs two small allocations
>   (the exception and its `Result` box).
> - The KDoc of the private helpers `requireBindableHere` and `rollBack` cites code by name.
> - The constructor's `@param seed` links `[rng]`.

### §4.6 Changed: `README.md` and `CHANGELOG.md`

> **As built (2026-10-08):** the QA pass changed two README texts. The installed-systems bullet
> now also states the binding check, and the core row lists `MissingWorldSystemException`
> (Experimental) after all.

### §4.8 New: `MAIN/MissingWorldSystemException.kt` (Stage 2; architecture C21, §4.4, §4.5)

> **As built (2026-10-08):** the KDoc says a miss costs two small allocations, the exception and
> the `Result` failure box that carries it, rather than one.

### §6.2 Level 2 — component (`TEST/component/gameobjects/`)

> **As built (2026-10-05):** two test-fake details the table does not spell out, both needed to
> observe what it asks for. `WorldInstallSystemTest`'s `RecordingSystem` also overrides
> `onUninstalled` and counts it (`uninstallCount`): the flipped "…genuinely uninstalls" test reads
> it to show that `onUninstalled` ran before `onInstalled` returned. And each direct implementor in
> the migrated tests (in `WorldInstallSystemTest` and `WorldSystemDefaultsTest`) captures the
> installing `World` in a `host` local and returns that from its `world` getter: inside an
> anonymous `object : WorldSystem`, `get() = world` resolves to the object's own `world` property,
> so the getter would call itself.

> **As built (2026-10-05):** in `WorldInstallSystemBindingTest`, the step-1 case (§2.2's
> flowchart) cannot assert "stays unbound": only a system that is already installed, and so
> already bound, is rejected at step 1 ("already installed"). Its test,
> `a system rejected as already installed keeps its existing binding unchanged`, asserts that
> `boundWorldOrNull()` is still the `World` it was installed on. The step-3 and step-4 cases
> (`a system rejected by the slot check stays unbound`, `a system rejected by the role check stays
> unbound`) assert unbound, as the row says.

> **As built (2026-10-08):** test changes from the QA pass.
> - `WorldInstallSystemBindingTest` gained `the identity check runs before the binding check - an
>   installed direct implementor is rejected as already installed without reading world`.
> - The `val host = world` aliases carry a comment explaining why they exist, and so do the
>   assertions that guard against `KClass.toString()`'s fallback text when `kotlin-reflect` is
>   absent (in `MissingWorldSystemExceptionTest`, `WorldInstallSystemUniquenessTest` and
>   `WorldSystemOfTest`).
> - `WorldSystemOfTest`'s `find` helper KDoc names the two tests that assert the failure itself,
>   instead of citing them by number.

### §6.2.1 `WorldSystemOfTest` — the `KClass` form, one adapter helper

> **As built (2026-10-05):** `WorldSystemOfTest` has 14 tests, not 12. Item 7 names three tests,
> and all three were written (`systemOf no longer finds a system after its install was rolled
> back`, `systemOf no longer finds a system after it is uninstalled`, `systemOf finds a system again
> after a re-install`), so items 8–12 are the file's tests 10–14. The required-peer pattern sits
> inside item 9's test, `systemOf is callable from onInstalled, step and onUninstalled`, as item 9
> places it.

### §6.6 Level 4c — non-functional (`TEST/nonfunctional/gameobjects/`)

> **As built (2026-10-05):** the throwing-install loop throws the test's own private
> `InstallFailure`, not `StepFailure`, whose KDoc describes a failing `step()`.

> **As built (2026-10-08):** that loop raises the `com.spartanlabs.gaming.gameobjects` logger to
> `ERROR` while it runs, and restores the previous level afterwards, so 10 000 roll-back `WARN`
> lines do not flood the test output.

### §8 Version control

> **As built (2026-10-05):** Step 0 ran as planned, but all three units were then implemented in
> one working tree before any commit, so commits 1–3 above are cut from that tree by path and, where
> a file is shared, by hunk. One line is shared with unit 2: README's Features installed-systems
> bullet carries this unit's rewrite (commit 3) and unit 2's `ZoneIndex` clause (unit 2's commit 5)
> on a single line, so the manager hand-splits it and stages the bullet without the clause for
> commit 3. This unit's CHANGELOG change, the #76 bullet, is a hunk of its own.

> **As built (2026-10-08):** the QA pass adds to what each commit carries. `World.kt` spans commits
> 1 and 2, as planned (`systemOf` arrives in commit 2), and now also holds the QA pass's KDoc fixes
> (§4.3). The README carries this unit's new fixes (§4.6): its core row, and the installed-systems
> bullet already shared with unit 2. C39's message reword in `AbstractWorldSystem` rides with this
> unit.

## From `plan-zone-world-system.md`

### §3.1 `src/main/kotlin/com/spartanlabs/gaming/world/zone/ZoneIndex.kt` — rewritten

> **As built (2026-10-08):** changes from the QA pass.
> - By the user's decision C35 (architecture §1.2), `step()` iterates a snapshot of
>   `World.gameObjects`, as `World.tick` does. An `EntityChangedZone` listener runs synchronously
>   inside the loop, so one that added or removed objects used to throw
>   `ConcurrentModificationException`. Its changes now take effect on the next step.
> - The class KDoc says `ZoneIndex` is not thread-safe: `zoneOf` and `entitiesIn` belong on the
>   thread driving the bound `World`.
> - `step`'s KDoc states the delivery order: owned entities' transitions in `World.gameObjects`
>   order (including any that left the grid's extent), then the "left the `World`" events in an
>   unspecified but deterministic order.
> - `zoneOf`'s KDoc says a miss costs two small allocations, the exception and its `Result` box.

### §3.2 `src/main/kotlin/com/spartanlabs/gaming/world/zone/EntityChangedZone.kt` — KDoc only

> **As built (2026-10-08):** the KDoc also points to `ZoneIndex.step` for the order in which a
> step delivers its events.

### §3.3 `src/main/kotlin/com/spartanlabs/gaming/world/zone/ZoneGrid.kt` — KDoc only (C13)

> **As built (2026-10-08):** two #47 code changes from the QA pass ride with this unit.
> - By the user's decision C36 (architecture §1.2), `ZoneGrid` copies its origin `Point` at
>   construction. It used to hold a reference to the space's own bounds location, so a later
>   change to the space's bounds would have moved the grid. The class KDoc now says the grid
>   copies its origin and cell size, and qualifies the immutability claim: its zones' `bounds` are
>   shared, mutable GeneralTools geometry that callers must not mutate in place.
> - By the user's decision C37, `Zone.kt` (not otherwise in this unit) keeps `Zone` a data class
>   but overrides `equals` and `hashCode` to use only `name`, `column` and `row`, so a zone stays a
>   stable key for `ZoneIndex` even if its `bounds` are mutated. GeneralTools#8 tracks read-only
>   geometry views.

### §3.5 Test sources (details and behaviours in §6)

> **As built (2026-10-05):** both renames were made on disk, not with `git mv`, so nothing was
> staged. That loses nothing: git stores no renames either way, and detects one at diff time when
> the old path's deletion and the new path's addition land in the same commit (this unit's commit 3,
> §8). Measured against `HEAD`, the integration test is about 68% similar to its predecessor, so it
> will show as a rename. The determinism test is about 38% similar, under git's default 50%
> threshold, so it will show as a deletion plus an addition, and `git log --follow` will not follow
> it at the default threshold.
> - `ZoneRefreshWorldIntegrationTest` → `ZoneIndexWorldIntegrationTest`
> - `ZoneIndexRefreshDeterminismTest` → `ZoneIndexDeterminismTest`

> **As built (2026-10-08):** the QA pass added two test files: `component/world/zone/ZoneTest.kt`
> (C37's equality, four tests) and `nonfunctional/world/zone/ZoneIndexQueryThroughputTest.kt`
> (§6.7). It also added cases to `ZoneIndexTest` and `ZoneGridTest` (§6.2).

### §3.7 `src/main/kotlin/com/spartanlabs/gaming/world/zone/UnzonedEntityException.kt` — new (C22)

> **As built (2026-10-08):** the KDoc says a miss costs two small allocations, the exception and
> the `Result` failure box that carries it, rather than one.

### §5 Documentation impact

> **As built (2026-10-08):** the QA pass softened "a `ZoneGrid` is immutable" in this bullet to
> "a `ZoneGrid` copies its geometry at construction", because its zones' `bounds` stay mutable
> (C36, C37).

> **As built (2026-10-05):** the inserted item 4 cites sections, not line numbers. Inserting it
> shifts every later line of `docs/plans/86-phase-1-map-and-space/49-physics/architecture.md`, so the line numbers it
> first cited were wrong as soon as it landed. The main session corrected the applied text, and
> the copy above was updated to match on 2026-10-04. The paragraph below keeps its line numbers,
> the file's before the insertion, as the record of how the list was found.

### §6.2 Level 2 — component (`testing.component.world.zone`)

> **As built (2026-10-05):** two names differ from the list, because the Kotlin/JVM compiler
> rejects `;` and `:` in function names ("name contains illegal characters", checked on Kotlin
> 2.2.0):
> - test 8 replaces `;` with ` -`:
>   `the binding is for life - rejected by another World even after uninstall - the bound World accepts it again`;
> - test 12 replaces `ZoneIndex::class` with "ZoneIndex's class":
>   `a substitute ZONE-slot system is not found under ZoneIndex's class, and blocks installing a ZoneIndex`.

> **As built (2026-10-08):** test changes from the QA pass, for the user's decisions C35–C37
> (architecture §1.2).
> - `ZoneIndexTest` gained two cases. For C35:
>   `a listener that spawns one object and removes another during a step does not disturb it, and the spawned object is placed on the next step`.
>   For C37:
>   `a ZoneIndex still answers entitiesIn and zoneOf correctly after a zone's bounds are mutated in place`.
> - The new `ZoneTest` covers C37 in four tests: `equality and hashCode ignore bounds`,
>   `mutating a zone's bounds in place changes neither its equality nor its hashCode`,
>   `zones differing in name, column or row are not equal`, and
>   `it stays a data class - copy and componentN still work`.
> - `ZoneGridTest` gained a C36 case:
>   `mutating the space's bounds location after construction does not change zoneAt answers`.
> - `ZoneIndexWorldSystemTest`'s test 4 is now
>   `stepping an index that was never installed fails with IllegalStateException`, without
>   "publishes nothing".
> - `ZoneFixtures` gained `@param` and `@return` KDoc.
> - Regression proof: with C35–C37 reverted, the new C35, C36 and C37 tests failed, the C35 one
>   with a real `ConcurrentModificationException`. Two of the `ZoneTest` cases also pass on the old
>   equality: they check behaviour the old equality already had.

### §6.5 Level 4a — deterministic (`testing.deterministic.world.zone`)

> **As built (2026-10-08):** `runScenario`'s KDoc was corrected. Its moves set absolute positions
> in −10..90, overshooting the 80×80 space on purpose so actors also leave and re-enter the grid.

### §8 Version control

> **As built (2026-10-05):** all three units were implemented in one working tree before any commit,
> so the precondition check before commit 1 does not hold as written. The commits above are cut from
> that tree by path and, where a file is shared, by hunk:
> - `ZoneGrid.kt` carries hunks for this unit's commit 3 (the `@param clamped` wording) and commit 4
>   (the class KDoc's pointers and immutability sentence), and for unit 3's commit 3 (`zoneAt`'s
>   failure and its `@return`).
> - `ZoneDrivenSimulationE2ETest` (reworked here) and `ZoneIndexSimulationLoopE2ETest` (new here)
>   each carry unit 3's commit-1 edit, a `.getOrNull()` on every `spawnPoint` call; this unit's
>   commit 3 stages them without it.
> - Four single lines are changed by two units, and the manager hand-splits each: README's Features
>   installed-systems bullet (unit 1's commit 3 and this unit's commit 5), and README's Modules
>   world row, README's Map & Space zone bullet and CONTRIBUTING's `gametools-world` row (this
>   unit's commit 5 and unit 3's commit 4).
> - In the CHANGELOG, this unit's #47 and #77 bullets (commit 6) and unit 3's #77 bullet (unit 3's
>   commit 4) form one unbroken run of changed lines, so commit 6 is staged without unit 3's bullet
>   by editing the hunk.
> - The two renames are recorded under §3.5.

> **As built (2026-10-08):** the QA pass adds to what each commit carries.
> - `ZoneGrid.kt` now also carries C36's origin copy and C37's KDoc wording, besides the hunks for
>   this unit's commits 3 and 4 and unit 3's commit 3.
> - `ZoneIndexTest.kt` carries this unit's commit-3 rework plus the new C35 and C37 cases.
> - `ZoneIndex.kt` carries C35's snapshot loop and the new KDoc (§3.1); `Zone.kt` (C37),
>   `ZoneTest.kt` and `ZoneIndexQueryThroughputTest.kt` are new to this unit.
> - The CHANGELOG carries the softened `ZoneGrid` wording in this unit's #77 bullet (§5), in the
>   same run of lines as before.
> - C36 and C37 are #47 code that rides with this unit. Whether they get `fix` commits of their own
>   is the manager's call.

## From `plan-tiled-map-result-lookups.md`

### §4.1 Changed: `MAP/TiledMap.kt`

> **As built (2026-10-08):** `terrainAt`'s KDoc says a miss costs two small objects, the `Result`
> failure wrapper and the exception, and no stack walk, rather than "one small allocation".

### §4.4 New: `MAP/OutOfGridException.kt` (C25, C28, C29)

> **As built (2026-10-08):** in both new types' KDoc (§4.3 and this section), "Stackless by design"
> says a miss costs two small objects, the `Result` failure wrapper and the exception, and no stack
> walk, rather than "one allocation".

### §4.5 Changed: `ZONE/ZoneGrid.kt` (C30)

> **As built (2026-10-08):** the `@return` states the stackless rationale generically, "so a caller
> that meets misses in bulk pays no stack walk for any of them", rather than naming
> `ZoneIndex.step`. `ZoneGrid.kt` also carries unit 2's C36 and C37 changes (unit 2 §3.3); §8
> lists how its hunks split.

### §4.6 New: `ZONE/UnzonedPointException.kt` (C30)

> **As built (2026-10-08):** the class KDoc's "Stackless by design" paragraph says a miss costs a
> few small objects (the `Result` failure wrapper, the exception and its copy of the point) and no
> stack walk, "which matters because a caller may meet misses in bulk". It no longer names
> `ZoneIndex.step`.

### §6.2 Level 2 — component

> **As built (2026-10-05):** the `assertNull` import was removed in commit 2, not commit 1. Until
> commit 2 rewrites the `terrainAt` test, its `assertNull` calls still need the import, so removing
> it in commit 1 would not compile.

### §6.8 What cannot be tested automatically

> **As built (2026-10-08):** an automated check for criterion 6 was attempted and dropped. The QA
> pass's test T6 compared bytes allocated per call by `isWalkable` and by `tileAt`. Run alone it
> passed, 0 against 0 bytes per call, because escape analysis removed the allocations; in the full
> test JVM it failed, 48 against 0. Its result depends on JIT state, so it cannot be made reliable.
> Review confirms that #77 adds no allocation on the in-grid path. The 32-byte iterator T6 exposed
> was pre-existing, in `StaticGeometry.blocksPoint`, and the user's decision C38 (architecture
> §1.2) removes it in this PR's own `perf(world)` commit.

### §8 Version control

> **As built (2026-10-05):** commits 3 and 4 needed no deviations, and commits 1 and 2 only the
> `assertNull` import's move to commit 2 (§6.2). All three units were implemented in one working
> tree before any commit, so the precondition above does not hold as written. The commits are cut
> from that tree by path and, where a file is shared, by hunk:
> - Within this unit, `TiledMap.kt`, `TiledMapTest` and `MapLoaderIntegrationTest` each span
>   commits 1 and 2: their `spawnPoint` hunks are commit 1's, their `terrainAt` hunks commit 2's.
> - `ZoneGrid.kt` carries hunks for unit 2's commits 3 and 4 and for this unit's commit 3.
> - Unit 2's `ZoneDrivenSimulationE2ETest` and `ZoneIndexSimulationLoopE2ETest` carry this unit's
>   commit-1 edit, a `.getOrNull()` on every `spawnPoint` call.
> - README's Modules world row and Map & Space zone bullet, and CONTRIBUTING's `gametools-world`
>   row, are single lines changed by unit 2's commit 5 and this unit's commit 4; the manager
>   hand-splits them.
> - In the CHANGELOG, this unit's bullet directly follows unit 2's commit-6 bullets in one unbroken
>   run of changed lines, so it is separated by editing the hunk.

> **As built (2026-10-08):** the QA pass changed what commits 2 and 3 carry and added one commit.
> - Commits 2 and 3 carry the reworded KDoc of §4.1 and §4.4–§4.6: two small objects per miss (a
>   few for `UnzonedPointException`), and a generic stackless rationale that no longer names
>   `ZoneIndex.step`.
> - `ZoneGrid.kt` now also carries unit 2's C36 origin copy and C37 KDoc wording, besides the hunks
>   above.
> - By the user's decision C38, `StaticGeometry.blocksPoint` becomes an indexed loop with no
>   iterator allocation, in its own `perf(world)` commit in this PR (§6.8).
