# Handoff — Next Session

1. Read `PROJECT_STATE.md`, `DECISIONS.md`, `FEATURE_MATRIX.md`, `TEST_RESULTS.md`, `KNOWN_LIMITATIONS.md`, `TODO.md` and `PHASE_0_RESEARCH.md`.
2. Inspect actual repository branch/HEAD if a repository has since been created.
3. The local Android 16 foundation and CI workflow exist but are unverified.
4. Obtain the existing GitHub repository identifier from the user; repository creation itself is not available through the connector.
5. Publish the exact local foundation, trigger CI, inspect failures, fix and rerun.
6. The first executable success criterion is a clean CI build/lint/unit-test run and downloadable APK artifact. It is not feature implementation.

**Current production code:** foundation screen only; no collectors.  
**Current build/test evidence:** none; scaffold unverified.  
**First unfinished point:** publish to an existing GitHub repository and run CI.
