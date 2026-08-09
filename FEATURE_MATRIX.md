# Feature Feasibility Matrix

Status means documentation-level feasibility as of 2026-08-10. It does **not** mean implementation verified on the target phone.

| Feature | Standard Android | Special permission | Shizuku | Root | Android 16 limitation | Reliability | Status |
|---|---|---|---|---|---|---|---|
| Storage volume totals/free space | Yes | None | Not needed | Not needed | App-visible volumes only | High | VERIFIED |
| Media category analysis | Yes | Granular media; user may select subset | Limited extra value | Broader paths | Partial photo access possible | High for granted set | VERIFIED |
| Arbitrary shared-file scan | Limited | SAF tree or All Files Access | Can expand some paths | Broad | Private app dirs and `Android/data` restrictions | Medium/OEM | PARTIALLY VERIFIED |
| Largest/old files | Yes in readable scope | Storage grant | May expand scope | Yes | Metadata may be incomplete/provider-dependent | High in scope | VERIFIED |
| Exact duplicates | Yes in readable scope | Storage grant | May expand scope | Yes | I/O/background limits | High | VERIFIED |
| Near-duplicate photos | Not implemented in this checkpoint | Media access | Not needed | Not needed | Selected-media scope | — | NOT IMPLEMENTED |
| Blur/bad-photo suggestions | Not implemented in this checkpoint | Media access | Not needed | Not needed | Heuristic only | — | NOT IMPLEMENTED |
| Orphan/remnant detection | Evidence model not implemented | All Files/SAF + app visibility | Better access/actions possible | Strongest | Ownership cannot always be proven | — | NOT IMPLEMENTED |
| Delete shared files | Yes with ownership/consent rules | SAF/MediaStore consent/All Files | May expand | Yes | User confirmation/path limits | High in scope | VERIFIED |
| Clear other app caches | No direct public API | Accessibility automation possible but undesirable | Possible if shell permission/command survives | Yes | OEM/version instability | Medium/Low | PARTIALLY VERIFIED |
| Total/available RAM | Yes | None | More shell diagnostics | Yes | No issue for device totals | High | VERIFIED |
| Memory pressure/low-memory state | Yes | None | More detail possible | Yes | Coarse public signal | High/Medium | VERIFIED |
| zRAM/swap diagnostics | Probe `/proc`/sysfs | None if readable | Often more readable | Yes | Kernel/OEM restrictions | Medium/Low | PARTIALLY VERIFIED |
| Per-app live RAM | Own app only/restricted | Usage Access does not solve live RSS | Shell tools may provide snapshots | Yes | Cross-process privacy restrictions | Medium with Shizuku | PARTIALLY VERIFIED |
| Device CPU load | No single universal public API | None | Shell snapshots | Yes | `/proc` access varies | Medium/Low | PARTIALLY VERIFIED |
| CPU frequencies/cores | Hardware metadata; frequency paths probed | None | May read more sysfs | Yes | OEM/kernel variation | Medium | PARTIALLY VERIFIED |
| Per-app CPU attribution | Not reliable | None | Shell snapshot/history possible | Yes | No stable public API | Low/Medium | EXPERIMENTAL |
| Thermal status | Yes | None | Extra sensors possible | Yes | Coarse status but stable API | High | VERIFIED |
| Thermal/CPU/GPU headroom | Android 16 APIs where supported | None | Not needed | Not needed | Hardware support/NaN and rate limits | Medium/High | VERIFIED |
| Throttling history | App must sample/derive | Background schedule constraints | More sensors possible | Yes | No universal direct duration API | Medium | PARTIALLY VERIFIED |
| Jank of this app | Yes | None | Not needed | Not needed | Only own rendering | High | VERIFIED |
| Jank of other apps | No general public access | Frame metrics not cross-app | Limited shell diagnostics | Yes | Privacy/security | Low | NOT POSSIBLE |
| Battery level/temp/voltage/status | Yes | None | Not needed | Not needed | Vendor sensor quality | High/Medium | VERIFIED |
| Battery current/charge counter/energy | If device property supported | None | May expose sysfs | Yes | Optional OEM properties/sign conventions | Medium | PARTIALLY VERIFIED |
| Charging speed/time estimate | Derived from supported sensors | None | More readings possible | Yes | Charger/system nonlinear behavior | Medium | VERIFIED |
| Capacity/health estimate | Multi-session estimate | None | OEM/sysfs data may help | Yes | No universal authoritative health API | Medium with confidence | PARTIALLY VERIFIED |
| Charge/discharge sessions | Yes, locally recorded | Notifications/FGS only if needed | Not needed | Not needed | Doze/background gaps | Medium/High | VERIFIED |
| Per-app battery contribution | Not equivalent to Settings | Usage Access gives activity, not exact energy | `dumpsys batterystats` may work | Yes | Output/permission/OEM variance | Medium/Low | PARTIALLY VERIFIED |
| Wi-Fi/mobile totals/history | Yes | Usage Access for broad queries | More shell stats | Yes | Bucket latency/granularity | Medium/High | VERIFIED |
| Per-app data history | Yes after user grants Usage Access | Usage Access | More detail possible | Yes | UID/package mapping; delayed buckets | Medium | VERIFIED |
| Foreground/background data split | Limited/aggregated | Usage Access | Shell stats may help | Yes | History APIs aggregate state in some queries | Low/Medium | PARTIALLY VERIFIED |
| Local per-app firewall | Not implemented in this checkpoint | User VPN consent + foreground service | Alternative shell policies possible | iptables/eBPF | Only one VPN; OEM behavior | — | NOT IMPLEMENTED |
| Domain/host firewall | Best effort via VPN parsing | VPN consent | Not necessary | More options | encrypted DNS/QUIC/CDN ambiguity | Medium/Low | PARTIALLY VERIFIED |
| App list/details | Filtered list | `QUERY_ALL_PACKAGES` subject to policy | Shell package list | Yes | Package visibility | High if authorized | PARTIALLY VERIFIED |
| App versions/install/update dates | For visible apps | Broad visibility if justified | Yes | Yes | Package visibility | High | VERIFIED |
| App permissions/dangerous permissions | For visible packages | Broad visibility if needed | Yes | Yes | Permission groups evolve | High | VERIFIED |
| App last use/screen time | Yes | Usage Access | Yes | Yes | Event retention/OEM accuracy | Medium/High | VERIFIED |
| App storage/cache/data sizes | Via StorageStats | Usage Access | Shell may expand | Yes | Not contents; OEM accuracy | Medium/High | VERIFIED |
| App notification state/count history | Own listener can observe future notifications | Notification Listener Access | Shell settings possible | Yes | No complete past history; sensitive | Medium | PARTIALLY VERIFIED |
| Force-stop arbitrary app | No | No normal grant | Possible with shell if allowed | Yes | Version/OEM and policy risk | Medium | PARTIALLY VERIFIED |
| Restrict app background/battery policy | Guided Settings only | User action | Some `cmd` operations possible | Yes | OEM/version-specific | Medium/Low | PARTIALLY VERIFIED |
| Change CPU governor/overclock | No | No | Generally no | Yes + kernel support | Unsafe/device-specific | Low | NOT POSSIBLE |
| Universal charging cutoff | No | No | OEM-dependent at best | OEM sysfs/root | No standard API | Low | NOT POSSIBLE |
| Personal baseline/anomaly detection | Yes, local | None | Better inputs possible | Not needed | Sampling gaps | High with quality flags | VERIFIED |
| Explainable diagnosis | Yes | Depends on input collectors | Better evidence | Better evidence | Causality remains probabilistic | Medium/High | VERIFIED |
| Before/after action measurement | Yes for measurable signals | Depends on action | Yes | Yes | Confounders/time delay | High with protocol | VERIFIED |

