# Test Results

## 2026-08-09 — Phase 0 documentation checks

- Production build/tests/device tests: **NOT RUN** — production code intentionally did not exist.
- Feasibility matrix: **DOCUMENTATION-VERIFIED**, not device-verified.

## 2026-08-09 — Phase 1 foundation

- Repository: `Angel2222522/Android-Device-Control-Center`.
- Commit: `5ef4d48de24a29626664a2b9b14765b7ba3ce5fc`; GitHub Actions run: `31311726075`.
- Android 16 SDK setup, `lintDebug`, `testDebugUnitTest`, `assembleDebug`, artifact upload: **PASSED**.
- Physical Android 16 installation and launch/render: **PASSED**, confirmed by screenshot.

## 2026-08-09 — Phase 2 capability snapshot

- PR #1; tested head `0f40dfcf662f8b7f648a2a716463194ebc5ccc61`; merge `0ddf03c2d9b2f08c364b791ad91eb1d8df3d24e9`.
- GitHub Actions run `31312304385`: SDK setup, lint, unit tests, assembly and artifact upload **PASSED**.
- Initial Phase 2 APK SHA-256: `6e21c96474ee91f202c237b8300790a1b97b6877b8274640b4986ada01839563`.
- Initial physical update: **FAILED** because different GitHub runners produced different ephemeral debug certificates.

## 2026-08-09 — Stable debug-signing correction

- PR #3; CI runs `31312849110` and `31312958333`: build/lint/tests **PASSED**.
- `apksigner verify --print-certs`: **PASSED**.
- Certificate SHA-256: `f805690fd2b6a9e925d6da491fbbb2839df7df581db580f58eb7f26742804c7a`.
- Merge commit: `e1d78cea001f0da8769ccf2db70eb6296407ec20`.
- Delivered stable APK SHA-256: `85f02f94780d85e641c7120a1973e66db747210e254285f9ed228b8caf789b1d`.
- APK ZIP integrity: **PASSED**.
- Clean installation after one-time removal of the ephemeral build: **PASSED**.
- Launch and Phase 2 screen rendering on target Android 16 phone: **PASSED**, screenshot evidence supplied by user.
- Observed RAM: 3.53 GB total; 1.13 GB available; 0.42 GB low-memory threshold; low-memory flag false.
- Observed storage: 101.76 GB total; 28.54 GB available.
- Observed thermal output: serious thermal load; headroom 1.02.
- Observed Usage Access / All Files Access: not granted; no automatic permission request.
- Thermal severity interpretation against official semantics/device behavior: **UNVERIFIED**.
- Comparison with Android system-information values: **UNVERIFIED**.
- Subsequent in-place stable-signed update: **UNVERIFIED**.
