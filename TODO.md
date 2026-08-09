# TODO

## Product decisions that remain open

- [x] Use a public canonical GitHub repository.
- [ ] User chooses storage-permission posture.
- [ ] User decides whether the local-VPN firewall is core or later optional scope.
- [ ] User chooses initial brand/experience direction.
- [ ] Confirm product/license strategy before borrowing any implementation.
- [ ] Accept or revise proposed milestones.

## Phase 1 — Foundation and signing

- [x] Create and publish Android 16-only Kotlin/Compose foundation.
- [x] Add lint, unit-test, APK build and artifact pipeline.
- [x] Verify foundation installation and launch on target Android 16 phone.
- [x] Diagnose ephemeral CI debug-signature conflict.
- [x] Add and CI-verify stable development-only signing in two independent builds.
- [x] Remove the final ephemeral-signed installed build once.
- [x] Verify clean installation and launch of the stable-signed APK.
- [x] Verify a later stable-signed APK updates in place without uninstalling.
- [ ] Establish separate production release-signing strategy without committed secrets.

## Phase 2 — Capability and permission center

- [x] Implement device/RAM/thermal/storage capability snapshot.
- [x] Detect Usage Access and All Files Access without requesting them at launch.
- [x] Add truthful unavailable/unsupported states.
- [x] Add unit tests and pass CI.
- [x] Physically install and render the snapshot on the target phone.
- [x] Compare RAM and storage values with Android/OPPO system information.
- [x] Validate thermal status/headroom interpretation against official Android documentation.
- [x] Correct misleading thermal wording and add regression tests.
- [x] Build and CI-verify the next stable-signed checkpoint.
- [x] Install it over the current version without uninstalling and inspect corrected thermal wording.
- [x] Add advertised physical RAM alongside kernel-accessible RAM.
- [x] Relabel storage value as the app-data filesystem rather than marketed total storage.
- [x] Physically inspect the new RAM/storage truth labels.
- [x] Identify and correct decimal GB versus binary GiB labelling defect.
- [x] Physically verify corrected GB/GiB rendering.

## Phase 2 — Diagnosis engine v1 (current slice)

- [x] Verify official `ActivityManager.MemoryInfo` and `PowerManager` semantics before implementing rules.
- [x] Define versioned, evidence-first rules for low-memory state, thermal status and battery-voltage data quality.
- [x] Implement the current-snapshot diagnosis report and Compose presentation without score or automatic action.
- [x] Add unit coverage for stable state, memory pressure, thermal severity, data quality and deterministic ordering.
- [x] Pass GitHub Actions lint, unit tests, Android 16 build, signing verification and artifact upload.
- [x] Install the milestone APK over the current stable-signed app and inspect the diagnosis card.
- [x] Physically verify the diagnosis output on the target phone, including the expected unavailable voltage limitation.
- [x] Merge the verified diagnosis-engine v1 checkpoint.

## Phase 2 — CPU activity probe v1 (current slice)

- [x] Verify the Android public-API boundary for device-wide CPU activity and current-thread CPU time.
- [x] Define the read-only `/proc/stat` probe, 250 ms counter window and explicit unavailable state.
- [x] Implement background collection and truthful CPU activity presentation without a diagnosis claim.
- [x] Add parser, counter-delta, invalid-input and unavailable-state unit tests.
- [x] Pass GitHub Actions lint, unit tests, Android 16 build, signing verification and artifact upload.
- [x] Verify the artifact ZIP and re-extract a complete APK after the first handoff file was found truncated.
- [ ] Install the milestone APK over the current stable-signed app and inspect the CPU card.
- [ ] Physically verify whether `/proc/stat` is readable and whether the displayed activity is plausible on the target.
- [ ] Merge the verified CPU activity probe checkpoint.

## Phase 2 — Battery factual snapshot

- [x] Verify official Android 16 battery API semantics and units.
- [x] Implement standard battery broadcast snapshot with optional `BatteryManager` properties.
- [x] Add explicit unavailable states and avoid battery-health/capacity claims.
- [x] Add unit tests for battery conversion, labels and unsupported properties.
- [x] Pass CI build, lint, unit tests, stable certificate verification and artifact upload.
- [x] Install the first milestone APK over the current stable-signed app and inspect the battery card.
- [x] Confirm level, status/source, temperature and charge-counter rendering on the target phone.
- [x] Detect and record the target device's physically implausible `3 mV` voltage result; keep it unverified.
- [x] Implement plausibility gating, source provenance and standard read-only `power_supply` fallback for voltage.
- [x] Build and CI-verify the corrected battery checkpoint APK.
- [x] Install the corrected APK over the current version and re-test the battery voltage path.
- [x] Physically verify explicit unavailable voltage handling when no trusted source exists.
- [x] Physically verify the factual battery snapshot rendering and merge PR #7.
