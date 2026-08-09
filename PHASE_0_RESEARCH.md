# Phase 0 — Feasibility and Product Direction

**Research date:** 2026-08-09  
**Target:** Android 16 (API 36) first; no root requirement; optional Shizuku.

## Executive conclusion

The product is feasible, but only if it is positioned as an **evidence-based device intelligence and guided-control suite**, not as an omnipotent system optimizer.

Its defensible core should be:

1. local historical telemetry and baselines for this specific device;
2. explainable, confidence-scored diagnosis across storage, thermal, battery, memory and network signals;
3. safe storage intelligence with explicit provenance and preview;
4. progressive permissions and graceful degradation;
5. measured before/after outcomes for every action;
6. an optional Shizuku adapter for shell-level diagnostics/actions that are verified per Android build/OEM.

The strongest differentiator is not “more numbers”. Existing products already show many numbers. It is the chain:

**observation → evidence quality → diagnosis → safe action → measured outcome → personal baseline**.

## Hard platform boundary

A normal third-party app is sandboxed. It cannot reliably:

- read every private app directory;
- read exact CPU/RAM use for every app in real time;
- clear every app cache directly;
- force-stop arbitrary apps;
- change CPU governor/frequency or overclock;
- set true system charging limits on arbitrary phones;
- reconstruct exact per-app battery blame equivalent to privileged `batterystats`;
- run unrestricted continuous sampling in the background.

Shizuku runs with ADB-shell or root identity. In non-root use it inherits the **limited and version/OEM-dependent shell permission set**. Every Shizuku operation therefore needs a capability probe, explicit failure state and device-specific verification. It is not a promise of root-equivalent control.

## Android 16 capability analysis

### Storage

- MediaStore and granular media permissions can analyze permitted media. Android 14+ may grant only selected photos/videos.
- Storage Access Framework (SAF) grants user-selected document/tree access and works without broad storage permission, but has provider-dependent performance and cannot grant unrestricted private app storage.
- `MANAGE_EXTERNAL_STORAGE` grants broad shared-storage access, but not unrestricted access to other apps' private data and remains Google Play policy-restricted. Android 13+ restrictions around `Android/data` remain relevant.
- Exact duplicate detection is reliable within readable files. A two-stage strategy (size → partial fingerprint → full SHA-256) minimizes I/O.
- Perceptual similarity, burst clustering and blur scores are implementable locally, but are heuristic and must show confidence/preview rather than “safe to delete”.

### Memory and CPU

- `ActivityManager.MemoryInfo` supports device-level memory state: available/total memory, threshold and low-memory indication.
- Modern Android restricts cross-process inspection. Per-app real-time RAM, process enumeration and CPU attribution are not a reliable Standard Mode promise.
- Device-wide CPU load/frequencies obtained through `/proc` or `/sys` vary by kernel/OEM access. Treat as probed telemetry, not a universal API contract.
- Android thermal APIs provide current thermal status. Android 16 adds thermal-headroom listeners and CPU/GPU headroom APIs, but hardware support and return quality must be detected.
- Overclock/governor control is root/kernel territory and is rejected.

### Battery

- `BatteryManager` and the sticky battery broadcast can expose level, status, temperature, voltage, plug type and device-supported current/charge/energy properties.
- `CURRENT_NOW` signs, units and sensor quality vary by vendor; unsupported properties must display as unavailable.
- Capacity/health estimation can be built from charge-counter/current integration over multiple qualified sessions, but must be labelled an estimate with sample quality and confidence. A single arbitrary “health” score is rejected.
- Standard Android does not expose full privileged `batterystats` attribution. Shizuku may parse `dumpsys batterystats`, subject to shell permission, output stability and OEM verification.

### Apps and network

- Usage Access enables usage events/stats after explicit Settings grant.
- `StorageStatsManager` and `NetworkStatsManager` can query other apps after user-granted Usage Access, but bucket granularity, latency and OEM behavior prevent a real-time guarantee.
- Foreground/background network state from historical NetworkStats is limited: current APIs aggregate some state dimensions for history queries. A local VPN can observe/filter live flows more precisely.
- Android 11+ package visibility filters installed-app discovery. `QUERY_ALL_PACKAGES` is sensitive and Google Play-restricted, although device-management/security/antivirus core use cases may qualify. Distribution policy needs a separate decision.
- A `VpnService` can implement an on-device, no-server firewall and per-app routing decisions. Android permits only one active VPN service, so it conflicts with the user's normal VPN. Domain attribution is inherently incomplete with encrypted DNS, QUIC, shared IPs/CDNs and certificate pinning; no TLS interception will be used.

### Background execution

- Continuous invisible monitoring is not viable. WorkManager is for deferrable persistent work; foreground services require a visible notification, declared service type and compliance with background-start restrictions.
- Android 15+ applies time limits to some foreground-service types. The product must use adaptive, event-driven, low-frequency sampling and user-initiated diagnostic sessions rather than permanent high-rate polling.

## Competitor analysis

