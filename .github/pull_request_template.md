<!--
The PR title must be a valid Conventional Commit — it becomes the merge-commit subject.
e.g.  feat(gameobjects): add Alive.cancelAttack()
-->

## What & why

<!-- What does this change and why. Link the issue.
     Implementation PR: `Closes #N`. Planning / doc-only PR: `Refs #N` — a merged plan does not
     close its issue. Name the tracking issue too if the issue is a sub-issue of one. -->

Closes #

## How it was tested

<!-- Which test levels ran, new tests added, anything checked by hand. -->

- [ ] `./gradlew componentTest deterministicTest`
- [ ] `./gradlew integrationTest e2eTest nonfunctionalTest` (if networking / world state touched)

## Checklist

- [ ] Follows `.aiassistant/rules/CLAUDE.md` (KDoc, `Result`, logging, import regions, one test class per file)
- [ ] Tests added or updated at the appropriate level(s)
- [ ] `CHANGELOG.md` `[Unreleased]` updated (for `feat` / `fix` / breaking changes)
- [ ] Branch is rebased on the latest `master`
- [ ] Version bump needed? If so, note it here (done in a separate `release/*` PR)
