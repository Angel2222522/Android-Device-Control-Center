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

## 2026-08-09 — Thermal semantics correction

- Official Android `PowerManager` documentation: **VERIFIED**.
- Finding: `getThermalHeadroom(0)` returns thermal-envelope use; `1.0` is the severe-throttling threshold and values may exceed it.
- Initial wording “Θερμικό περιθώριο” was technically counterintuitive and recorded as a presentation defect.
- PR #4 head: `c392add0f55cd641e138d346797307415eb4b3f1`.
- GitHub Actions run: `31313577829`.
- Build, lint, unit tests, stable-certificate verification and artifact upload: **PASSED**.
- Merge commit: `0be0590673d079eb761fc56a288d915059282b91`.
- Corrected physical rendering: **UNVERIFIED**.
- In-place APK update: **UNVERIFIED**.

## 2026-08-09 — Stable-signing in-place update and corrected UI

- Procedure: installed `Device-Control-Center-thermal-semantics-update.apk` directly over the existing stable-signed Phase 2 app without uninstalling.
- In-place Android package update: **PASSED**.
- Application launch after update: **PASSED**.
- Corrected thermal wording rendered: **PASSED**, screenshot evidence retained.
- Observed after update: RAM 3.53 GB total / 1.03 GB available / low-memory flag false; thermal status severe; thermal-envelope use 107%; storage 101.76 GB total / 28.46 GB available.
- Stable development-signing solution: **PHYSICALLY VERIFIED ACROSS SUCCESSIVE BUILDS**.

## 2026-08-09 — OEM RAM/storage comparison and truth labels

- Target device: OPPO A60 5G, Android/ColorOS 16.0.5.
- OEM settings evidence: 4.00 GB physical RAM + 4.00 GB storage-backed RAM expansion; UI reports 3.09 GB used and 4.91 GB available across the combined pool.
- App evidence: 3.53 GiB kernel-accessible RAM; 1.03 GiB available; low-memory flag false.
- OEM storage evidence: 96.9 GB / 128 GB shown as used/advertised total.
- App evidence: 101.76 GiB app-data filesystem; 28.46 GiB available.
- Official Android semantics for `advertisedMem`, `totalMem`, `availMem` and `StatFs`: **VERIFIED**.
- PR #5; head `fd4a7dea3f9c84b0be80d9dc1972c5494c8027c0`; CI run `31314062520`: build/lint/tests/certificate/artifact **PASSED**.
- Merge commit: `addd31b6b2ded85b56968f8456578a882014e003`.
- Physical rendering of new split labels: **UNVERIFIED**.

## 2026-08-09 — GB/GiB unit defect and correction

- Physical PR #5 rendering: **PASSED**, but exposed a presentation defect.
- Observed `advertisedMem`: 4,000,000,000 bytes, rendered as 3.73 “GB”.
- Root cause: formatter divided by 2^30 (GiB) but used the suffix GB.
- Correct expectation: 4.00 GB decimal = 3.73 GiB binary.
- PR #6 head `ced43516093e2072dde0db46e6abe3ac6d58b15d`; CI run `31314345629`.
- Build, lint, unit tests, certificate verification and artifact: **PASSED**.
- Merge commit: `4b9ed5d14b7f5d08f081e924bf8ec20700912c3c`.
- Corrected physical rendering: **UNVERIFIED**.

## 2026-08-09 — Corrected units physical verification

- Installed PR #6 stable-signed APK over the existing app without uninstalling: **PASSED**.
- RAM labels: **PASSED** — 4.00 GB (3.73 GiB) advertised physical RAM; 3.53 GiB kernel-accessible; 1.08 GiB available; 0.42 GiB low-memory threshold.
- Storage labels: **PASSED** — 28.37 GiB available; 101.76 GiB app-data filesystem.
- Thermal capture: 106% thermal-envelope use and severe thermal restriction; values are dynamic.
- Screenshot evidence: user-provided and archived in the project evidence folder.
- GB/GiB presentation milestone: **PHYSICALLY VERIFIED**.

## 2026-08-09 — Battery factual snapshot implementation

- Official Android `BatteryManager` reference and Android 16/AOSP battery-service semantics: **VERIFIED**.
- Confirmed units: battery temperature broadcast is in tenths of a degree Celsius; voltage is in millivolts; current properties are in microamperes with positive meaning net current entering the battery and negative meaning net discharge; charge counter is in microampere-hours; energy counter is in nanowatt-hours.
- PR #7 head `ebdb8b289c27f472516865c5f839668e02bd65f7` initially failed CI run `31315368065` at unit-test compilation because one nullable `Double` was passed to a non-null assertion overload. No production compilation error occurred.
- Corrective commit `9ffe70d5a240c7d4e8e5e64c39bbafb4f3e829eb` fixed the test assertion.
- CI run `31315477707`: lint, unit tests, Android 16 build, stable certificate verification and artifact upload **PASSED**.
- Battery tests cover level conversion, status/source labels, unit conversions and unsupported-property handling.
- Artifact ID: `9038613946`; artifact digest: `sha256:7a4a12b561bf07ce81453dd663709a5c563efdedc47c8dac8f2a05fb00852bc8`.
- Extracted APK SHA-256: `9f8ee46e05793606f33fe1959a7b7e94c4ae8ea246603f02a73954706ee97480`.
- Physical installation and inspection on the target OPPO Android 16 phone: **PENDING**. No physical battery value is claimed yet.