| Product | Strength | Structural gap/opportunity |
|---|---|---|
| SD Maid SE | Best reference for honest storage maintenance, remnants, SAF/Shizuku and safe cleanup | Primarily maintenance/cleaning; opportunity is cross-domain diagnosis and personal baselines |
| Files by Google | Accessible cleanup flows and media suggestions | Limited technical explanation, historical device diagnosis and advanced control |
| AccuBattery / Battery Guru | Charging sessions, current, capacity estimates, alarms | Battery silo; estimates can appear more certain than sensor/sample quality warrants |
| DevCheck / CPU-Z / AIDA64 | Broad live hardware and sensor dashboards | Mostly raw telemetry; weak causal diagnosis and outcome verification |
| GlassWire | Clear data history and alerts | Android platform granularity/latency limits; not a unified device diagnosis system |
| NetGuard | Proven no-root local-VPN firewall; per-app Wi-Fi/mobile policies | Occupies the single VPN slot; domain attribution and OEM VPN behavior limitations |

### Reference-code policy

- **Shizuku API:** MIT; suitable dependency/reference.
- **SD Maid SE:** strong technical reference, but copyleft license must be checked at the exact revision before copying code. Default policy: study behavior and APIs, do not copy implementation.
- **NetGuard:** strong architectural reference for `VpnService`; license must be checked at the exact revision before copying. Default: independent implementation unless the whole product deliberately adopts a compatible license.
- Every future dependency gets a pinned version, source, license and reason in `DECISIONS.md` before adoption.

## Proposed product architecture

Use a single Android application with clear internal modules, not premature multi-module fragmentation.

### Layers

1. **Capability layer** — runtime probes, permission state, OEM/API support, Standard/Shizuku adapters.
2. **Collectors** — storage, memory, thermal/performance, battery, usage/app and network collectors.
3. **Local data layer** — Room for time-series aggregates, sessions, diagnoses and action results; DataStore for settings/permission education state.
4. **Analysis layer** — baselines, anomaly detection, rule evaluation, confidence and evidence quality.
5. **Action layer** — safe intents, file operations, VPN policies and verified Shizuku actions; all with dry-run/preview where relevant.
6. **Presentation layer** — Kotlin, Compose, Material 3; overview, investigation, history, actions and capability/permission center.

### Diagnosis model

Each finding must contain:

- finding ID and versioned rule;
- timestamp/window;
- evidence values and data-quality flags;
- personal-baseline comparison;
- confidence (low/medium/high with reason, not fake precision);
- plausible alternatives and missing evidence;
- reversible recommendations;
- optional action receipt with before/after measurement.

Start with transparent deterministic rules and robust statistics (rolling median/MAD, percentiles, minimum sample rules). Do not introduce ML until a validated use case beats these methods.

### Sampling strategy

- live screen: collect only while visible;
- diagnostic session: user-initiated short high-resolution capture with visible state if needed;
- background: sparse WorkManager snapshots and event-triggered session records;
- raw samples: short retention; daily/hourly aggregates: longer retention;
- battery/network-intensive collectors: adaptive backoff.

### Development without a computer

- GitHub is the canonical repository and source editor/manager accessible from Android.
- GitHub Actions on Linux installs the pinned Android toolchain, builds, runs lint/unit tests and publishes APK/test artifacts.
- Instrumented tests use an Android emulator in CI where stable; physical-device claims remain `UNVERIFIED` until installed and tested on the target phone.
- A public repository gives free standard hosted-runner use. A private repository has a finite free monthly quota, so privacy of source versus permanent zero-cost CI is a product decision.
- Release signing material must never be committed. Initial internal APKs can use CI debug signing; production signing requires a protected secret/keystore workflow.

## Differentiators worth building

1. Explainable “Why is my phone slow now?” investigation with evidence quality.
2. Personal-device baselines and anomaly detection rather than generic thresholds.
3. Action receipts: before, action, after, outcome, and whether the change mattered.
4. Capability truth screen: exactly what the current device/permission mode can and cannot measure.
5. Cross-domain timeline correlating temperature, charging, storage pressure, foreground app and network changes.
6. Safe storage recommendations with provenance, confidence and user-controlled exclusions.

## Features rejected or reframed

| Claim | Decision |
|---|---|
| RAM booster / one-tap speed boost | Reject; Android commonly uses free RAM as cache, and killing processes can increase reload cost |
| CPU boost/overclock/governor without root | Reject as false |
| Exact per-app CPU/RAM live dashboard in Standard Mode | Reject; replace with explicitly available/probed signals |
| Exact universal battery health percentage | Reframe as multi-session estimate with confidence and OEM-native data when verifiable |
| Universal app-cache cleaner | Reject as direct Standard API; use settings deep links or optional verified Shizuku action, not accessibility automation by default |
| “Junk” mass deletion | Reject; every category needs ownership evidence, preview and safe defaults |
| Guaranteed domain firewall | Reframe; reliable IP/flow policy, best-effort domain observations with limitations |
| Permanent invisible monitoring | Reject; adaptive background snapshots plus explicit diagnostic sessions |
| Charging protection toggle on every phone | Reject; monitoring/alarm is standard, true cutoff only on verified OEM/root paths |

