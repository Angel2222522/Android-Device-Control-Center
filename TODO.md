# TODO

## Product decisions that remain open

- [x] Use a public canonical GitHub repository.
- [ ] User chooses storage-permission posture.
- [ ] User decides whether the local-VPN firewall is core or later optional scope.
- [ ] User chooses initial brand/experience direction.
- [ ] Confirm product/license strategy before borrowing any implementation.
- [ ] Accept or revise proposed milestones.

## Phase 1

- [x] User creates/supplies an existing GitHub repository.
- [x] Create local pinned Android/Gradle project configuration.
- [x] Add minimal native project and CI workflow.
- [x] Add lint, unit-test and APK artifact jobs.
- [x] Publish foundation to repository.
- [x] Run CI and inspect every result.
- [x] Fix and rerun until green.
- [ ] Define physical Android 16 checkpoint procedure.
- [x] Verify debug APK installation and launch on target Android 16 phone.
- [ ] Establish release signing strategy without committing secrets.
- [ ] CI-verify stable development-only debug signing.
- [ ] User uninstalls the final ephemeral-signed foundation APK once.
- [ ] Verify clean installation of the stable-signed APK.
- [ ] Verify a later stable-signed APK updates in place without uninstalling.

## Phase 2 — Capability and permission center

- [x] Implement device/RAM/thermal capability snapshot.
- [x] Implement Usage Access and All Files Access state detection without requesting them at launch.
- [x] Add truthful unavailable/unsupported states.
- [x] Add unit tests for presentation mapping.
- [x] Build, lint and test in CI.
- [ ] Install on target phone and compare displayed data with Android system information.
