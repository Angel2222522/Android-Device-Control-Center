# Decisions

## D-001 — Product truth model

- **Decision:** Evidence-based intelligence and guided control; no generic “boost” score.
- **Why:** Android already manages memory/processes aggressively and blocks many privileged controls. The product earns trust through provenance, uncertainty and measured outcomes.
- **Rejected:** RAM booster, CPU booster, universal optimizer score.

## D-002 — Platform baseline

- **Decision:** Android 16 / API 36 first. Root is never required. Shizuku is optional.
- **Why:** The user targets a modern Android 16 device and does not want quality diluted for old versions.

## D-003 — Native stack

- **Decision:** Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, Room, DataStore and WorkManager. Dependency injection only when the object graph warrants it.
- **Why:** Native API coverage, lifecycle correctness and production support.
- **State:** Locked and build-verified.

## D-004 — Analysis approach

- **Decision:** Transparent versioned rules plus robust statistics before ML.
- **Why:** Works locally, is testable/explainable and avoids fake “AI”.

## D-005 — Reference implementations

- **Decision:** Shizuku API may be used under MIT. SD Maid SE and NetGuard are references only until exact licenses and compatibility with our chosen product license are recorded.
- **Why:** Avoid accidental copyleft/license contamination.

## D-006 — No accessibility automation by default

- **Decision:** Do not build UI-clicking cache cleaners as a core path.
- **Why:** Fragile across OEMs, high-trust permission and poor professional reliability. Prefer guided Settings actions or verified Shizuku operations.

## D-007 — RAM–CPU diagnosis is a product core

- **Decision:** Professional memory-pressure diagnosis, targeted RAM-management guidance/actions, CPU/thermal diagnosis and joint “why is it slow now?” investigations are first-class product capabilities.
- **Why:** This is the central user value, not an auxiliary dashboard.
- **Boundary:** No claim of changing Android's LMKD, zRAM configuration, kernel scheduler, governor or frequencies without a separately verified privileged/root path.

## D-008 — Temporary engineering identity

- **Decision:** Use `DeviceControlCenter` / `dev.devicecontrolcenter` only as internal placeholders during foundation work.
- **Why:** Branding remains open and must not block technical verification.

## D-009 — GitHub continuous project memory

- **Decision:** Every material verified step, failed verification, scope change and newly discovered limitation must be recorded in the repository state documents during the same checkpoint.
- **Why:** Chat memory is not ground truth. Code, current `main`, test evidence and explicit state files are.

## D-010 — Public repository

- **Decision:** The canonical repository is public: `Angel2222522/Android-Device-Control-Center`.
- **Why:** Permanent zero-cost standard GitHub Actions and transparent development.

## D-011 — Stable public debug signing identity

- **Decision:** CI debug APKs use one repository-pinned development-only keystore. Its credentials are intentionally non-secret Android debug defaults.
- **Why:** Ephemeral GitHub runners otherwise generate different debug certificates, causing Android to reject installation over the previous build.
- **Boundary:** This key must never sign a production release. Release signing will use a separate protected keystore and secrets.

## D-012 — Translate API semantics, not API names

- **Decision:** Present thermal headroom as thermal-envelope use with an explicit 100% severe-throttling threshold.
- **Why:** The official API name “headroom” is counterintuitive because larger returned values mean less safety, not more.
- **Rejected:** Exposing the raw 1.02 value as unexplained “thermal headroom”.

## D-013 — Separate marketed, kernel-accessible and storage-backed memory

- **Decision:** Display advertised physical RAM and kernel-accessible RAM separately. Do not add OEM storage-backed “RAM expansion” to physical RAM.
- **Why:** OPPO reports 4 GB physical + 4 GB storage-backed expansion as an 8 GB combined pool, while Android reports 3.53 GiB accessible to the kernel. These values answer different questions.
- **Storage rule:** A `StatFs` reading of the data filesystem is labelled as app-data capacity, not the device's marketed 128 GB capacity.

## D-014 — Units are part of measurement truth

- **Decision:** Use decimal GB for marketed/advertised capacity and binary GiB for values divided by 2^30. When useful, show both.
- **Why:** The target device exposed that 4,000,000,000 bytes is 4.00 GB but 3.73 GiB. Labelling a GiB calculation as GB is a factual error.

## D-015 — Factual battery snapshot boundaries

- **Decision:** Battery telemetry starts with the standard sticky battery broadcast and optional `BatteryManager` properties. Missing or unsupported properties remain unavailable; they are never replaced with zero or a fabricated estimate.
- **Units:** Temperature is displayed in °C after converting the broadcast's tenths-of-a-degree-Celsius value; voltage in mV; current in μA/mA with the Android sign convention; charge counter in μAh/mAh; energy counter in nWh/Wh.
- **Rejected:** Battery-health percentage, full-capacity claim, universal charging-time claim or optimisation action before a qualified multi-session evidence model exists.
- **Why:** The public APIs expose current state and optional fuel-gauge readings, not an authoritative universal health value. Vendor support and sensor semantics vary.

## D-016 — Reject untrusted battery voltage

- **Decision:** Accept Android's battery-broadcast voltage only inside a conservative plausible phone-battery range. If it fails validation, try the standard read-only `power_supply` sysfs `voltage_now` source in μV and convert it to mV. Preserve the source in the snapshot.
- **Rejected:** Converting an OEM value such as `3` to `3000 mV` by heuristic. If no trusted source is available, show `μη διαθέσιμη` and explain that the value was rejected or absent.
- **Why:** The target OPPO returned `3 mV (0.003 V)` for a live phone. The official Android/AOSP contract describes the broadcast voltage as mV and the standard power-supply voltage file as μV, but the target's observed value violates the expected physical range. Truthful unavailability is safer than a plausible-looking fabricated conversion.
- **Physical result:** The corrected APK rejected the target value and displayed an explicit unavailable state. This handling is verified; a usable voltage measurement is not available on the target device.

## Open product decisions

- SAF/MediaStore-first versus broad All Files Access posture.
- Local-VPN firewall in main roadmap versus later optional module.
- Brand personality and visual direction.
