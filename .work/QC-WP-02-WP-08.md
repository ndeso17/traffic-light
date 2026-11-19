# QC Report — WP-02 sampai WP-08

Tanggal: 2026-11-19
HEAD: `ba30c57`
Branch: `ci/full-debug-apk`
GitHub Actions: `44571468132`

## Verdict: PASS

## Gate independen

| Gate | Hasil | Evidence |
|---|---|---|
| Full/Play/Lite debug build | PASS | job `build-full-debug` sukses; APK ketiga flavor ada di `.artefak/run-44571468132/` |
| Full test suite, run 1 | PASS | job `full-test-baseline` sukses |
| Full test suite, run 2 | PASS | job yang sama sukses; tidak ada timeout/hang |
| Focused privileged/root tests | PASS | `testFullDebugUnitTest`, XML artifact |
| XML aggregate | PASS | 25 XML suite, 178 tests, 0 failures/errors/skipped |
| Root packaging | PASS | Full APK memuat `RootPrivilegedBackend`, coordinator, libsu, dan spike harness |
| Flavor isolation | PASS | Play/Lite dependency graph tanpa libsu; dex tanpa `RootPrivilegedBackend`, `RootCommand`, atau libsu Shell |
| Artifact retention | PASS | 3 APK + test report di `.artefak/run-44571468132/`, folder gitignored |
| Host build prohibition | PASS | Gate hanya melalui GitHub Actions; tidak ada build lokal setelah kontrak diperbarui |

## Checklist

- WP-02: domain contract backend-agnostic; availability, authorization, intent terpisah.
- WP-03: libsu Full-only, explicit authorization, allowlist, timeout/cancel, bounded parser, failure mapping.
- WP-04: Shizuku existing dipertahankan sebagai adapter; Basic fallback semua flavor.
- WP-05: priority Root → Shizuku → Basic, Mutex, no prompt during refresh, cancellation tidak ditelan.
- WP-06: intent `advanced_access_enabled` terpisah dari runtime grant; Play/Lite no-root DI.
- WP-07: UI domain-state, rationale lokal/privacy, Root/Shizuku/Basic copy, explicit action.
- WP-08: regression CI dua kali, security audit, dependency+dex isolation, artifacts.

## Notes

- Runtime device scenarios tidak diluluskan oleh laporan ini; semuanya tetap WP-09.
- Root manager version tidak direkam protocol spike; wajib masuk matrix WP-09.
- `dumpsys isub` Android 16 tetap known gap dan provider canonical tetap sumber record.
