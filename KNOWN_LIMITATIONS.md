# Known Limitations

## Current professional branch — 2026-08-10

- The audited code baseline is an implementation checkpoint, not a release claim: it is based on `4d9a1b4` plus pending local hardening/migration changes. It has not received a successful CI build/lint/test run or a complete physical-device verification pass.
- Android's sandbox still prevents universal access to other applications' private data, live processes and exact cross-app resource attribution.
- `QUERY_ALL_PACKAGES` improves package visibility for this local APK, but Android/OEM package visibility and package-manager behavior can still omit or describe packages imperfectly. This is an Android limitation, not a Play Store release decision.
- All Files Access is broad shared-storage access, not root. Private app directories and protected Android paths remain outside the app's guaranteed scope.
- SAF scans are read-only metadata scans. A separately confirmed file action copies a selected file into the app-private durable trash and then attempts to delete the original; provider write permissions, source changes and OEM behavior can still reject that action.
- The SAF picker requests and persists read/write grants when the provider permits them; if persistence fails the URI is not retained across process death and destructive actions remain disabled.
- Storage scanning is bounded: SAF/shared-storage scanning stops at 20,000 entries; the explorer exposes a bounded directory window; metadata can be unknown, stale or unreadable for a provider.
- Exact duplicate detection hashes only same-size candidates, at most 1,000 files and 512 MiB per file. It is a content-confirmation tool for the checked subset, not proof that no duplicate exists outside the subset. Near-duplicate photos are not implemented.
- The private trash has durable metadata, source fingerprints and payload files and is designed to recover across process death. Uncertain recovery is retained for review; restore still depends on the original parent being available and no conflicting destination existing. It is not a general system recycle bin and payloads are not encrypted at rest.
- No automatic deletion, cache clearing, force-stop, CPU governor change or RAM booster is provided. The Android public API does not make those actions reliable or safe for arbitrary applications.
- Usage and network statistics can be delayed, bucketed and OEM-dependent. Per-app network bytes are attributed only to packages with a unique UID; packages sharing a UID are explicitly marked unavailable rather than double-counted.
- A successful App Center inventory refresh records bounded local per-package history: at most 120 samples per package and 4,096 samples globally. It stores package name, returned usage/storage/network values and availability states, not labels, permissions, paths or user content; unavailable Android metrics remain unavailable rather than inferred.
- Battery history is local and bounded to 120 samples. Observed charging duration, indicative equivalent cycles, charge-counter-derived capacity and high-temperature alerts are estimates or observations; they are not authoritative battery health, design capacity, wear, runtime or charging-cause measurements.
- Battery current, voltage, charge counter and energy support varies by vendor. The OPPO target rejected its broadcast voltage as physically implausible and did not expose a trusted sysfs fallback, so usable voltage remains unavailable on that device.
- The diagnosis engine remains a current-snapshot rule set. Local history is used for battery/network presentation, but it is not yet a calibrated cross-domain baseline, causal model or universal anomaly detector.
- CPU activity is a short-window whole-device `/proc/stat` probe when the OEM exposes valid counters; it is not CPU speed, frequency, per-app CPU attribution or proof of a performance problem. Numeric CPU activity was unavailable on the verified OPPO target.
- Background execution is best effort. WorkManager's optional 12-hour snapshot runs only under its constraints and can be delayed by Doze, OEM battery policies or reboot state; there is no guaranteed continuous sampler or notification anomaly channel.
- Room history and the action log remain local but are not encrypted by the application; they rely on Android/device protection. The optional DCCX v1 export uses Android Keystore AES-GCM for the final export file, is bound to the same installation/device key and has no bundled import/decryption UI or cross-device reader. Before the picker result is written, its plaintext report is staged in app-private files and is removed on completion/cancellation/failure or stale-file cleanup after seven days. Plain reports can contain package names, measurements, timestamps and action details and must be treated as sensitive.
- The app declares no Internet permission and has no built-in account, sync or upload path. An export is nevertheless written to the user-selected document provider; if that provider syncs or shares the chosen destination, its own privacy and network behavior applies.
- Clearing telemetry history is explicit and transactional for snapshot/battery/network tables; the action log intentionally keeps the record of the deletion. The current product does not offer selective per-row history editing.
- No local VPN firewall, near-photo matching, reliable orphan/remnant ownership proof, encrypted Room database, home-screen widget or notification-based anomaly alert exists in this checkpoint.
- Charts are local summaries of sampled data, not laboratory-grade time series. Sparse sampling, unavailable fields and long background gaps can make trends incomplete.
- The current professional UI has not yet been accepted on the physical target at large font sizes, with a screen reader, or across the full destructive-action flow. These are release gates, not assumed successes.
- The latest available remote CI run for checkpoint `46247b429e01ad521c99fda5bb996610b01592a6` (`31336454187`) failed at Kotlin compilation in `AppIntelligence.kt`; the audited code baseline's subsequent local fixes and migration changes are not yet covered by CI.

Earlier entries below are historical milestone records and do not override the current-branch status above.

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
- Refresh remains user-triggered point-in-time collection with single-flight/throttle protection. Successful snapshots are persisted locally up to 120 entries; an optional WorkManager path can add constrained periodic snapshots. There is no synchronization or fully calibrated historical diagnosis. If a later refresh fails, the app keeps the last successful snapshot; an initial failure requires a retry.
- CI emulator success cannot prove behavior on the user's physical Android 16/OEM build.
- Zero-cost GitHub Actions is unlimited on standard runners for public repositories; private repositories have quotas.
- APKs signed by different certificates cannot update the same Android application ID. CI debug builds therefore require a stable development-only identity; production uses a separate protected key.

- The current professional UI expands the accepted premium overview into multiple sections, but the new branch has not yet completed CI, accessibility checks or full physical-device acceptance. It must not be called the final product design yet.


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
