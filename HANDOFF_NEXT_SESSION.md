# Handoff — Next Session

1. Read all project-state documents and inspect repository `Angel2222522/Android-Device-Control-Center`, branch `main`, current HEAD.
2. Phase 1 foundation passed CI and physical Android 16 installation/launch.
3. Phase 2 PR #1 passed CI and merged as `0ddf03c2d9b2f08c364b791ad91eb1d8df3d24e9`.
4. The first update failed because separate CI runners used different ephemeral debug certificates.
5. Stable signing passed two independent CI builds and explicit certificate verification; PR #3 merged as `e1d78cea001f0da8769ccf2db70eb6296407ec20`.
6. User removed the old build once, clean-installed the stable-signed Phase 2 APK and supplied screenshots confirming successful launch and real telemetry rendering.
7. Physical observations: RAM 3.53 GB total / 1.13 GB available / 0.42 GB low threshold / low flag false; storage 101.76 GB total / 28.54 GB available; thermal headroom 1.02 with “serious” label; special accesses not granted.
8. Official semantics are verified: headroom 1.0 is the severe-throttling threshold; 1.02 is at/above it.
9. PR #4 corrected the counterintuitive wording, passed CI run `31313577829`, and merged as `0be0590673d079eb761fc56a288d915059282b91`.
10. The PR #4 APK installed over the existing stable-signed app without uninstalling; corrected wording rendered successfully in screenshot evidence.
11. OEM comparison completed: OPPO shows 4 GB physical + 4 GB storage-backed expansion and 128 GB advertised storage; app APIs returned 3.53 GiB kernel-accessible RAM and 101.76 GiB app-data filesystem.
12. PR #5 added `advertisedMem`, separated advertised/kernel-accessible RAM, corrected storage labels, passed CI run `31314062520`, and merged as `addd31b6b2ded85b56968f8456578a882014e003`.
13. Physical rendering exposed a GB/GiB labelling defect. PR #6 corrected it, passed CI run `31314345629`, and merged as `4b9ed5d14b7f5d08f081e924bf8ec20700912c3c`.
14. The next milestone is PR #7, `Add factual battery snapshot`, currently open as a draft from branch `feature/battery-factual-snapshot`.
15. The first PR #7 CI run `31315368065` failed only at test compilation because of a nullable `Double` assertion; the production code compiled. Corrective commit `9ffe70d5a240c7d4e8e5e64c39bbafb4f3e829eb` was added.
16. Corrected CI run `31315477707` passed lint, unit tests, Android 16 build, stable certificate verification and artifact upload.
17. The single checkpoint APK is ready for physical inspection. Extracted APK SHA-256: `9f8ee46e05793606f33fe1959a7b7e94c4ae8ea246603f02a73954706ee97480`.
18. The first checkpoint was installed over the current app and inspected on the target. Level, state/source, temperature and charge-counter fields rendered; the Android broadcast returned `3 mV (0.003 V)` for voltage, which is physically implausible. Current was `929 μA` while discharging, so it remains a raw sensor value with no inferred direction.
19. The corrected checkpoint was then installed over the current app. It rendered 62%, discharging, 35.8 °C, 762 μA raw current and 2,943 mAh charge counter; it rejected the invalid voltage and displayed explicit unavailable state/provenance.
20. The factual battery snapshot and truthful unavailable-voltage handling are now **PHYSICALLY VERIFIED**. A usable voltage reading is not available on this OPPO target; do not infer one.
21. The correction rejects broadcast voltage outside a conservative plausible envelope, tries only the standard read-only `power_supply` sysfs `voltage_now` path in μV, records the voltage source and shows unavailable when neither source is trustworthy.
22. Corrected commit `fff926687aeec0b4c2e7058c3efe80060a6e0eb` passed CI run `31316180145`; artifact `9038822328`; extracted APK SHA-256 `f8e87d4e2681c2bb329df814b32b2c61bd779b08275a210a39f6af043e8231c`.
23. PR #7 was marked ready and merged as `76eb50e29dee6cb72310c47416961d9b601d9bad`. Direct repository inspection confirmed that `main` now points to this merge commit. The successful code-validation run remains `31316180145`.

24. The first diagnosis-engine v1 implementation checkpoint is on branch `feature/diagnosis-engine-v1`. It evaluates only the Android low-memory flag, current thermal status and battery-voltage data quality.
25. Findings are versioned and explainable: rule ID/version, condition or data-quality type, severity, evidence and explicit limitations. No score, memory ratio, health claim, app attribution, history/baseline or automatic action was added.
26. Unit coverage was added for stable state, low-memory warning, severe/critical thermal states, battery data-quality reporting and deterministic ordering. GitHub Actions passed; APK inspection followed after the CI checkpoint.
27. GitHub Actions run `31317443100` passed lint, unit tests, Android 16 build, stable-certificate verification and artifact upload. Artifact `9039174070` digest: `sha256:64307230b197a03646d5b36838ef948e7db84882d243f448097f14d3faf23070`; extracted APK SHA-256: `eb87948a2b259cca9624724b491634f5ac45de9a2daa6d362f4c6c2911bd9d0b`.
28. The milestone APK was installed over the current stable-signed app without uninstalling and inspected on the target. The diagnosis card rendered correctly: no active memory-pressure finding, one informational unavailable/rejected-voltage finding, evidence/limitations/rule version, and no automatic action.
29. Physical snapshot values included 58% battery, 35.1 °C, 1.04 GiB available RAM, `lowMemory=false`, no current thermal restriction, and 28.26 GiB available app-data storage. These values are dynamic; the target voltage remains unavailable and must not be inferred.
30. PR #8 was marked ready and merged as `d55decfa4d09ee2d662588695eaaa029a50e5583`. Direct repository inspection confirmed `origin/main` and the latest `main` commit point to this merge commit.
31. The next bounded milestone is branch `feature/cpu-activity-probe-v1`. It adds a read-only `/proc/stat` two-sample CPU activity probe over 250 ms, collected off the UI thread, with explicit unavailable behavior.
32. The CPU value is intentionally not a diagnosis, CPU-speed claim or per-app attribution. The implementation has parser/counter-delta/unavailable tests.
33. CPU CI run `31318259964` passed; artifact `9039406371` has ZIP digest `sha256:6acdf78a05671925c625ffccda8a70234780f06363c7c1d866288100288c453`; complete APK SHA-256 is `bfa9492e31f0f37945f20def40c23e240609c768abbf6c862569b06686bb2512`.
34. The first local APK handoff was truncated at 16,711,680 bytes and could not be opened. The original artifact ZIP was intact; a complete 27,096,030-byte APK was re-extracted and passed archive integrity testing. Do not use the truncated file as evidence.
35. The next action is to install only the complete APK over the current stable-signed app and inspect launch plus CPU-card rendering. Do not merge PR #9 before physical verification.

**Current production code:** real RAM/thermal/storage/access snapshot plus a factual battery snapshot and diagnosis-engine v1 on `main`; CPU activity probe v1 is on the checkpoint branch and not yet verified.
**Verified:** diagnosis-engine v1 CI and target inspection, stable certificate, previous clean install/in-place updates, previous telemetry rendering, factual battery snapshot and truthful rejection of the target's invalid voltage.
**First unfinished point:** install and physically inspect the complete CPU APK from artifact `9039406371`. Preserve the target limitation: battery voltage is unavailable and must not be inferred; do not add health/capacity claims.
