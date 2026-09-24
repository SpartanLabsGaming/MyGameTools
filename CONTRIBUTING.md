# Contributing to GameTools

This is the working agreement for every `SpartanLaboratories/*` repository. It is written for
a team even while the team is one person — the point is that the process is already in place
when the second contributor arrives.

- [Coding rules](#coding-rules)
- [Module layout](#module-layout)
- [Planning large work](#planning-large-work)
- [Branching model](#branching-model)
- [Commit messages](#commit-messages)
- [Pull requests](#pull-requests)
- [Merge strategy](#merge-strategy)
- [Running the build and tests](#running-the-build-and-tests)
- [Versioning](#versioning)
- [Releasing](#releasing)
- [Deployment environments](#deployment-environments)

## Coding rules

All code, tests, and documentation follow [`.aiassistant/rules/CLAUDE.md`](.aiassistant/rules/CLAUDE.md):
Kotlin idioms blended OO/FP, `Result` instead of thrown exceptions for expected failures,
structured slf4j logging, KDoc on every public declaration, region-grouped imports, one test
class per file. Tests are organised by the five-level hierarchy into
`com.spartanlabs.gaming.testing.<level>` packages.

## Module layout

Since `4.0.0` the library is a Gradle multi-module build. As of the `gametools-world`
bootstrap (issue #48) it is published as four Maven coordinates:

| Module | Coordinate | Contents | Depends on |
| --- | --- | --- | --- |
| `gametools-core` | `io.github.spartanlabsgaming:gametools-core` | `com.spartanlabs.gaming.{gameobjects,spatial,event,simulation}.*`, `com.spartanlabs.geometry.serializations.*` | — |
| `gametools-net` | `io.github.spartanlabsgaming:gametools-net` | `com.spartanlabs.gaming.networking.*` (`GameServer`, `MouseAction`) | `api(project(":gametools-core"))` |
| `gametools-world` | `io.github.spartanlabsgaming:gametools-world` | Phase 1 map/zone/physics/vision systems (issues #46–#50); `com.spartanlabs.gaming.world.map.*` — `TiledMap`, `TerrainLayer`, `TerrainType`, `StaticGeometry`, `SpawnPoint`, `MapDefinition`, `MapLoader` (#46); `com.spartanlabs.gaming.world.zone.*` — `Zone`, `ZoneGrid`, `ZoneIndex`, `EntityChangedZone` (#47) | `api(project(":gametools-core"))` |
| `gametools` (umbrella) | `io.github.spartanlabsgaming:gametools` | no source — `api` re-export of every module above | all three |

Shared build configuration lives in the `build-logic/` included build as the
`gametools.kotlin-library` / `gametools.published-library` convention plugins; a module build
file is a `plugins {}` block plus its `coordinates(...)` and `pom {}`. New modules on the
roadmap (`gametools-combat`, …) are added the same way.

Every module has its own five-level test tree under `src/test/kotlin/com/spartanlabs/gaming/testing/<level>/`.
The per-level Gradle tasks (`componentTest`, `integrationTest`, `deterministicTest`,
`e2eTest`, `nonfunctionalTest`) and `./gradlew build` span every module; the four CI check
names are unchanged by the split.

## Planning large work

Every unit of work starts as an issue. When one design spans **more than one PR** (a roadmap
phase, or a system of systems like World Systems), it also gets a **tracking issue** above
those issues. GitHub offers three tools here, each with one job:

| Tool | Job | Rule |
| --- | --- | --- |
| **Tracking issue + sub-issues** | Structure: what the work is and how it breaks down | One tracking issue per initiative (label `type: tracking`, template *Tracking issue*). Each stage is a sub-issue of it. A stage that grows its own stages becomes a tracking issue in turn. |
| **Milestones** | When: which release ships it | Set a milestone on a sub-issue once it is scheduled for a release (`5.3.0`). Tracking issues can span releases and carry none. |
| **The roadmap Project** | Status: one board across every initiative | A single *GameTools Roadmap* Project, not one per system. Its `Initiative` field groups items, with one saved view per initiative. |

**Rules**

1. **Planning and doc PRs use `Refs #N`, never `Closes #N`.** Only the PR that implements an
   issue closes it. A merged plan is not a delivered feature (issues #49 and #76 were each
   closed by their plan PR and had to be reopened).
2. **Record dependencies as links, not prose.** Use the sub-issue hierarchy and GitHub's
   *blocked by* relationship. Write "Depends on #76" in a body only in addition to the link,
   and never leave a placeholder such as `#-tbd` once the issue exists.
3. **An issue has one parent.** When a stage belongs to two initiatives, put it under the one
   that owns its delivery and cross-reference it from the other tracking issue's
   *Cross-initiative dependencies* section.
4. **The tracking issue is the source of truth for scope and order.** Reorder, add or drop
   stages there, with a comment saying why, before the change lands in a plan doc.
5. **Branches stay per issue.** Name a branch after the sub-issue being implemented
   (`feature/77-zone-world-system`), never after the tracking issue.

**Roadmap Project fields**

| Field | Values | Replaces |
| --- | --- | --- |
| `Status` | Todo · Planned · In progress · Blocked · Done | the `status: blocked` label |
| `Initiative` | Phase 1 · World Systems · Combat · … (one value per tracking issue) | — |
| `Phase` | Roadmap phase 0–7 | — |

Enable the Project's built-in workflows: *auto-add* for new issues in this repository, *item
closed → Done*, and *PR merged → Done*. The Project may include issues from other
`SpartanLabsGaming` repositories when an initiative depends on them.

**Current tracking issues**

| Initiative | Tracking issue | Sub-issues |
| --- | --- | --- |
| Phase 1 — Map & space | #86 | #46, #47, #48, #49, #50 |
| World Systems | #87 | #76, #77, #78, #79, #80 |

## Branching model

Trunk-based development. `master` is always releasable and never receives direct commits —
branch protection enforces this.

| Prefix | For | Example |
| --- | --- | --- |
| `feature/<issue#>-<slug>` | new functionality | `feature/1-alive-cancel-attack` |
| `fix/<issue#>-<slug>` | bug fixes | `fix/2-attack-dead-target` |
| `chore/<slug>` | tooling, deps, CI — no product change | `chore/bump-kotlin` |
| `docs/<slug>` | documentation only | `docs/quadtree-readme` |
| `release/<version>` | release preparation (short-lived) | `release/1.10.0` |
| `hotfix/<version>` | patch a released version (branch off its tag) | `hotfix/1.10.1` |

Branch off the latest `master`. Keep branches short-lived — hours to a couple of days. There
is no `develop` branch and there are no per-environment branches.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body — why, not what; wrap ~72 cols>

<footers>
```

- **Types:** `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`
- **Scopes:** `gameobjects`, `networking`, `spatial`, `serialization`, `build`, …
- **Breaking changes:** `feat(x)!:` in the subject **and** a `BREAKING CHANGE:` footer
- Reference issues in the body (`Refs #2`); the PR that implements an issue closes it
  (`Closes #2`). Planning and doc PRs only reference it — see
  [Planning large work](#planning-large-work)

Commits on your own branch may be rough — tidy them with `git rebase -i` before the PR is
ready for review.

## Pull requests

Every change reaches `master` through a PR. Even solo.

1. `git switch -c fix/2-attack-dead-target`
2. Commit, `git push -u origin HEAD`
3. `gh pr create` — fill in the template; title **must be a valid Conventional Commit**
   (it becomes the merge-commit subject)
4. CI must be green
5. Branch must be up to date with `master` — use **Update with rebase**, never a merge from
   `master` into your branch
6. Merge, then delete the branch

A second approving review becomes required once a second person is on the repo.

## Merge strategy

**Semi-linear history.** Rebase locally, merge publicly.

- You rebase your **own** feature branch onto `master`. You never rebase anything that is
  shared (`master`, a release branch someone else is on).
- The feature branch merges **up** to `master` as a **merge commit** (`--no-ff`). That merge
  commit is the record that a unit of work landed, and where.
- On GitHub only **“Create a merge commit”** is enabled — “Squash” and “Rebase and merge” are
  turned off, so the strategy is not a per-PR decision.
- Read history at the feature level with:

  ```
  git config --global alias.lg "log --first-parent --oneline --graph"
  ```

## Running the build and tests

Requires JDK 23. The Gradle wrapper pins Gradle 9.7.1.

| Command | What it runs |
| --- | --- |
| `./gradlew componentTest deterministicTest` | Levels 2 + 4a — fast, no sockets. Run before every push. |
| `./gradlew integrationTest e2eTest nonfunctionalTest` | Levels 3 + 4b + 4c — bind fixed UDP ports; serialized by the `GameServerPortsLock` build service. |
| `./gradlew test` | Every level. |
| `./gradlew dokkaGeneratePublicationHtml` | API docs — also catches broken KDoc links. |
| `./gradlew build` | Compile, test, assemble. |

> The port-binding tests fail with `java.net.BindException` if a `MainKt` game server (or a
> previous test run) is still holding the common UDP ports. That is an environment problem,
> not a code failure — stop the stray process and re-run.

## Versioning

`Major.Feature.MinorChange`, optionally a trailing letter for a bug fix (e.g. `1.5.2a`). The
version lives only in the `coordinates(...)` call in each module's `build.gradle.kts`
(`gametools-core`, `gametools-net`, `gametools`) — all three modules release together on one
version, so the release branch bumps all three.

| Change | Bump | Example |
| --- | --- | --- |
| `feat:` | Feature release | `1.9.0` → `1.10.0` |
| `fix:` / `perf:` | MinorChange, or a trailing letter | `1.9.0` → `1.9.1` / `1.9.0a` |
| `feat!:` / `BREAKING CHANGE:` | Major release | `1.9.0` → `2.0.0` |
| `docs` / `chore` / `ci` / `test` / `build` / `refactor` | none — rides the next release | |

## Releasing

1. All target changes are merged to `master` and CI is green.
2. `git switch -c release/1.10.0` — bump the version in every module's `coordinates(...)`
   (`gametools-core`, `gametools-net`, `gametools`), move the `CHANGELOG.md` `[Unreleased]`
   entries under a new `[1.10.0]` heading with today's date, and update the link references.
3. PR → merge. The merge commit is `chore(release): 1.10.0`.
4. `git tag -a v1.10.0 -m "Release 1.10.0"` on `master`, then `git push origin master --follow-tags`.
5. `release.yml` creates the GitHub Release from the tag.
6. **Publish manually:** `./gradlew publishAndReleaseToMavenCentral` stages and releases all
   three coordinates. This step is irreversible and stays a deliberate human action — no
   Maven Central credentials live in CI.

Credentials for step 6 are in `~/.gradle/gradle.properties`
(`mavenCentralUsername` / `mavenCentralPassword`, `signing.*`).

## Deployment environments

Environments (dev / staging / production) are **GitHub Environments**, never branches. A
release is one immutable tagged artifact promoted from one environment to the next; only
configuration differs between them. Library releases have no environments — Maven Central is
production, and a `-SNAPSHOT` publish from `master` is the staging analogue for downstream
projects.
