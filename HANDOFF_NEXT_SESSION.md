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

**Current production code:** real RAM/thermal/storage/access snapshot plus a factual battery snapshot; no diagnosis engine.
**Verified:** CI, stable certificate, clean install, in-place updates, previous telemetry rendering, factual battery snapshot and truthful rejection of the target's invalid voltage.
**First unfinished point:** after merging PR #7, begin the first diagnosis-engine slice. Do not treat battery voltage as available on the OPPO target, and do not add health/capacity claims.
