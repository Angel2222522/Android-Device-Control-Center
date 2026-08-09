# Android Device Control Center

Android 16-first, local-first device intelligence and guided-control application.

The project is in **Phase 2: capability and permission center**.

Phase 1 foundation and the RAM/thermal/storage/access snapshot are verified in CI and on the target Android 16 phone. The factual battery snapshot and truthful unavailable-voltage handling are physically verified; this target does not expose a usable voltage measurement. The first deterministic diagnosis-engine slice is CI-verified and physically inspected on the target; PR #8 is pending merge and performs no automatic action.

## Product contract

- Truth over marketing
- Diagnosis before action
- Local-first data
- Measurable before/after outcomes
- Standard Android first, optional Shizuku advanced mode
- No fake RAM or CPU boosters

## Ground truth documents

- `PROJECT_SCOPE.md` — the agreed product and engineering contract
- `PROJECT_STATE.md` — the exact current state
- `TEST_RESULTS.md` — executable and physical evidence
- `TODO.md` — completed and remaining work
- `DECISIONS.md` — locked and open decisions
- `FEATURE_MATRIX.md` — Android/Shizuku/root feasibility
- `HANDOFF_NEXT_SESSION.md` — exact continuation point
- `PHASE_0_RESEARCH.md` — research and sources
