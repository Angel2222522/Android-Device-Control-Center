# Test Results

## 2026-08-09 — Phase 0 documentation checks

- Production build: **NOT RUN** — production code intentionally does not exist.
- Unit tests: **NOT RUN** — no code yet.
- Emulator tests: **NOT RUN**.
- Android 16 physical-device tests: **NOT RUN**.
- Feasibility matrix: **DOCUMENTATION-VERIFIED**, not device-verified.

Future entries must include date, commit SHA, environment/device, exact command or procedure, result and retained evidence/artifact.

## 2026-08-09 — Phase 1 foundation

- File-presence/static scaffold check: **PASSED**.
- Repository: `Angel2222522/Android-Device-Control-Center` (`main`).
- Commit: `5ef4d48de24a29626664a2b9b14765b7ba3ce5fc`.
- GitHub Actions run: `31311726075`.
- Android 16 SDK setup: **PASSED**.
- `lintDebug`: **PASSED**.
- `testDebugUnitTest`: **PASSED**.
- `assembleDebug`: **PASSED**.
- Artifact upload: **PASSED** — `android-verification`, artifact ID `9037597185`, 9,476,976 bytes, SHA-256 digest `44709102816321a7a08a7cf307a433b85ffd2108ba5e46af5ae649ca567aaf54`.
- Physical Android 16 installation: **PASSED** — confirmed by user-provided screenshot.
- Physical Android 16 launch/render: **PASSED** — activity remained open and rendered the expected Compose foundation screen.
- Exact device telemetry: **NOT YET TESTED**.

## 2026-08-09 — Phase 2 capability snapshot

- Pull request: `#1`.
- Tested head commit: `0f40dfcf662f8b7f648a2a716463194ebc5ccc61`.
- Squash merge commit: `0ddf03c2d9b2f08c364b791ad91eb1d8df3d24e9`.
- GitHub Actions run: `31312304385`.
- Android SDK/toolchain setup: **PASSED**.
- `lintDebug`: **PASSED**.
- `testDebugUnitTest`: **PASSED**.
- `assembleDebug`: **PASSED**.
- Artifact upload: **PASSED** — artifact ID `9037728326`.
- Extracted APK ZIP integrity: **PASSED**.
- APK SHA-256: `6e21c96474ee91f202c237b8300790a1b97b6877b8274640b4986ada01839563`.
- APK size: `27,014,110` bytes.
- Target-device telemetry values and rendering: **UNVERIFIED**.

## 2026-08-09 — Physical update failure

- Attempt: install Phase 2 APK over the Phase 1 APK.
- Result: **FAILED**.
- Android message: package conflicts with an existing package.
- Root cause: GitHub-hosted runners generated different ephemeral debug signing certificates for builds with the same application ID.
- Application code/telemetry was not launched, so Phase 2 remains physically unverified.
- Corrective action: repository-pinned development-only debug keystore; production signing remains separate.
- Required verification: CI signature inspection, clean install after removing the old APK, then installation of a subsequent debug build without uninstalling.

## 2026-08-09 — Stable debug-signing correction

- Pull request: `#3`.
- First independent CI run: `31312849110` — build/lint/tests and stable-keystore restoration **PASSED**.
- Second independent CI run: `31312958333` — build/lint/tests **PASSED**.
- Explicit `apksigner verify --print-certs`: **PASSED**.
- Verified certificate SHA-256: `f805690fd2b6a9e925d6da491fbbb2839df7df581db580f58eb7f26742804c7a`.
- Squash merge commit: `e1d78cea001f0da8769ccf2db70eb6296407ec20`.
- Clean installation on target phone: **UNVERIFIED**.
- Subsequent in-place update on target phone: **UNVERIFIED**.