## Milestones and verification gates

0. **Research/product contract** — matrix, risks, architecture and user decisions accepted.
1. **Reproducible foundation** — clean CI build, lint/unit tests, APK artifact, state docs.
2. **Capability & permission center** — runtime probes and truthful unavailable states verified on emulator + target phone.
3. **Device overview and telemetry core** — memory, storage totals, battery and thermal snapshots with data-quality tests.
4. **Historical intelligence** — Room schema, aggregation, retention, export/delete, anomaly test fixtures.
5. **Storage intelligence** — scoped scanner, exact duplicate pipeline, safe deletion receipts; large-file and cancellation tests.
6. **Battery intelligence** — sessions, drain/charge analysis and qualified capacity estimates.
7. **App intelligence** — package/usage/storage/network data with visibility and permission degradation.
8. **Performance investigation** — thermal/headroom and user-initiated diagnostic session.
9. **Diagnosis engine v1** — versioned rules, confidence, alternative causes, synthetic and real scenario tests.
10. **Network intelligence** — NetworkStats history/alerts; firewall is a separately gated sub-milestone because it materially changes battery/privacy/VPN UX.
11. **Advanced Mode** — Shizuku pairing, capability probes, narrow verified operations, reboot/offline states.
12. **Measured actions and profiles** — only actions already verified; profiles are transparent bundles, never magic modes.
13. **Professional UX/accessibility/performance** — complete states, themes, screen-reader and low-overhead profiling.
14. **Hardening/release** — physical Android 16 matrix, process death, upgrades, backup/export, security review, signed APK.

No milestone is complete merely because it compiles. Emulator-tested, target-device-tested and untestable claims are tracked separately.

## Primary risks

| Risk | Likelihood/impact | Mitigation |
|---|---|---|
| OEM/API variability | High/High | Capability probes, data quality, physical-device verification, no universal claims |
| Background collector becomes the drain | Medium/High | Sparse sampling, adaptive backoff, profiling and self-overhead budget |
| Broad permissions block Play distribution | Medium/High | Progressive permissions; separate distribution flavor if required; policy review before implementation |
| Shizuku breaks across releases/OEMs | High/Medium | Narrow adapter, command/version probes, fail closed, Standard Mode remains useful |
| SAF scan is slow or incomplete | High/Medium | Indexed resumable scanning, cancellation, partial results, benchmark on target device |
| VPN conflicts and battery cost | High/High | Separate opt-in module/mode, disclose single-VPN limitation, benchmark overhead |
| False causal diagnosis | Medium/High | Evidence trace, confidence labels, competing hypotheses, test corpus, never claim certainty from correlation |
| CI-only testing hides device bugs | High/High | APK checkpoints and explicit physical test protocol on user's Android 16 phone |
| Zero-cost private CI quota | Medium/Medium | Public repo or build-budget controls; no paid dependency assumption |
| Scope explosion | High/High | Vertical milestones and hard gates; diagnosis core before breadth |

## Decisions requiring product-owner input

1. **Distribution/source model:** public repository (best guarantee of zero-cost CI) or private repository (source privacy, finite free Actions quota).
2. **Storage permission posture:** seek broad All Files Access for the core cleaner, or make SAF/MediaStore the default and broad access an advanced sideload capability.
3. **Firewall scope:** include local-VPN firewall in the main roadmap despite single-VPN conflict and ongoing battery cost, or keep NetworkStats intelligence first and gate firewall later.
4. **Product identity:** clinical/technical “device doctor” versus calm consumer “control center”. This should be decided before visual design, not before technical foundation.

## Sources

- Android 16 behavior changes: https://developer.android.com/about/versions/16/behavior-changes-16
- Storage and All Files Access: https://developer.android.com/about/versions/11/privacy/storage
- Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- UsageStatsManager: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- StorageStatsManager: https://developer.android.com/reference/android/app/usage/StorageStatsManager
- NetworkStatsManager: https://developer.android.com/reference/android/app/usage/NetworkStatsManager
- BatteryManager: https://developer.android.com/reference/android/os/BatteryManager
- PowerManager thermal APIs: https://developer.android.com/reference/android/os/PowerManager
- Package visibility: https://developer.android.com/training/package-visibility
- Foreground-service restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Foreground-service timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout
- Android VPN guide: https://developer.android.com/develop/connectivity/vpn
- Shizuku API/project: https://github.com/RikkaApps/Shizuku-API
- SD Maid SE project/setup: https://github.com/d4rken-org/sdmaid-se and https://github.com/d4rken-org/sdmaid-se/wiki/Setup
- NetGuard project: https://github.com/M66B/NetGuard
- GitHub Actions billing: https://docs.github.com/billing/managing-billing-for-github-actions/about-billing-for-github-actions

