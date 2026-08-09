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
- [ ] Install the corrected APK over the current version and re-test the battery voltage path.
- [ ] Physically verify a plausible voltage or explicit unavailable state and then merge PR #7.
