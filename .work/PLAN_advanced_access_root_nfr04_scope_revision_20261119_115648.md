# PLAN — Advanced Access via Root (revisi scope NFR-04)

> **Superseded** oleh `.work/PLAN_advanced_access_root_checkpoint_after_wp08_20261119_170244.md` setelah checkpoint Planner wajib pasca WP-08. File ini dipertahankan sebagai history.

## Context
Traffic Light Full saat ini mengikat advanced multi-SIM tracking pada Shizuku. Delivery menambahkan backend Root berbasis libsu untuk compatible `su` (Magisk, KernelSU, KernelSU Next), mempertahankan Shizuku, dan memilih Root → Shizuku → Basic. Root hanya diminta dari aksi eksplisit pengguna.

Revisi 2026-11-19 (D-01): QC WP-01 (`.work/QC-WP-01.md`, verdict FAIL, REWORK-R6/R7/R8) menolak memindahkan requirement bertag NFR-04 secara sepihak. Planner merevisi scope secara eksplisit: **verifikasi matrix perangkat NFR-04 bukan lagi acceptance WP-01**, melainkan gate WP-09/release. WP-01 dibatasi pada pembuktian kombinasi yang benar-benar diuji dan pada akurasi klaim dokumentasi (R8).

PLAN ini menggantikan `.work/PLAN_advanced_access_via_root_magisk_kernelsu_kernelsunext_20261118_170153.md` sebagai rencana aktif. File lama tetap disimpan sebagai history dan memuat Log Executor WP-01 (R1–R5 dan evidence perangkat).

## Scope
### In scope
- Spike pembuktian akses telephony dari root pada kombinasi perangkat yang diuji.
- Domain contract backend-agnostic, Root backend Full-only, adaptasi Shizuku/Basic, coordinator/fallback.
- Migrasi preference, UI generik, tests, flavor isolation.
- Verifikasi matrix perangkat dan dokumentasi kompatibilitas sebagai gate release (WP-09).

### Out of scope
- Modul Magisk/KernelSU, Zygisk, kernel/SELinux changes, daemon root permanen.
- Root pada Play/Lite, mengganti Usage Access, atau mengubah Room schema.

## Requirements
FR-01–FR-10, NFR-01–NFR-06, dan SC-01–SC-07 dari `.work/PRD.md`.

### Scope decision
| ID | Keputusan | Alasan | Dampak |
|---|---|---|---|
| D-01 | Matrix NFR-04 (Magisk, KernelSU, KernelSU Next, Shizuku-only, Basic/no privilege, Android minimum API 28) diverifikasi di WP-09 sebagai blocker release; WP-01 hanya membuktikan kombinasi yang diuji dan tidak boleh mengklaim lebih. | Environment eksekusi tidak memiliki perangkat ADB; matrix multi-root-manager membutuhkan perangkat fisik pengguna. QC menolak memindahkan requirement sendiri, sehingga revisi dilakukan Planner. | WP-01 dapat diajukan ulang ke QC setelah T-042; final acceptance tetap diblokir sampai WP-09 lulus. |

---

## WP-01 — Spike akses telephony melalui root (kombinasi teruji)
Status: done
QC: PASS_WITH_NOTES (2026-11-19 ronde 3, commit `f613dd7`). R1–R5, R6/R7 (via decision D-01: matrix NFR-04 → WP-09), dan R8 (klaim dipersempit) tertutup. Lihat `.work/QC-WP-01-r3.md`. Catatan terbuka: identitas/versi root manager wajib dicatat di WP-09 (T-038); dokumen kontrak `.work/` masih untracked.

File:
- `.work/ROOT-SPIKE.md`
- `app/src/full/` (harness minimal bila diperlukan)

Dependensi:
- Tidak ada

Tugas:
- [x] T-001 Dokumentasikan target platform dan data telephony minimum; tandai Android minimum (minSdk 28) sebagai target yang **belum diverifikasi** dan matrix NFR-04 sebagai milik WP-09 (FR-03).
- [x] T-002 Uji libsu grant dan allowlisted command tanpa prompt saat initialization (FR-01, FR-02, SC-05).
- [x] T-003 Buktikan subscription ID, slot, carrier, dan subscriber ID melalui UID root pada perangkat nyata (FR-03, SC-01).
- [x] T-004 Catat variasi API/transaction, SELinux, exit code, timeout, dan protocol output pada kombinasi yang diuji, tanpa data sensitif (NFR-01, NFR-02).
- [x] T-005 Tetapkan GO/REWORK mekanisme produksi dan fallback (FR-05, NFR-03).
- [x] T-042 Persempit klaim evidence di `.work/ROOT-SPIKE.md` menjadi "lengkap untuk kombinasi yang diuji", pertahankan `dumpsys isub` Android 16 sebagai known gap, dan rujuk matrix NFR-04 ke WP-09 (FR-03, SC-01).

