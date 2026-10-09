# GameTools — Post-Roadmap Exploratory Ideas

## Header / Association

- **Status:** idea log only. Nothing here is planned, scheduled, or committed to. Every entry
  sits *outside* the seven phases of [`framework-vision-and-roadmap.md`](framework-vision-and-roadmap.md);
  an idea that is picked up gets its own `docs/` plan (via the planner) and its own GitHub issue
  before any implementation, and is then marked **Promoted** here with a link.
- **Tracking:** #128 lists the same entries.
- **Adding an idea:** append a section below with a short scope paragraph, what prompted it,
  and any related issues or plans. Keep it to the idea — no design.

---

## Profiler

- **Status:** Idea
- **Added:** 2026-09-28
- **Issue:** #129

A profiling facility for GameTools' simulation. It would let a consumer, or the library's own
tests, measure where a frame's time actually goes rather than asserting ad-hoc "sane time
budgets". Candidate areas it covers: per-`WorldSystem` step cost and `World.stepSystems()` as a
whole, `installSystem`/`uninstallSystem` cost, `World.tick()` throughput, and spatial-index
operations.

**Prompted by:** the #76 QA pass found that the installed-systems registry's install/uninstall
cost (documented as O(n) in `docs/plans/87-world-systems/76-world-system-core/plan.md` §7) is never timed.
`WorldSystemRegistryRobustnessTest` does its 1,000 installs in untimed setup. That gap is one area
the profiler would cover, not its whole purpose.

**Related:** Phase 7's scale-hardening work (#119, #122) also measures performance. How the two
relate is to be settled when this idea is planned.
