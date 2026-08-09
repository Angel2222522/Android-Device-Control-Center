# Feature Feasibility Matrix

Status means documentation-level feasibility as of 2026-08-09. It does **not** mean implementation verified on the target phone.

| Feature | Standard Android | Special permission | Shizuku | Root | Android 16 limitation | Reliability | Status |
|---|---|---|---|---|---|---|---|
| Storage volume totals/free space | Yes | None | Not needed | Not needed | App-visible volumes only | High | VERIFIED |
| Media category analysis | Yes | Granular media; user may select subset | Limited extra value | Broader paths | Partial photo access possible | High for granted set | VERIFIED |
| Arbitrary shared-file scan | Limited | SAF tree or All Files Access | Can expand some paths | Broad | Private app dirs and `Android/data` restrictions | Medium/OEM | PARTIALLY VERIFIED |
| Largest/old files | Yes in readable scope | Storage grant | May expand scope | Yes | Metadata may be incomplete/provider-dependent | High in scope | VERIFIED |
| Exact duplicates | Yes in readable scope | Storage grant | May expand scope | Yes | I/O/background limits | High | VERIFIED |
| Near-duplicate photos | Yes locally | Media access | Not needed | Not needed | Selected-media scope | Medium; heuristic | VERIFIED |
| Blur/bad-photo suggestions | Yes locally | Media access | Not needed | Not needed | Heuristic only | Medium | EXPERIMENTAL |
| Orphan/remnant detection | Heuristic | All Files/SAF + app visibility | Better access/actions possible | Strongest | Ownership cannot always be proven | Medium | PARTIALLY VERIFIED |
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
| Local per-app firewall | Yes via VpnService | User VPN consent + foreground service | Alternative shell policies possible | iptables/eBPF | Only one VPN; OEM behavior | High for IP/app rules | VERIFIED |
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

## Implementation checkpoint — 2026-08-09

- Local successful snapshot history: implemented in Room, capped at 120 entries, physically accepted on the target; historical baseline/anomaly analysis is not implemented.
- Scoped storage intelligence: implemented as read-only metadata scanning for an explicitly selected SAF folder; physically accepted on the target within provider/device limits.
- Shared-storage scan: implemented behind an explicit optional `MANAGE_EXTERNAL_STORAGE` flow; this is shared-storage metadata access, not root or private app-directory access.
- Exact duplicate detection: not implemented in this milestone. Same-size groups are displayed as candidates only; no content hashing or destructive action exists.
- The table above remains a feasibility matrix. The implementation checkpoint is separate so feasibility status is not mistaken for full product completion.
