# Project State

**Updated:** 2026-08-09  
**Phase:** 2 — capability and permission center  
**Production code:** real device/battery snapshot collectors, diagnosis-engine v1 and CPU activity probe v1 implementation on the current checkpoint branch

## Completed

- Initial Android 16 API/restriction, Shizuku, competitor and reference implementation research.
- Feature feasibility matrix, proposed architecture, milestones, risk register and placebo/impossible feature rejection list.
- RAM–CPU professional diagnosis locked as a first-class product capability.
- Android 16-only Kotlin/Compose foundation and GitHub Actions verification pipeline.
- Phase 1 physical installation and launch verified on the target Android 16 device.
- Phase 2 collector implemented for RAM totals/threshold/low-memory flag, thermal status/headroom, storage totals, Usage Access and All Files Access state.
- Phase 2 CI passed and PR #1 merged as `0ddf03c2d9b2f08c364b791ad91eb1d8df3d24e9`.
- Agreed product scope consolidated in `PROJECT_SCOPE.md`.
- Ephemeral debug-signature conflict identified from physical installation evidence.
- Stable development signing passed two independent CI builds plus explicit certificate verification and merged in PR #3 as `e1d78cea001f0da8769ccf2db70eb6296407ec20`.
- Stable-signed Phase 2 APK clean-installed and launched successfully on the target phone.
- Screenshot evidence confirms rendering of real RAM, thermal, storage and access-state values.
- Official PowerManager semantics verified: headroom 1.0 is the severe-throttling threshold; 1.02 is at/above that threshold.
- Thermal wording corrected in PR #4 and CI-verified in run `31313577829`; merged as `0be0590673d079eb761fc56a288d915059282b91`.
- Factual battery snapshot implemented in PR #7. It uses the standard Android battery broadcast plus optional `BatteryManager` properties, with explicit unavailable states and no health/optimisation claims. The target's invalid voltage was rejected correctly in the second physical inspection; the truthful battery snapshot milestone is now physically verified, with voltage unavailable on this device.
- PR #7 was merged as `76eb50e29dee6cb72310c47416961d9b601d9bad`; `main` now contains the physically verified battery milestone.

## Current milestone — Diagnosis engine v1 implementation checkpoint

- Implemented a small deterministic, current-snapshot diagnosis engine with versioned rule IDs, finding type, severity, evidence and explicit limitations.
- The first slice evaluates only Android's official low-memory flag, current thermal status and battery-voltage data quality.
- It deliberately has no score, no arbitrary memory ratio, no app-level causal attribution, no battery-health estimate, no history/baseline and no automatic action.
- Unit coverage was added for stable state, low-memory state, severe/critical thermal states, data-quality findings and stable ordering.
- GitHub Actions run `31317443100` passed lint, unit tests, Android 16 build, stable-certificate verification and artifact upload.
- CI artifact `9039174070` has digest `sha256:64307230b197a03646d5b36838ef948e7db84882d243f448097f14d3faf23070`; extracted APK SHA-256 is `eb87948a2b259cca9624724b491634f5ac45de9a2daa6d362f4c6c2911bd9d0b`.
- The artifact was installed over the existing stable-signed app without uninstalling, launched successfully and was inspected on the target OPPO Android 16 phone.
- The diagnosis card rendered the expected current result: no active memory-pressure finding, one informational battery-voltage data-quality finding, explicit evidence and no automatic action.
- Physical screenshot evidence showed `lowMemory=false`, current thermal status without restriction, and the target's voltage remaining unavailable/rejected as untrusted. The displayed RAM, battery and storage values were treated as dynamic snapshot values.
- Diagnosis engine v1 is now PHYSICALLY VERIFIED on the target. PR #8 was merged as `d55decfa4d09ee2d662588695eaaa029a50e5583`, and `main` now contains the milestone.

## Current milestone — CPU activity probe v1 implementation checkpoint

- Added a read-only, capability-probed device-level CPU activity collector using two `/proc/stat` counter samples over a 250 ms window.
- Collection runs off the Compose/UI thread. If procfs is unavailable, malformed or restricted by the OEM/kernel, the snapshot remains explicitly unavailable.
- The displayed value is derived whole-device counter activity, not CPU speed, frequency, temperature, app attribution or a diagnosis finding.
- Added parser, counter-delta, invalid-input and unavailable-state unit coverage.
- GitHub Actions run `31318259964` passed lint, unit tests, Android 16 build, stable-certificate verification and artifact upload.
- The valid artifact is `9039406371`; its ZIP digest is `sha256:6acdf78a05671925c625ffccda8a70234780f06363c7c1d866288100288c453`, and the complete APK payload is 27,096,030 bytes with SHA-256 `bfa9492e31f0f37945f20def40c23e240609c768abbf6c862569b06686bb2512`.
- A first local handoff extraction was truncated and therefore unusable; the original artifact ZIP passed integrity testing and was re-extracted completely. This delivery failure is not physical app verification.
- The complete APK was installed over the existing stable-signed app on the OPPO A60 5G (Android/ColorOS 16.0.5) and launched successfully.
- The CPU card rendered the explicit unavailable state because read-only `/proc/stat` was unavailable or did not return a valid sample. No fabricated CPU percentage was shown. This is a physically verified capability limitation, not an application failure.
- Static APK inspection, installation, launch and CPU-card inspection are complete for this slice; a numeric CPU activity value is not available on this target.

