# Known Limitations

- Android app sandbox prevents universal access to other apps' private data and live processes.
- Package visibility and Google Play policies constrain complete installed-app inventory.
- All Files Access is broad but not equivalent to root and is distribution-policy sensitive.
- Usage/network statistics can be delayed, bucketed and OEM-dependent.
- Cross-app CPU/RAM and precise battery attribution have no universally reliable public API.
- Battery current/capacity sensor support and semantics vary by manufacturer.
- Thermal/headroom APIs depend on hardware support; fine-grained sensor files may be inaccessible. The headroom value is a normalized thermal-envelope signal, not a temperature, and values above 1.0 do not map uniquely to severity levels beyond the severe threshold.
- Shizuku non-root mode is ADB-shell level, restarts after boot and varies by Android/OEM permissions.
- Local VPN firewall occupies Android's single VPN slot and can add battery/latency overhead.
- Domain attribution is incomplete under encrypted DNS, QUIC, CDNs and shared endpoints.
- Background execution restrictions prevent silent continuous high-frequency sampling.
- CI emulator success cannot prove behavior on the user's physical Android 16/OEM build.
- Zero-cost GitHub Actions is unlimited on standard runners for public repositories; private repositories have quotas.
- APKs signed by different certificates cannot update the same Android application ID. CI debug builds therefore require a stable development-only identity; production uses a separate protected key.