## Current implementation checkpoint — 2026-08-10

The table above remains a feasibility matrix. The inventory below describes the audited code baseline (`4d9a1b4` plus the pending local hardening/migration changes) and deliberately separates code presence from verification:

- **CODE PRESENT** means the capability exists in the current checkout.
- **CI PENDING** means no successful Android build/lint/test result has yet been recorded for this exact head.
- **PHYSICAL PENDING** means the new professional surface has not yet been exercised on the OPPO target; earlier physical acceptance applies only to the older merged checkpoint.

| Area | Current implementation | Current evidence status |
|---|---|---|
| Home and navigation | Material 3 multi-section shell: overview, apps, storage, signals and privacy; system light/dark theme; loading, empty and error states | CODE PRESENT · CI PENDING · PHYSICAL PENDING |
| App center | Visible installed packages including system/disabled/service/no-launcher/no-icon cases; search, filters, sort by name/storage/network/last use/update; detail permissions, storage, Usage Access data, settings, launch and uninstall intents; bounded local per-package usage history | CODE PRESENT · CI PENDING · PHYSICAL PENDING |
| Storage analysis | SAF folder scan and optional shared-storage scan; bounded explorer; categories, largest/oldest files, empty directories, unknown-size/unreadable/truncated reporting | CODE PRESENT · CI PENDING · PHYSICAL PENDING |
| Exact duplicates | SHA-256 confirmation after same-size candidates; bounded to at most 1,000 files and 512 MiB per file; explicit cancellation and skipped-file reporting | CODE PRESENT · CI PENDING · PHYSICAL PENDING |
| File actions | Explicit confirmation, copy-then-delete to app-private durable trash, free-space preflight, source SHA-256 re-check after the copy, no overwrite on restore, persistent recovery metadata and restore action | CODE PRESENT · CI PENDING · PHYSICAL PENDING |
| Battery analytics | Room samples for level/status/temperature/current/charge counter/optional voltage and energy; observed charging time, indicative equivalent cycles/capacity estimate and high-temperature alerts; wear is not claimed | CODE PRESENT · CI PENDING · PHYSICAL PENDING |
| Performance and thermal | RAM/low-memory state, thermal status/headroom, CPU probe with truthful unavailable state, local history and signal charts | CODE PRESENT · CI PENDING · PHYSICAL PENDING |
| Network analytics | 24-hour Wi-Fi/mobile totals, Room history, daily/weekly comparison, Usage Access app data for unique UIDs and explicit shared-UID uncertainty | CODE PRESENT · CI PENDING · PHYSICAL PENDING |
| Privacy, history and automation | No Internet permission/account/sync/ads; access center; Room telemetry history capped at 120 plus app-usage history capped at 120 per package/4,096 globally; action log capped at 120; plain or optional Android-Keystore AES-GCM DCCX export (final file only); explicit telemetry-history deletion that preserves the deletion log; optional WorkManager 12-hour local snapshot | CODE PRESENT · CI PENDING · PHYSICAL PENDING |

Not present in this checkpoint: near-duplicate photo matching, reliable orphan/remnant ownership proof, arbitrary cache clearing, a local VPN firewall, encrypted Room-at-rest storage, a home-screen widget, and notification-based anomaly alerts. An encrypted DCCX export is present, but it does not encrypt the Room database or storage-trash payloads; its plaintext report is staged app-privately until completion/cancellation or cleanup.

The latest available remote verification run for remote checkpoint `46247b429e01ad521c99fda5bb996610b01592a6` (workflow run `31336454187`) failed during Kotlin compilation because of nullable network-byte arguments in `AppIntelligence.kt`. The audited code baseline contains that fix plus later hardening/migration changes, but still requires the final CI gate.