## Physical Phase 2 observations

- RAM: 3.53 GB total, 1.13 GB available, 0.42 GB low-memory threshold; Android did not report low memory.
- Thermal: UI rendered “Σοβαρή θερμική επιβάρυνση” and thermal headroom 1.02.
- Storage: app-visible data filesystem 101.76 GiB total; 28.54 GiB available in first capture.
- OEM comparison: 4 GB physical RAM + 4 GB storage-backed RAM expansion; 128 GB advertised storage.
- Official semantics verified: `advertisedMem` is retail/advertised physical memory, while `totalMem` is kernel-accessible RAM.
- PR #5 adds both RAM values and truthful app-data-filesystem storage labels; CI run `31314062520` passed and merge commit is `addd31b6b2ded85b56968f8456578a882014e003`.
- Usage Access and All Files Access: not granted, as expected; the app did not request them automatically.

## Current gate

Clean installation, stable-signed in-place updating, telemetry rendering, corrected thermal wording and thermal API semantics are all physically verified. Comparison with OEM settings is complete. Physical rendering exposed a GB/GiB labelling defect: binary GiB values were labelled GB. PR #6 corrected the unit system, passed CI run `31314345629`, and merged as `4b9ed5d14b7f5d08f081e924bf8ec20700912c3c`. Corrected physical rendering is now VERIFIED by user screenshots: 4.00 GB (3.73 GiB), 3.53 GiB kernel-accessible, 1.08 GiB available; storage 28.37 GiB available / 101.76 GiB app-data filesystem. The thermal signal remains dynamic and showed 106% in this capture.

The first battery checkpoint was installed and inspected on the OPPO target. Level, status/source, temperature and charge-counter rendering appeared as live values, but the Android battery broadcast returned `3 mV (0.003 V)`, which is physically implausible for a live phone. The corrected checkpoint rejects that value, reports the voltage as unavailable with explicit provenance, and does not fabricate `3000 mV`. The second physical inspection passed this behavior: 62% level, discharging, 35.8 °C, 762 μA raw current and 2,943 mAh charge counter were rendered; values are dynamic and no health/capacity estimate is claimed. The target has no verified voltage source because the standard sysfs fallback was unavailable.

The corrected checkpoint is commit `fff926687aeec0b4c2e7058c3efe80060a6e0eb`, CI run `31316180145`, artifact `9038822328`, extracted APK SHA-256 `f8e87d4e2681c2bb329df814b32b2c61bd779b08275a210a39f6af043e8231c`.

The merge commit is `76eb50e29dee6cb72310c47416961d9b601d9bad`; current `main` was checked directly after merge. The successful validation run remains `31316180145`.

The diagnosis-engine v1 implementation has passed CI and physical inspection on the target phone. PR #8 was merged as `d55decfa4d09ee2d662588695eaaa029a50e5583`; direct `main` inspection confirmed that this is the current HEAD.

The next bounded milestone is CPU activity probe v1. It is deliberately a collector/capability slice, not a CPU diagnosis claim. It must pass CI and target inspection before the source is treated as usable on the OPPO device.

## Verification language

- Matrix `VERIFIED` = feasibility supported by current documentation/reference evidence.
- Implementation `VERIFIED` requires build + tests + inspected result.
- Foundation build pipeline and physical launch are VERIFIED.
- Phase 2 telemetry collection/rendering is PHYSICALLY VERIFIED; corrected thermal presentation is PHYSICALLY VERIFIED.
- Battery snapshot implementation and truthful unavailable-voltage handling are PHYSICALLY VERIFIED. A usable voltage measurement is NOT AVAILABLE on this target; the rejected vendor value is not presented as a measurement.
- The diagnosis-engine v1 implementation, CI and target-device behavior are PHYSICALLY VERIFIED and are now present on `main` after PR #8 merge.
- CPU activity probe v1 is CI-VERIFIED and PHYSICALLY VERIFIED for truthful unavailable-state behavior on the target. The target does not expose a usable `/proc/stat` sample, so numeric CPU activity is NOT AVAILABLE.
- No optimization action exists, and the diagnosis engine executes no action automatically.
- Stable debug signing, clean installation and subsequent in-place update are PHYSICALLY VERIFIED.
