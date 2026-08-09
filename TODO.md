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
- [ ] Physically inspect the new RAM/storage truth labels.
