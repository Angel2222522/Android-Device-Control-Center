# Decisions

## D-001 — Product truth model

- **Decision:** Evidence-based intelligence and guided control; no generic “boost” score.
- **Why:** Android already manages memory/processes aggressively and blocks many privileged controls. The product earns trust through provenance, uncertainty and measured outcomes.
- **Rejected:** RAM booster, CPU booster, universal optimizer score.

## D-002 — Platform baseline

- **Decision:** Android 16 / API 36 first. Root is never required. Shizuku is optional.
- **Why:** The user targets a modern Android 16 device and does not want quality diluted for old versions.

## D-003 — Native stack proposal

- **Decision:** Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, Room, DataStore and WorkManager. Dependency injection only when the object graph warrants it.
- **Why:** Native API coverage, lifecycle correctness and production support.
- **State:** Proposed; becomes locked after Phase 0 approval.

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

## Open product decisions

- Public versus private GitHub repository; repository creation is externally blocked until the user supplies an existing repository.
- SAF/MediaStore-first versus broad All Files Access posture.
- Local-VPN firewall in main roadmap versus later optional module.
- Brand personality and visual direction.