Kriteria terima:
- Prompt hanya dari aksi eksplisit; seluruh `SubscriptionRecord` terbukti pada kombinasi uji (moto g57 power, Android 16 / SDK 36, KernelSU Next) atau blocker direproduksi.
- Command/protocol tidak menerima input pengguna dan tidak mencatat subscriber ID.
- Dokumentasi tidak mengklaim kompatibilitas lintas root manager/OEM/Android minimum; klaim dibatasi pada kombinasi yang diuji.

Risiko:
- Root dibatasi SELinux atau signature transaksi berbeda antar Android/OEM.

Mitigasi:
- WP ini menjadi gerbang backend produksi; Shizuku/Basic tetap tersedia. Variasi lintas manager/OEM/Android dipindahkan ke matrix WP-09/D-01.

### Log WP-01 (Executor) — rework R6–R8 (scope D-01)

Log R1–R5, evidence perangkat attempt 1–8, dan gate CI/handoff QC tersimpan di PLAN sebelumnya: `.work/PLAN_advanced_access_via_root_magisk_kernelsu_kernelsunext_20261118_170153.md`.

Rework R6–R8 (scope D-01) — dokumentasi saja, tidak ada kode aplikasi/test yang diubah:

- T-001: PASS — `.work/ROOT-SPIKE.md` memuat matrix target dengan kolom status teruji/belum teruji; `minSdk 28` dicatat sebagai target, bukan hasil verifikasi; matrix NFR-04 dirujuk ke WP-09.
- T-004: PASS untuk kombinasi teruji — evidence KernelSU Next/Android 16 (SELinux, exit code, timeout, parser, redaksi) dipertahankan; variasi manager/Android lain eksplisit dipindahkan ke WP-09, bukan diklaim.
- T-042: PASS — klaim "device evidence lengkap" dipersempit menjadi "lengkap untuk kombinasi yang diuji"; ditambah tabel cakupan teruji vs belum diuji; `dumpsys isub` Android 16 tetap known gap; known gaps ditambah API 28, Shizuku-only, dan Basic/no-privilege.

Gate/evidence mentah:

```text
$ git diff --check
(no output, exit 0)

$ grep -rn "lengkap" .work/ROOT-SPIKE.md
5:  **Device evidence lengkap untuk kombinasi yang diuji; WP-01 diajukan ulang ke QC.**
205: (historis, ditandai "Catatan historis")
```

- Perubahan hanya file dokumentasi (`ROOT-SPIKE.md`, PLAN lama sebagai penanda superseded, PLAN aktif). Tidak ada source/test/Gradle yang berubah, sehingga unit test modul dan `bash gradlew test` tidak dijalankan pada rework ini; tidak ada gate yang diklaim PASS di luar yang benar-benar dijalankan.
- `README.md` tidak memuat klaim kompatibilitas root, jadi tidak ada klaim universal di dokumentasi pengguna untuk diperbaiki.
- Deviasi: tidak ada.
- Risiko tersisa: matrix NFR-04 masih kosong sampai WP-09 memiliki perangkat Magisk/KernelSU/Shizuku-only/Basic/API 28; tetap blocker release, bukan blocker spike.

### Log WP-01 (QC) — re-audit ronde 3

Verdict: **PASS_WITH_NOTES** → WP-01 `qc → done` sebagai spike. Laporan: `.work/QC-WP-01-r3.md`.

Gate yang dijalankan QC sendiri (bukan salinan Executor):

