# Project State

**Updated:** 2026-08-09  
**Phase:** 2 — capability and permission center  
**Production code:** real device/battery snapshot collectors, diagnosis-engine v1 and CPU activity probe v1 on `main`; the target has no numeric device-level CPU activity source

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

## Completed milestone — CPU activity probe v1

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
- PR #9 was physically verified and merged as `679aec0a530d6c8c38742a827d273cd35d359596`; direct repository inspection confirmed that `main` contains the CPU probe milestone.

## Current milestone — Snapshot lifecycle v1 verification checkpoint

- Added a user-triggered refresh action for the device snapshot; collection remains off the Compose/UI thread.
- Only one refresh can run at a time, and completed refreshes are throttled to at most one per second.
- The UI records and displays the last successful capture time.
- A failed refresh preserves the last successful snapshot; an initial failure shows an explicit retryable error state.
- Added unit coverage for refresh gating, state transitions and capture-time presentation.
- GitHub Actions run `31320108150` passed lint, unit tests, Android 16 build, stable-certificate verification and artifact upload.
- Artifact `9039921975` has digest `sha256:d2d5e96e06f51690e9025c53cb3adf6233a9887bf0024d0953eb38706b5f3fa9`.
- Physical target-device inspection of the refresh action, timestamp and failure behavior is still pending. This checkpoint is not yet physically verified or merged.

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

The CPU probe milestone is deliberately a collector/capability slice, not a CPU diagnosis claim. It is merged because the target behavior is truthful and physically verified. The next bounded slice must be selected from the merged `main` state; numeric CPU activity must remain unavailable on this target.

Snapshot lifecycle v1 is the next bounded slice on draft PR #10. It improves the trustworthiness of the existing point-in-time overview without adding history, permissions, background monitoring or new diagnosis claims. The branch is CI-verified; physical inspection remains the merge gate.

## Product design direction — recorded, implementation pending

- The user explicitly raised design quality to a first-class product requirement after reviewing the current overview screenshots.
- The target is a distinctive, premium and emotionally compelling experience that aims above typical Google Play utility apps; “professional” is only the minimum bar.
- The current UI remains a functional technical prototype, not the accepted final design. Large uniform cards, weak hierarchy and long technical paragraphs are recorded design debt.
- The next bounded design milestone is the existing overview only: memorable first viewport, clear primary state, scan-friendly metrics, deliberate visual system, semantic state treatment and progressive disclosure for evidence.
- No fake health/optimization score, fabricated CPU value, unsupported diagnosis or hidden limitation may be introduced for visual effect.
- Implementation, CI and physical visual inspection on the OPPO target are required before the design can be called complete.

## Current milestone — Premium overview design/UX v1 verification checkpoint

- Replaced the prototype overview with a deliberate dark visual system, a first-viewport status hero and compact scan-first metric surfaces.
- Added semantic visual treatment for normal, informational, warning, critical and unavailable states without introducing a health score or unsupported certainty.
- Moved diagnosis evidence and battery/RAM technical fields behind progressive disclosure while preserving the full evidence and provenance.
- Preserved existing collectors, diagnosis rules, refresh lifecycle, truthful unavailable states and the no-automatic-action boundary.
- Added pure presentation-state unit coverage for warning, critical, data-quality-only and neutral overview states.
- Draft PR #11 is on branch agent/premium-overview-design-v1, stacked on PR #10.
- GitHub Actions run 31321637219 passed lint, unit tests, Android 16 build, stable certificate verification and artifact upload.
- Artifact 9040353819 has ZIP digest sha256:18ad6ea8c6eb2287bb15221d28376b9864619b3a354f92d9fdfd30d7a9b38636.
- Complete candidate APK is 27,259,870 bytes with SHA-256 f795026281bdf57a1c1cddb9a943930a8c3e3c50a125376da5f03c5888fb7024.
- ZIP/APK integrity checks passed. Physical visual inspection on the OPPO A60 5G remains pending; this design checkpoint is not yet VERIFIED or merged.

## Verification language

- Matrix `VERIFIED` = feasibility supported by current documentation/reference evidence.
- Implementation `VERIFIED` requires build + tests + inspected result.
- Foundation build pipeline and physical launch are VERIFIED.
- Phase 2 telemetry collection/rendering is PHYSICALLY VERIFIED; corrected thermal presentation is PHYSICALLY VERIFIED.
- Battery snapshot implementation and truthful unavailable-voltage handling are PHYSICALLY VERIFIED. A usable voltage measurement is NOT AVAILABLE on this target; the rejected vendor value is not presented as a measurement.
- The diagnosis-engine v1 implementation, CI and target-device behavior are PHYSICALLY VERIFIED and are now present on `main` after PR #8 merge.
- CPU activity probe v1 is CI-VERIFIED and PHYSICALLY VERIFIED for truthful unavailable-state behavior on the target. The target does not expose a usable `/proc/stat` sample, so numeric CPU activity is NOT AVAILABLE.
- Snapshot lifecycle v1 is CI-VERIFIED on draft PR #10, but its refresh, timestamp and error states are NOT VERIFIED on the target device yet.
- No optimization action exists, and the diagnosis engine executes no action automatically.
- Stable debug signing, clean installation and subsequent in-place update are PHYSICALLY VERIFIED.


## 2026-08-09 — Premium overview v1 physical inspection and corrective iteration

- The first candidate was installed and visually inspected on the OPPO A60 5G through user-supplied screenshots.
- The dark visual system, status hero, metric grid and compact surfaces rendered successfully.
- The first visual checkpoint was **NOT ACCEPTED** because the last-capture time, thermal tile value, CPU supporting text and second special-access badge were visibly truncated.
- The hero wording “Χωρίς ενεργή πίεση” also conflicted with the diagnosis summary showing one informational condition. This was a presentation defect, not a collector, diagnosis or data-integrity defect.
- Corrective code commit: `97b8ac57e0361b6b75f98c6dc41fe2628a772e7d`.
- Corrective changes shorten only compact UI copy, remove lossy ellipsis from important metric values/supporting text, give access-row labels constrained space, use a compact capture-time label and align the informative headline with the diagnosis state.
- GitHub Actions run `31322315102` passed lint, unit tests, Android 16 build, stable-certificate verification and artifact upload.
- Corrective artifact `9040538004` has ZIP digest `sha256:1725f6a07b5980224efa7785c298268bb0ae0c23690ca657b06e7fc8ecda4d34`; extracted APK SHA-256 is `c12e5775519c230281c26d6b75ee0dde49041bf972d85aaa1dafda1787a9bbac`.
- The artifact ZIP and APK passed integrity inspection. Unit report: 30 tests, 0 failures. Lint: 0 errors and 6 existing warnings.
- The corrective candidate is ready for a second physical visual inspection. The premium design remains **CI-VERIFIED but NOT VERIFIED/ACCEPTED** until that inspection and user review pass.
