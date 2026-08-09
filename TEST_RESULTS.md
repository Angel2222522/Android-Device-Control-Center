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
- Physical installation and inspection on the target OPPO Android 16 phone was pending at the time of this CI checkpoint; the subsequent target inspection is recorded below.

## 2026-08-09 — Battery checkpoint physical inspection

- Installed the stable-signed PR #7 checkpoint APK over the existing app: **PASSED**.
- Target: OPPO A60 5G, Android/ColorOS 16.0.5; app launched and rendered the battery card: **PASSED**.
- Physically observed: 66% level, discharging state, battery source, 36.3 °C temperature and 3,108 mAh charge counter. These are observed snapshot values, not battery-health or full-capacity estimates.
- Voltage returned by the Android battery broadcast: `3 mV (0.003 V)`. This is physically implausible for a live phone and is **FAILED / NOT VERIFIED**.
- Current returned: `929 μA (0.93 mA)` while the status was discharging. It is retained as a raw vendor sensor value; no drain direction or causal conclusion is claimed.
- Decision: do not merge PR #7. Add plausibility validation, a standard read-only `power_supply` sysfs fallback where accessible, explicit voltage provenance and an unavailable state when no trusted value exists. No heuristic `3 -> 3000 mV` conversion will be added.

## 2026-08-09 — Battery voltage validation correction CI

- Corrective commit: `fff926687aeec0b4c2e7058c3efe80060a6e0eb`.
- Added plausibility gating for broadcast voltage, standard read-only `power_supply` sysfs μV fallback, source provenance and explicit unavailable/rejected rendering.
- Added regression tests for the observed `3 mV` case, trusted sysfs fallback and no-fallback behavior.
- GitHub Actions run `31316180145`: lint, unit tests, Android 16 build, stable certificate verification and artifact upload **PASSED**.
- Artifact ID `9038822328`; artifact digest `sha256:1c592e07f4d7c1216407c74431e65790aa380b743383dc8801786a3e920a2d02`.
- Extracted corrected APK SHA-256: `f8e87d4e2681c2bb329df814b32b2c61bd779b08275a210a39f6af043e8231c`.

## 2026-08-09 — Battery corrected checkpoint physical verification

- Installed the corrected stable-signed APK over the existing app without uninstalling: **PASSED**.
- Target screenshot: OPPO A60 5G, Android/ColorOS 16.0.5; application launched and retained the existing RAM, thermal, storage and access-state cards: **PASSED**.
- Battery snapshot rendered: 62% level, discharging, battery source, 35.8 °C, instantaneous raw current `762 μA (0.76 mA)`, charge counter `2,943 mAh`; values are point-in-time and dynamic.
- Invalid broadcast voltage path: **PASSED** — the prior `3 mV` value is no longer displayed as a measurement. The UI shows `Τάση: μη διαθέσιμη` and identifies the source as unavailable/rejected as untrusted.
- Trusted voltage measurement on this target: **NOT AVAILABLE**. The read-only sysfs fallback did not provide a usable value. No voltage value is claimed.
- Battery factual snapshot and truthful unavailable-state handling: **PHYSICALLY VERIFIED**.
- Battery health, true capacity and drain-direction interpretation remain intentionally unimplemented.

## 2026-08-09 — Battery milestone merge

- PR #7 merged successfully: `76eb50e29dee6cb72310c47416961d9b601d9bad`.
- Direct repository check after merge: `main` points to `76eb50e29dee6cb72310c47416961d9b601d9bad`.
- The code-validation CI evidence remains run `31316180145`, which passed lint, tests, Android 16 build, signing verification and artifact upload. No separate workflow run was returned for the merge commit by the repository's pull-request workflow query.

## 2026-08-09 — Diagnosis engine v1 implementation checkpoint

- Official API semantics checked before implementation against Android `ActivityManager.MemoryInfo` and `PowerManager` documentation: **VERIFIED**.
- Implemented three deterministic current-snapshot rules: official `lowMemory` state, current thermal status and battery-voltage data quality.
- Findings include rule ID/version, condition or data-quality type, product severity, raw-derived evidence and limitations. The report has no generic score and performs no action.
- Unit tests were added for stable state, low-memory warning, severe/critical thermal states, battery data-quality reporting and deterministic ordering.
- Local Gradle execution: **NOT RUN** in the inspection workspace; GitHub Actions remains the build/test gate.
- GitHub Actions run `31317443100`: lint, unit tests, Android 16 build, stable-certificate verification and artifact upload **PASSED**.
- Artifact `9039174070`; artifact digest: `sha256:64307230b197a03646d5b36838ef948e7db84882d243f448097f14d3faf23070`.
- Extracted APK SHA-256: `eb87948a2b259cca9624724b491634f5ac45de9a2daa6d362f4c6c2911bd9d0b`.
- Diagnosis engine v1 implementation and CI: **VERIFIED**. APK installation/inspection and target-phone physical verification: **PENDING**.