```text
$ bash gradlew :app:testFullDebugUnitTest --tests 'com.leekleak.trafficlight.spike.*' --rerun-tasks   # run 1
BUILD SUCCESSFUL in 1m 27s
$ bash gradlew :app:testFullDebugUnitTest --tests 'com.leekleak.trafficlight.spike.*' --rerun-tasks   # run 2
BUILD SUCCESSFUL in 1m 18s   (83 actionable tasks: 83 executed)
XML run 1 & 2: RootSpikeProtocolTest tests=40 failures=0 errors=0 skipped=0
               RootSpikeShellRunnerTest tests=1 failures=0 errors=0 skipped=0

$ git diff --stat 67cbc9b..HEAD -- app core .github gradle build.gradle.kts settings.gradle.kts
(empty)  → kode identik dengan commit yang lulus full suite CI 44506300028

$ grep -n "minSdk" app/build.gradle.kts → 31: minSdk = 28
$ grep audit redaksi 19 file Download/traffic-light-root-spike-attempt-*.txt
(no imsiValue=/subscriberIdValue=/carrierNameValue=/rawOutput=)
$ git ls-files | forbidden patterns → none
$ git diff --check → exit 0
```

Temuan yang diteruskan (bukan blocker WP-01): identitas/versi root manager belum ada di protocol evidence (wajib dicatat T-038/WP-09), dan dokumen kontrak `.work/` masih untracked (LOW).



---

## WP-02 — Domain contract privileged access
Status: done

File:
- `app/src/main/java/com/leekleak/trafficlight/integrations/PrivilegedAccess.kt`
- `app/src/test/java/com/leekleak/trafficlight/integrations/PrivilegedAccessTest.kt`

Dependensi:
- WP-01

Tugas:
- [x] T-006 Tambahkan model kind, availability, authorization, state, error, record/snapshot (FR-04, FR-06, NFR-05).
- [x] T-007 Definisikan `PrivilegedBackend` dan `PrivilegedAccessProvider` tanpa tipe backend bocor ke UI (FR-04, NFR-05).
- [x] T-008 Test pemisahan availability dan authorization (FR-01, FR-09).

Kriteria terima:
- Main source set tanpa libsu; contract dapat diimplementasikan Root/Shizuku/Basic.

Risiko:
- Kontrak terlalu mengikuti satu backend.

Mitigasi:
- Batasi pada kebutuhan DataPlanManager dan UI.

---

## WP-03 — Root backend Full-only dengan libsu
Status: done

