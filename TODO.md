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
- [ ] Verify a later stable-signed APK updates in place without uninstalling.
- [ ] Establish separate production release-signing strategy without committed secrets.

## Phase 2 — Capability and permission center

- [x] Implement device/RAM/thermal/storage capability snapshot.
- [x] Detect Usage Access and All Files Access without requesting them at launch.
- [x] Add truthful unavailable/unsupported states.
- [x] Add unit tests and pass CI.
- [x] Physically install and render the snapshot on the target phone.
- [ ] Compare RAM and storage values with Android system information.
- [ ] Validate thermal status/headroom interpretation before using it diagnostically.
- [ ] Correct any misleading thermal wording or threshold mapping.
- [ ] Build the next stable-signed checkpoint and verify in-place updating.
