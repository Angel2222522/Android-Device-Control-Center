# Known Limitations

- Android app sandbox prevents universal access to other apps' private data and live processes.
- Package visibility and Google Play policies constrain complete installed-app inventory.
- All Files Access is broad but not equivalent to root and is distribution-policy sensitive.
- Usage/network statistics can be delayed, bucketed and OEM-dependent.
- Cross-app CPU/RAM and precise battery attribution have no universally reliable public API.
- Battery current/capacity sensor support and semantics vary by manufacturer. A vendor may also expose a battery-broadcast voltage with an unexpected unit or physically invalid value.
- The battery card is a point-in-time snapshot from a sticky broadcast and optional fuel-gauge properties. Vendors may omit, rate-limit or provide noisy current/charge/energy values; this milestone does not create historical drain or health estimates.
- Voltage is sanity-gated and may fall back to the read-only `power_supply` sysfs `voltage_now` file. Sysfs access is OEM/kernel/SELinux-dependent; when neither source is trustworthy, the app deliberately reports voltage as unavailable.
- On the verified OPPO target, the Android voltage value was rejected as physically invalid and the sysfs fallback was unavailable; the app therefore has no usable voltage measurement for this device.
- Diagnosis engine v1 currently evaluates one device snapshot at a time. Local history now exists, but it is not yet used for a historical baseline, confidence model, alternative-cause model or cross-domain correlation.
- The memory rule uses only Android's official `lowMemory` flag; it does not infer pressure from an arbitrary available/total ratio or identify a responsible app.
- The thermal rule reports the current Android status and may include headroom as evidence, but it cannot establish which app, component or workload caused the restriction. Thermal values are dynamic.
- The battery rule reports a voltage data-quality gap when needed; it does not estimate battery health, true capacity, drain direction or remaining runtime.
- Diagnosis engine v1 remains a current-snapshot slice. Historical baseline comparison, confidence calibration, alternative-cause analysis and cross-domain correlation remain unimplemented even though local snapshot storage now exists.
- CPU activity probe v1 is not an Android public system-wide CPU API. It depends on read-only `/proc/stat` access and may be unavailable or vary with the OEM/kernel.
- A returned CPU activity percentage is a short-window aggregate counter derivation for the whole device; it is not CPU speed, frequency, temperature, app attribution or proof of a performance problem. No CPU diagnosis rule uses it yet.
- The logical-processor count is runtime-reported context, not a complete CPU topology or performance-core description.
- CPU activity probe v1 is CI-VERIFIED and physically verified for its explicit unavailable state on the target. The OPPO Android 16/OEM kernel did not expose a usable read-only `/proc/stat` sample, so numeric device-level CPU activity is unavailable on this device.
- Thermal/headroom APIs depend on hardware support; fine-grained sensor files may be inaccessible. The headroom value is a normalized thermal-envelope signal, not a temperature, and values above 1.0 do not map uniquely to severity levels beyond the severe threshold.
- Shizuku non-root mode is ADB-shell level, restarts after boot and varies by Android/OEM permissions.
- Local VPN firewall occupies Android's single VPN slot and can add battery/latency overhead.
- Domain attribution is incomplete under encrypted DNS, QUIC, CDNs and shared endpoints.
- Background execution restrictions prevent silent continuous high-frequency sampling.
- Refresh remains user-triggered point-in-time collection with single-flight/throttle protection. Successful snapshots are now persisted locally up to 120 entries; no synchronization, export/delete workflow or historical diagnosis is implemented yet. If a later refresh fails, the app keeps the last successful snapshot; an initial failure requires a retry.
- CI emulator success cannot prove behavior on the user's physical Android 16/OEM build.
- Zero-cost GitHub Actions is unlimited on standard runners for public repositories; private repositories have quotas.
- APKs signed by different certificates cannot update the same Android application ID. CI debug builds therefore require a stable development-only identity; production uses a separate protected key.

- The current overview UI is not the final product design. The premium redesign is implemented, physically accepted and merged as a first baseline; additional polish remains deferred design debt.


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


## 2026-08-09 — Premium overview v1 physical verification and user acceptance

- The corrective candidate was installed and visually inspected on the OPPO A60 5G through six user-supplied screenshots covering the first viewport, quick metrics, diagnosis, battery details, RAM details and special accesses.
- The previously observed truncation defects were resolved: the capture time rendered as `19:08:00`, the thermal and CPU tile copy remained readable, and both special-access badges were fully visible.
- The first viewport, dark visual system, semantic status hero, metric grid, storage surface, diagnosis disclosure and technical-detail sections rendered coherently on the physical device.
- Truthful unavailable states remained intact: numeric CPU activity was unavailable, battery voltage remained unavailable/rejected, and no health, capacity or optimization claim was introduced.
- The user reviewed the result and accepted it as a good first version, choosing to defer further design refinement to a later phase so functional development can continue.
- Premium overview design/UX v1 is now **PHYSICALLY VERIFIED and ACCEPTED AS A BASELINE**, not declared final. Future polish remains recorded design debt.
- The design PR remains stacked on snapshot lifecycle PR #10. The next unfinished gate is PR #10 physical verification of repeated-tap protection and failure-state preservation; the design milestone must not be described as the final product design.

## 2026-08-09 — Device Intelligence v1 limitations

- Room history contains selected snapshot fields only, is capped at 120 successful entries and has no baseline/anomaly analysis, export/delete UI or cross-device synchronization.
- SAF scanning is provider-dependent, read-only and metadata-only. A scan can encounter unknown sizes, unreadable directories or the 20,000-entry safety limit.
- Shared-storage scanning requires explicit `MANAGE_EXTERNAL_STORAGE` special access and remains limited to readable shared-storage volumes; it is not root and does not expose other applications' private directories.
- Same-size groups are candidates only. The milestone does not hash file contents, confirm duplicates, delete files or move files.
- The user confirmed the merged milestone on the OPPO A60 5G; these boundaries remain product limitations rather than hidden capabilities.


## 2026-08-09 — Unified capability v1 limitations

- App intelligence requires the user to enable Usage Access in Android settings. It uses a seven-day aggregate and visible launcher applications; it is not exact per-app energy, CPU attribution or a complete package inventory.
- Per-app StorageStats may be unavailable or OEM-dependent. The UI keeps that state explicit instead of fabricating a size.
- Exact duplicate detection hashes only after an explicit user action, compares SHA-256 content among same-size candidates and is capped at 20,000 entries / 256 MiB. It does not inspect private app directories, delete files or move files.
- History trends require at least five previous successful local snapshots and compare endpoints plus thermal context. They are not continuous monitoring and do not establish causality.
- These new capabilities are CI-verified but not yet physically verified on the target phone; the final merge gate remains open.