File:
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/full/java/com/leekleak/trafficlight/integrations/RootPrivilegedBackend.kt`
- test Full terkait

Dependensi:
- WP-01
- WP-02

Tugas:
- [x] T-009 Tambahkan libsu hanya sebagai `fullImplementation` (FR-08, SC-06).
- [x] T-010 Implementasikan availability tanpa grant dan authorization eksplisit (FR-01, FR-02, SC-05).
- [x] T-011 Implementasikan protocol spike dengan timeout, cancellation, allowlist, exit-code check, strict parser (FR-03, NFR-01).
- [x] T-012 Petakan denial/revoke/timeout/failure/malformed tanpa log sensitif (FR-05, FR-09, NFR-02, NFR-03).
- [x] T-013 Test success dan seluruh failure mode (FR-03, FR-05, FR-09).

Kriteria terima:
- Snapshot valid; initialization tidak prompt; tidak ada injection/log sensitif; libsu tidak hadir di Play/Lite.

Risiko:
- Shell sulit di-fake pada host.

Mitigasi:
- Wrapper interface sempit; fake untuk unit test; grant diuji pada perangkat.

---

## WP-04 — Adaptasi Shizuku dan Basic backend
Status: done

File:
- `app/src/main/java/com/leekleak/trafficlight/integrations/ShizukuServicesProvider.kt`
- `app/src/full/java/com/leekleak/trafficlight/integrations/ShizukuServicesProviderImpl.kt`
- `app/src/play/java/com/leekleak/trafficlight/integrations/ShizukuServicesProviderImpl.kt`
- `app/src/lite/java/com/leekleak/trafficlight/integrations/ShizukuServicesProviderImpl.kt`
- test integrations

Dependensi:
- WP-02

Tugas:
- [x] T-014 Bungkus Shizuku existing sebagai backend tanpa mengubah binder semantics (FR-07).
- [x] T-015 Implementasikan Basic backend setara `updateSimDataBasic` (FR-05, SC-04).
- [x] T-016 Pertahankan bind/unbind/callback dengan error generik (FR-07, NFR-03).
- [x] T-017 Test snapshot Shizuku dan Basic reconciliation (FR-07, SC-04, SC-07).

Kriteria terima:
- Shizuku dan Basic existing tetap bekerja; UI tidak memerlukan tipe binder.

Risiko:
- Callback rusak atau plan duplikat.

Mitigasi:
- Pertahankan transaksi; regression test reconciliation.

---

## WP-05 — Coordinator priority dan runtime fallback
Status: done

File:
- `app/src/main/java/com/leekleak/trafficlight/integrations/PrivilegedAccessProviderImpl.kt`
- `app/src/test/java/com/leekleak/trafficlight/integrations/PrivilegedAccessProviderTest.kt`

Dependensi:
- WP-03
- WP-04

Tugas:
- [x] T-018 Implementasikan Root → Shizuku → Basic dengan StateFlow dan Mutex (FR-04, NFR-05, NFR-06).
- [x] T-019 Implementasikan runtime fallback (FR-05, SC-03, SC-04).
- [x] T-020 Serialisasi authorization/enable/refresh dan hormati cancellation (NFR-03, NFR-06).
- [x] T-021 Sinkronkan snapshot ke DataPlanManager tanpa persistent grant (FR-09, NFR-02).
- [x] T-022 Test priority, fallback, concurrency, cancellation, dan no-startup authorization (SC-02–SC-05).

Kriteria terima:
- Root sehat menang; error recoverable fallback; initialization tidak request; tidak ada refresh loop.

Risiko:
- Race callback Shizuku dan lifecycle.

Mitigasi:
- Coordinator tunggal, Mutex, idempotent transition, concurrency tests.

---

## WP-06 — Preference migration, PermissionManager, dan flavor DI
Status: done

File:
- `app/src/main/java/com/leekleak/trafficlight/database/AppPreferenceRepo.kt`
- `app/src/main/java/com/leekleak/trafficlight/model/PermissionManager.kt`
- integration modules Full/Play/Lite
- test preference/permission

Dependensi:
- WP-05

Tugas:
- [x] T-023 Migrasikan intent `shizuku_tracking` ke `advanced_access_enabled`, bukan Root grant (FR-10).
- [x] T-024 Ubah PermissionManager ke state/action generik tanpa prompt lifecycle (FR-02, FR-06, SC-05).
- [x] T-025 Wire Full dan no-op/Basic Play/Lite (FR-08, SC-06).
- [x] T-026 Test migration idempotent, revoke/retry, dan no-request saat construction/update (FR-09, FR-10, SC-05).

Kriteria terima:
- Setup Shizuku existing terjaga; grant runtime-only; Play/Lite compile tanpa libsu.

Risiko:
- Migrasi memicu prompt atau menonaktifkan setup lama.

Mitigasi:
- Pisahkan enabled intent dari authorization; regression tests.

---

## WP-07 — UI advanced access generik
Status: done

File:
- DataPlans/History ViewModel dan Compose files
- komponen status Core terkait
- resource strings

Dependensi:
- WP-06

Tugas:
- [x] T-027 Ganti UI state Shizuku-specific dengan advanced state (FR-06, NFR-05).
- [x] T-028 Tambahkan rationale, aksi eksplisit, status Root/Shizuku/Basic, denial/retry/stopped (FR-02, FR-06, FR-09).
- [x] T-029 Pertahankan guidance/fallback Shizuku (FR-07).
- [x] T-030 Tambahkan strings dan accessibility tanpa klaim universal compatibility (NFR-04).
- [x] T-031 Test state/action dan no automatic prompt (SC-05).

Kriteria terima:
- Backend aktif jelas; root hanya diminta setelah aksi; Root aktif tidak menampilkan warning Shizuku; Basic usable.

Risiko:
- UX Shizuku regresi/translation luas.

Mitigasi:
- Reuse layout existing dan batasi copy.

---

## WP-08 — Regression, security, dan flavor isolation
Status: done

File:
- test files `app/src/test/`
- evidence build/dependency pada artifact WP

Dependensi:
- WP-03–WP-07

Tugas:
- [x] T-032 Jalankan tests integrations/preferences/PermissionManager/DataPlan/NetworkUsage (SC-07).
- [x] T-033 Audit allowlist, timeout, cancellation, parser bounds, dan log redaction (NFR-01, NFR-02).
- [x] T-034 Verifikasi Play/Lite tanpa libsu/jalur `su` (FR-08, SC-06).
- [x] T-035 Build/test semua flavor dan catat blocker MockK/ByteBuddy jujur (NFR-03).
- [x] T-036 Test malformed/oversized output dan repeated fallback tanpa loop (FR-05, NFR-01, NFR-06).

Kriteria terima:
- Gates lulus atau blocker bereproduksi; Full menyertakan Root; Play/Lite tidak; tidak ada injection/log sensitif.

Risiko:
- Host test hang menutupi assertion failure.

Mitigasi:
- Task terfokus/XML report; timeout bukan PASS.

---

## WP-09 — Verifikasi perangkat, matrix kompatibilitas, dan dokumentasi
Status: todo

File:
- `.work/ROOT-DEVICE-VERIFICATION.md`
- `.work/ROOT-SPIKE.md`
- dokumentasi pengguna terkait

Dependensi:
- WP-08

Tugas:
- [ ] T-037 Verifikasi Magisk: startup/grant/refresh/deny/revoke/retry (NFR-04, SC-01, SC-05).
- [ ] T-038 Verifikasi KernelSU dan KernelSU Next (NFR-04, SC-01).
- [ ] T-039 Verifikasi Root+Shizuku priority dan fallback (SC-02, SC-03).
- [ ] T-040 Verifikasi Shizuku-only dan perangkat tanpa privilege (NFR-04, FR-07, SC-04).
- [ ] T-041 Dokumentasikan batas kompatibilitas, privasi, disable/retry, troubleshooting (NFR-02, NFR-04).
- [ ] T-043 Verifikasi perangkat pada Android minimum yang didukung (`minSdk 28`); bila perangkat tidak tersedia, catat sebagai blocker release, bukan PASS (NFR-04).
- [ ] T-044 Perbarui klaim kompatibilitas di `.work/ROOT-SPIKE.md`/dokumentasi pengguna dari "kombinasi teruji" menjadi matrix penuh hanya setelah T-037–T-043 memiliki evidence (NFR-04).

Kriteria terima:
- Matrix mencatat OS/Android/root manager/version/OEM/SELinux dan evidence tanpa data sensitif untuk minimal Magisk, KernelSU, KernelSU Next, Shizuku-only, Basic/no privilege, dan Android minimum.
- Item yang belum diuji menjadi blocker release dan tidak diklaim kompatibel.

Risiko:
- Perangkat/root manager tidak tersedia di environment eksekusi; evidence bergantung pada perangkat fisik pengguna.

Mitigasi:
- Jangan generalisasi hasil satu perangkat; tandai blocker acceptance; kumpulkan evidence per manager sebelum menyatakan matrix lengkap.

---

## Delivery Log WP-02–WP-08 (Executor/QC)

- **WP-02 done:** backend-agnostic domain contract + availability/authorization separation tests (`6bc5b15`, test fixes `22ed97e`, `42b2eb0`).
- **WP-03 done:** Full-only libsu root backend, constant allowlist, timeout/cancel, bounded parser, denial/revoke/failure tests (`f11d840`).
- **WP-04 done:** Shizuku adapter, Basic backend, Play/Lite no-op adapters (`d98429b`).
- **WP-05 done:** serialized Root → Shizuku → Basic coordinator, runtime fallback and reconciliation (`0f90c98`).
- **WP-06 done:** `advanced_access_enabled` intent migration, generic PermissionManager, flavor DI (`6035a39`).
- **WP-07 done:** generic advanced-access UI/rationale/state/copy (`282eb6b`).
- **WP-08 done:** cancellation preservation + APK symbol/dependency isolation (`ba30c57`).

QC evidence: GitHub Actions run `44571468132` **PASS**. `build-full-debug` dan `full-test-baseline` sukses; Full/Play/Lite APK dibangun; full test suite dijalankan dua kali; focused privileged/root tests sukses; Play/Lite bebas libsu dan simbol root execution; Full memuat libsu/root backend. Artifact diunduh ke `.artefak/run-44571468132/` (gitignored): 3 APK dan XML summary `178 tests, 0 failures, 0 errors, 0 skipped`. Host/lokal tidak menjalankan build sesuai kontrak.

Known notes: device/runtime matrix bukan diklaim oleh WP-02–WP-08 dan tetap gate WP-09.

---

## Traceability Matrix
| Requirement | WP | Test / Verification |
|---|---|---|
| FR-01 | WP-01, WP-02, WP-03 | availability/auth tests; no-prompt spike |
| FR-02 | WP-01, WP-03, WP-06, WP-07 | no-request tests; manual startup |
| FR-03 | WP-01, WP-03 | parser/backend tests; rooted device evidence (kombinasi teruji) |
| FR-04 | WP-02, WP-05 | priority tests |
| FR-05 | WP-01, WP-03–WP-05, WP-08 | fallback/failure tests |
| FR-06 | WP-02, WP-06, WP-07 | ViewModel/UI tests |
| FR-07 | WP-04, WP-07, WP-09 | Shizuku regression/device |
| FR-08 | WP-03, WP-06, WP-08 | dependency graph/builds |
| FR-09 | WP-02, WP-03, WP-06, WP-07 | denial/revoke/retry tests |
| FR-10 | WP-06 | migration tests |
| NFR-01 | WP-01, WP-03, WP-08 | security/negative tests |
| NFR-02 | WP-01, WP-03, WP-05, WP-08, WP-09 | log/encryption/evidence audit |
| NFR-03 | WP-01, WP-03–WP-05, WP-08 | failure/concurrency/smoke |
| NFR-04 | WP-09 | device matrix (Magisk, KernelSU, KernelSU Next, Shizuku-only, Basic, Android minimum) |
| NFR-05 | WP-02, WP-05, WP-07 | architecture review |
| NFR-06 | WP-05, WP-08 | concurrency/cache tests |
| SC-01 | WP-01, WP-03, WP-09 | rooted multi-SIM device |
| SC-02 | WP-05, WP-09 | priority test/device |
| SC-03 | WP-05, WP-09 | fallback test/device |
| SC-04 | WP-04, WP-05, WP-09 | Basic fallback |
| SC-05 | WP-01, WP-03, WP-05–WP-07, WP-09 | no-prompt tests/manual |
| SC-06 | WP-03, WP-06, WP-08 | dependency/build isolation |
| SC-07 | WP-04, WP-08 | regression suite |

## MANUAL-VERIFY
- Magisk, KernelSU, KernelSU Next grant/deny/revoke/retry dan multi-SIM (WP-09).
- Root+Shizuku priority/live fallback; Shizuku-only; non-root Basic (WP-09).
- OEM/Android/SELinux coverage dan Android minimum API 28 (WP-09).
- Notification service/widget smoke setelah backend failure.
- UX/accessibility dan tidak ada startup superuser prompt.

## Blockers
- WP-03 diblokir hanya oleh WP-02 (`todo`); gate WP-01 sudah `done` sebagai spike (QC PASS_WITH_NOTES, `.work/QC-WP-01-r3.md`).
- Final acceptance diblokir sampai matrix perangkat WP-09 (T-037–T-044) memiliki evidence.
- WP-09 membutuhkan perangkat fisik pengguna (Magisk, KernelSU, KernelSU Next, Shizuku-only, no-privilege, API 28); environment eksekusi tidak menyediakan ADB device. Root manager + versi wajib dicatat dari perangkat, bukan dari konteks (temuan QC MEDIUM).
- Host test sebelumnya hang pada MockK/ByteBuddy external attach di `AppReviewManagerTest`; investigasi WP-08 dan jangan dianggap PASS.
- Dokumen kontrak `.work/` (`AGENTS.md`, `PRD.md`, `Architecture.md`, `Design.md`, `Schema.md`, `Rules.md`, `INDEX.md`) masih untracked sehingga klon bersih tidak dapat memverifikasi kutipan sumber kebenaran; perlu keputusan manusia (LOW).

## Planner Notes
- Revisi D-01 dijalankan atas permintaan manusia setelah QC menolak memindahkan requirement NFR-04 sendiri.
- Tidak ada WP berstatus `done`; tidak ada WP yang dihapus atau direnumber. WP-01 tetap `rework` dan hanya menerima task baru T-042; task baru WP-09 memakai T-043/T-044.
- Log Executor WP-01 tetap dipertahankan pada PLAN sebelumnya; PLAN ini tidak menghapus history.
- INDEX memindai 274 file tetapi 0 simbol Kotlin; targeted source search diizinkan oleh Rules.
- Tidak ada Room schema change.
- UI menunggu contract/coordinator stabil.
- Rerun Planner setelah setiap 3 WP `done` sesuai kontrak, atau bila PRD/arsitektur/kontrak berubah.
