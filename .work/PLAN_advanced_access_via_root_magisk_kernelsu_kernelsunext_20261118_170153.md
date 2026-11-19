# PLAN — Advanced Access via Root

## Context
Traffic Light Full saat ini mengikat advanced multi-SIM tracking pada Shizuku. Delivery menambahkan backend Root berbasis libsu untuk compatible `su` (Magisk, KernelSU, KernelSU Next), mempertahankan Shizuku, dan memilih Root → Shizuku → Basic. Root hanya diminta dari aksi eksplisit pengguna.

## Scope
### In scope
- Spike pembuktian akses telephony dari root.
- Domain contract backend-agnostic, Root backend Full-only, adaptasi Shizuku/Basic, coordinator/fallback.
- Migrasi preference, UI generik, tests, flavor isolation, dan verifikasi perangkat.

### Out of scope
- Modul Magisk/KernelSU, Zygisk, kernel/SELinux changes, daemon root permanen.
- Root pada Play/Lite, mengganti Usage Access, atau mengubah Room schema.

## Requirements
FR-01–FR-10, NFR-01–NFR-06, dan SC-01–SC-07 dari `.work/PRD.md`.

---

## WP-01 — Spike akses telephony melalui root
Status: wip
QC: rework R1–R5 ditangani; evidence perangkat protocol v2 lengkap; handoff QC menunggu hasil job CI `full-test-baseline`.

File:
- `.work/ROOT-SPIKE.md`
- `app/src/full/` (harness minimal bila diperlukan)

Dependensi:
- Tidak ada

Tugas:
- [x] T-001 Dokumentasikan Android/perangkat target dan data telephony minimum (FR-03, NFR-04).
- [ ] T-002 Uji libsu grant dan allowlisted command tanpa prompt saat initialization (FR-01, FR-02, SC-05).
- [ ] T-003 Buktikan subscription ID, slot, carrier, dan subscriber ID melalui UID root pada perangkat nyata (FR-03, SC-01).
- [ ] T-004 Catat variasi API/transaction, SELinux, exit code, timeout, dan protocol output tanpa data sensitif (NFR-01, NFR-02, NFR-04).
- [x] T-005 Tetapkan GO/REWORK mekanisme produksi dan fallback (FR-05, NFR-03).

Kriteria terima:
- Prompt hanya dari aksi eksplisit; seluruh `SubscriptionRecord` terbukti atau blocker direproduksi.
- Command/protocol tidak menerima input pengguna dan tidak mencatat subscriber ID.

Risiko:
- Root dibatasi SELinux atau signature transaksi berbeda antar Android/OEM.

Mitigasi:
- WP ini menjadi gerbang backend produksi; Shizuku/Basic tetap tersedia.

### Log WP-01 (Executor)

- T-001: PASS — target matrix Android/perangkat/root manager/SELinux/multi-SIM dan empat field minimum didokumentasikan di `.work/ROOT-SPIKE.md`.
- T-002: BLOCKED — tidak ada perangkat ADB; grant libsu dan no-prompt initialization belum diuji.
- T-003: BLOCKED — tidak ada perangkat rooted multi-SIM; UID root dan field subscription belum dibuktikan.
- T-004: BLOCKED — format evidence aman serta aspek yang wajib dicatat sudah ditentukan, tetapi variasi API/transaction, SELinux, exit code, timeout, dan protocol aktual belum diuji.
- T-005: PASS — keputusan **REWORK / production backend blocked**, dengan fallback Shizuku → Basic dan syarat keluar terdokumentasi.

Gate/evidence mentah:

```text
$ adb devices -l
List of devices attached
```

- Kriteria prompt eksplisit dan `SubscriptionRecord`: belum terverifikasi karena tidak ada perangkat.
- Quality gate `ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk bash gradlew test`: **FAIL**, 4/4 `AppReviewManagerTest` gagal pada `setUp` (`ExceptionInInitializerError` / ByteBuddy external attach); `BUILD FAILED in 1m 29s`, 151 actionable tasks (4 executed, 147 up-to-date). Ini sesuai blocker host yang sudah tercatat dan tidak diklaim PASS.
- `git diff --check`: PASS (tanpa output).
- Audit dokumen: command harus konstanta allowlist tanpa input pengguna; subscriber ID, output mentah sensitif, command sensitif, dan binder token dilarang dicatat.
- Deviasi: harness Full tidak dibuat karena tanpa perangkat ia tidak menghasilkan evidence dan menambah kode di luar kebutuhan spike saat ini.
- Risiko baru: environment executor tidak menyediakan perangkat; WP-03 tetap diblokir sampai protocol telephony root memiliki evidence nyata atau alternatif disetujui.

### Log WP-01 (Executor) — rework R1–R5

Status WP tetap `wip`: harness sudah ada dan build terverifikasi, tetapi T-002–T-004 tetap `[ ]` karena belum ada evidence perangkat.

Implementasi yang menutup rework:

- REWORK-R1 (harness Full-only): harness berada di source set `fullDebug` saja (`RootSpikeProtocol.kt`, `RootSpikeActivity.kt`, `AndroidManifest.xml` launcher "Root Telephony Spike"), unit test di `app/src/testFullDebug/`. Tidak ada `su` path detection; ketersediaan root hanya dibuktikan dari grant eksplisit.
- REWORK-R2 (allowlist dan fail-safe): empat command read-only sebagai konstanta allowlist, dua probe injeksi kegagalan terpisah (`false`, `sleep 5` dengan deadline 1 detik), timeout + cancel + tutup shell, dan tidak ada input pengguna yang diinterpolasi.
- REWORK-R3 (evidence aman): file `Download/traffic-light-root-spike-attempt-<N>.txt` via MediaStore (API 29+) atau external-files aplikasi (API 28) berisi hanya field aman; attempt, exit code, kategori, jumlah record, dan flag keberadaan — tanpa nilai IMSI, tanpa output mentah, tanpa string command.
- REWORK-R4 (build dan gating): workflow `.github/workflows/full-debug-apk.yml` membangun APK lewat GitHub Actions (batas environment tidak mengizinkan build Gradle lokal), menjalankan unit test spike, membuktikan libsu hanya pada `fullDebug`, dan memverifikasi simbol harness di dalam dex APK.
- REWORK-R5 (dokumentasi): `.work/ROOT-SPIKE.md` memuat matriks target, allowlist, dua jalur baca, format evidence, dan prosedur verifikasi perangkat.

Gate/evidence mentah:

```text
$ curl -s https://api.github.com/repos/ndeso17/traffic-light/actions/runs/44438632587/jobs
JOB 135273685985 completed success
   7 Build Full Debug APK | completed success
   8 Upload Full Debug APK | completed success
   9 Unit test root spike protocol | completed success
   10 Prove Play and Lite do not carry libsu | completed success
   11 Verify libsu reaches only the Full Debug variant | completed success
   12 Verify spike harness is packaged | completed success
   15 Enforce build and test results | completed success

$ curl -s https://api.github.com/repos/ndeso17/traffic-light/actions/runs/44438632587/artifacts
14490296132 trafficlight-full-debug-1228363401f0462ec5b06291ecd0cb98747e4862 18926284

$ curl -s https://api.github.com/repos/ndeso17/traffic-light/check-runs/135273685985/annotations
fullDebug runtime classpath libsu entries: +--- com.github.topjohnwu.libsu:core:6.0.0
play: no libsu dependency
lite: no libsu dependency
dex contains: Lcom/leekleak/trafficlight/spike/RootSpikeActivity;
dex contains: Lcom/leekleak/trafficlight/spike/RootSpikeProtocol;
dex contains: Lcom/topjohnwu/superuser/Shell;
```

- Verifikasi lokal non-Gradle (tanpa Gradle, karena batas resource environment build): `K2JVMCompiler` + `android-37.2/android.jar` + libsu `classes.jar` → harness compile bersih; `JUnitCore` → `OK (27 tests)`.
- Quality gate `bash gradlew test`: **tidak dijalankan ulang**; blocker MockK/ByteBuddy external attach dan dependensi Robolectric tetap tercatat sebagai blocker environment, tidak diklaim PASS.
- Download artifact lewat API anonim ditolak `401 Requires authentication`; APK harus diunduh manual dari halaman run GitHub.

Deviasi dan catatan teknis:

- `fullDebugImplementation` tidak ada saat blok `dependencies` dievaluasi pada AGP 9.5.1 (`Configuration with name 'fullDebugImplementation' not found`), karena source set gabungan varian dibuat pada fase variant creation. Dependensi ditambahkan secara lazy via `configurations.configureEach` yang hanya menyasar `fullDebugImplementation`, sehingga `fullRelease`, `play`, dan `lite` tetap bebas libsu.
- Bentuk lambda `configureEach` harus memakai receiver Gradle Kotlin DSL (`Configuration.() -> Unit`); bentuk parameter eksplisit ditolak dengan `Argument type mismatch` dan sudah dikoreksi.
- Build lokal Gradle sengaja dihindari sesuai batasan environment (OOM dan reboot sebelumnya); otoritas build ada di GitHub Actions.
- Risiko baru: environment executor tidak dapat mengunduh artifact tanpa kredensial GitHub, sehingga pemasangan APK dan pengumpulan evidence perangkat bergantung pada aksi pengguna; T-002–T-004 belum bisa diselesaikan sebelum file evidence `Download/traffic-light-root-spike-attempt-<N>.txt` dibaca.

### Log WP-01 (Executor) — evidence perangkat attempt 1–3

Tiga attempt dijalankan pengguna pada build `1228363` (moto g57 power, Android 16 / SDK 36, host rooted DroidSpace):

```text
attempt=1 shellStatus=non_root          shellStatusInt=0 suVisibleToApp=false  (denial)
attempt=2 shellStatus=root_uid_not_zero shellStatusInt=1 suVisibleToApp=true   probe[uid].exitCode=0 lineCount=0
attempt=3 shellStatus=root_uid_not_zero shellStatusInt=1 suVisibleToApp=true   probe[uid].exitCode=0 lineCount=0
```

Temuan:

- Denial fail-safe: tidak ada crash, fallback `NON_ROOT_SHELL` (`shellStatusInt=0`), evidence tetap tertulis. Prompt hanya muncul setelah tombol ditekan pada kedua skenario.
- Grant berhasil: libsu melaporkan `ROOT_SHELL` (`shellStatusInt=1`). Jadi prompt/deny/grant tidak terjadi saat initialization.
- Bug harness ditemukan: `probe[uid].exitCode=0` dengan `lineCount=0`. Bytecode libsu 6.0.0 (`JobTask.run`) membuktikan `Shell.Job` hanya menampung output bila pemanggil mendaftarkan sink lewat `Shell.Job.to(...)`; tanpa itu output dibuang meski exit code terbaca. Perbaikan: `RootSpikeShellRunner` mendaftarkan sink stdout+stderr sebelum `exec()`.
- Bug kedua: jalur `root_uid_not_zero` tidak menutup shell, sehingga attempt 3 memakai `shellCachedBefore=true` dan revoke berikutnya akan tersamarkan. Perbaikan: shell ditutup di semua jalur (`try/finally`), dan setiap attempt mengevaluasi ulang otorisasi.
- Evidence kini juga mencatat `probe[id].stderrLineCount` agar kegagalan `content query`/`dumpsys` dapat dibedakan tanpa mencatat output mentah.

Gate/evidence mentah (commit `b4671b9`):

```text
$ curl -s https://api.github.com/repos/ndeso17/traffic-light/actions/runs/44447434395/jobs
JOB 135302889564 completed success
   7 Build Full Debug APK | completed success
   8 Upload Full Debug APK | completed success
   9 Unit test root spike protocol | completed success
   10 Prove Play and Lite do not carry libsu | completed success
   11 Verify libsu reaches only the Full Debug variant | completed success
   12 Verify spike harness is packaged | completed success
   15 Enforce build and test results | completed success

$ curl -s https://api.github.com/repos/ndeso17/traffic-light/actions/runs/44447434395/artifacts
14494480735 trafficlight-full-debug-b4671b9fd1be8be9963a230f50f6521f7ea0f5c5 18930020
```

- Unit test lokal non-Gradle: `OK (28 tests)` (27 protocol/evidence + 1 regression capture), harness compile bersih terhadap `android-37.2/android.jar` + libsu.
- TDD untuk bug capture: test ditulis lebih dulu, gagal pada `outputSinksRegistered` dengan implementasi yang meniru perilaku libsu (output dibuang), lalu hijau setelah sink didaftarkan.
- T-002 dan T-003 belum dicentang: grant terbukti, tetapi UID root, SELinux, dan record SIM belum terbaca pada build yang memperbaiki capture. Perlu satu rangkaian attempt baru pada artifact `14494480735`.

---

## WP-02 — Domain contract privileged access
Status: todo
QC: tidak diaudit (2026-11-18) — status masih `todo`, tidak ada Log Executor, file contract/test belum ada, dan dependency WP-01 belum `done`. Lihat `.work/QC-WP-01.md`.

File:
- `app/src/main/java/com/leekleak/trafficlight/integrations/PrivilegedAccess.kt`
- `app/src/test/java/com/leekleak/trafficlight/integrations/PrivilegedAccessTest.kt`

Dependensi:
- WP-01

Tugas:
- [ ] T-006 Tambahkan model kind, availability, authorization, state, error, record/snapshot (FR-04, FR-06, NFR-05).
- [ ] T-007 Definisikan `PrivilegedBackend` dan `PrivilegedAccessProvider` tanpa tipe backend bocor ke UI (FR-04, NFR-05).
- [ ] T-008 Test pemisahan availability dan authorization (FR-01, FR-09).

Kriteria terima:
- Main source set tanpa libsu; contract dapat diimplementasikan Root/Shizuku/Basic.

Risiko:
- Kontrak terlalu mengikuti satu backend.

Mitigasi:
- Batasi pada kebutuhan DataPlanManager dan UI.

---

## WP-03 — Root backend Full-only dengan libsu
Status: todo

File:
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/full/java/com/leekleak/trafficlight/integrations/RootPrivilegedBackend.kt`
- test Full terkait

Dependensi:
- WP-01
- WP-02

Tugas:
- [ ] T-009 Tambahkan libsu hanya sebagai `fullImplementation` (FR-08, SC-06).
- [ ] T-010 Implementasikan availability tanpa grant dan authorization eksplisit (FR-01, FR-02, SC-05).
- [ ] T-011 Implementasikan protocol spike dengan timeout, cancellation, allowlist, exit-code check, strict parser (FR-03, NFR-01).
- [ ] T-012 Petakan denial/revoke/timeout/failure/malformed tanpa log sensitif (FR-05, FR-09, NFR-02, NFR-03).
- [ ] T-013 Test success dan seluruh failure mode (FR-03, FR-05, FR-09).

Kriteria terima:
- Snapshot valid; initialization tidak prompt; tidak ada injection/log sensitif; libsu tidak hadir di Play/Lite.

Risiko:
- Shell sulit di-fake pada host.

Mitigasi:
- Wrapper interface sempit; fake untuk unit test; grant diuji pada perangkat.

---

## WP-04 — Adaptasi Shizuku dan Basic backend
Status: todo

File:
- `app/src/main/java/com/leekleak/trafficlight/integrations/ShizukuServicesProvider.kt`
- `app/src/full/java/com/leekleak/trafficlight/integrations/ShizukuServicesProviderImpl.kt`
- `app/src/play/java/com/leekleak/trafficlight/integrations/ShizukuServicesProviderImpl.kt`
- `app/src/lite/java/com/leekleak/trafficlight/integrations/ShizukuServicesProviderImpl.kt`
- test integrations

Dependensi:
- WP-02

Tugas:
- [ ] T-014 Bungkus Shizuku existing sebagai backend tanpa mengubah binder semantics (FR-07).
- [ ] T-015 Implementasikan Basic backend setara `updateSimDataBasic` (FR-05, SC-04).
- [ ] T-016 Pertahankan bind/unbind/callback dengan error generik (FR-07, NFR-03).
- [ ] T-017 Test snapshot Shizuku dan Basic reconciliation (FR-07, SC-04, SC-07).

Kriteria terima:
- Shizuku dan Basic existing tetap bekerja; UI tidak memerlukan tipe binder.

Risiko:
- Callback rusak atau plan duplikat.

Mitigasi:
- Pertahankan transaksi; regression test reconciliation.

---

## WP-05 — Coordinator priority dan runtime fallback
Status: todo

File:
- `app/src/main/java/com/leekleak/trafficlight/integrations/PrivilegedAccessProviderImpl.kt`
- `app/src/test/java/com/leekleak/trafficlight/integrations/PrivilegedAccessProviderTest.kt`

Dependensi:
- WP-03
- WP-04

Tugas:
- [ ] T-018 Implementasikan Root → Shizuku → Basic dengan StateFlow dan Mutex (FR-04, NFR-05, NFR-06).
- [ ] T-019 Implementasikan runtime fallback (FR-05, SC-03, SC-04).
- [ ] T-020 Serialisasi authorization/enable/refresh dan hormati cancellation (NFR-03, NFR-06).
- [ ] T-021 Sinkronkan snapshot ke DataPlanManager tanpa persistent grant (FR-09, NFR-02).
- [ ] T-022 Test priority, fallback, concurrency, cancellation, dan no-startup authorization (SC-02–SC-05).

Kriteria terima:
- Root sehat menang; error recoverable fallback; initialization tidak request; tidak ada refresh loop.

Risiko:
- Race callback Shizuku dan lifecycle.

Mitigasi:
- Coordinator tunggal, Mutex, idempotent transition, concurrency tests.

---

## WP-06 — Preference migration, PermissionManager, dan flavor DI
Status: todo

File:
- `app/src/main/java/com/leekleak/trafficlight/database/AppPreferenceRepo.kt`
- `app/src/main/java/com/leekleak/trafficlight/model/PermissionManager.kt`
- integration modules Full/Play/Lite
- test preference/permission

Dependensi:
- WP-05

Tugas:
- [ ] T-023 Migrasikan intent `shizuku_tracking` ke `advanced_access_enabled`, bukan Root grant (FR-10).
- [ ] T-024 Ubah PermissionManager ke state/action generik tanpa prompt lifecycle (FR-02, FR-06, SC-05).
- [ ] T-025 Wire Full dan no-op/Basic Play/Lite (FR-08, SC-06).
- [ ] T-026 Test migration idempotent, revoke/retry, dan no-request saat construction/update (FR-09, FR-10, SC-05).

Kriteria terima:
- Setup Shizuku existing terjaga; grant runtime-only; Play/Lite compile tanpa libsu.

Risiko:
- Migrasi memicu prompt atau menonaktifkan setup lama.

Mitigasi:
- Pisahkan enabled intent dari authorization; regression tests.

---

## WP-07 — UI advanced access generik
Status: todo

File:
- DataPlans/History ViewModel dan Compose files
- komponen status Core terkait
- resource strings

Dependensi:
- WP-06

Tugas:
- [ ] T-027 Ganti UI state Shizuku-specific dengan advanced state (FR-06, NFR-05).
- [ ] T-028 Tambahkan rationale, aksi eksplisit, status Root/Shizuku/Basic, denial/retry/stopped (FR-02, FR-06, FR-09).
- [ ] T-029 Pertahankan guidance/fallback Shizuku (FR-07).
- [ ] T-030 Tambahkan strings dan accessibility tanpa klaim universal compatibility (NFR-04).
- [ ] T-031 Test state/action dan no automatic prompt (SC-05).

Kriteria terima:
- Backend aktif jelas; root hanya diminta setelah aksi; Root aktif tidak menampilkan warning Shizuku; Basic usable.

Risiko:
- UX Shizuku regresi/translation luas.

Mitigasi:
- Reuse layout existing dan batasi copy.

---

## WP-08 — Regression, security, dan flavor isolation
Status: todo

File:
- test files `app/src/test/`
- evidence build/dependency pada artifact WP

Dependensi:
- WP-03–WP-07

Tugas:
- [ ] T-032 Jalankan tests integrations/preferences/PermissionManager/DataPlan/NetworkUsage (SC-07).
- [ ] T-033 Audit allowlist, timeout, cancellation, parser bounds, dan log redaction (NFR-01, NFR-02).
- [ ] T-034 Verifikasi Play/Lite tanpa libsu/jalur `su` (FR-08, SC-06).
- [ ] T-035 Build/test semua flavor dan catat blocker MockK/ByteBuddy jujur (NFR-03).
- [ ] T-036 Test malformed/oversized output dan repeated fallback tanpa loop (FR-05, NFR-01, NFR-06).

Kriteria terima:
- Gates lulus atau blocker bereproduksi; Full menyertakan Root; Play/Lite tidak; tidak ada injection/log sensitif.

Risiko:
- Host test hang menutupi assertion failure.

Mitigasi:
- Task terfokus/XML report; timeout bukan PASS.

---

## WP-09 — Verifikasi perangkat dan dokumentasi
Status: todo

File:
- `.work/ROOT-DEVICE-VERIFICATION.md`
- dokumentasi pengguna terkait

Dependensi:
- WP-08

Tugas:
- [ ] T-037 Verifikasi Magisk: startup/grant/refresh/deny/revoke/retry (NFR-04, SC-01, SC-05).
- [ ] T-038 Verifikasi KernelSU dan KernelSU Next (NFR-04, SC-01).
- [ ] T-039 Verifikasi Root+Shizuku priority dan fallback (SC-02, SC-03).
- [ ] T-040 Verifikasi Shizuku-only dan perangkat tanpa privilege (FR-07, SC-04).
- [ ] T-041 Dokumentasikan batas kompatibilitas, privasi, disable/retry, troubleshooting (NFR-02, NFR-04).

Kriteria terima:
- Matrix mencatat OS/root manager/version dan evidence tanpa data sensitif; item belum diuji menjadi blocker release.

Risiko:
- Perangkat/root manager tidak tersedia.

Mitigasi:
- Jangan generalisasi hasil satu perangkat; tandai blocker acceptance.

---

## Traceability Matrix
| Requirement | WP | Test / Verification |
|---|---|---|
| FR-01 | WP-01, WP-02, WP-03 | availability/auth tests; no-prompt spike |
| FR-02 | WP-01, WP-03, WP-06, WP-07 | no-request tests; manual startup |
| FR-03 | WP-01, WP-03 | parser/backend tests; rooted device evidence |
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
| NFR-04 | WP-01, WP-07, WP-09 | device matrix |
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
- Magisk, KernelSU, KernelSU Next grant/deny/revoke/retry dan multi-SIM.
- Root+Shizuku priority/live fallback; Shizuku-only; non-root Basic.
- OEM/Android/SELinux coverage dari WP-01.
- Notification service/widget smoke setelah backend failure.
- UX/accessibility dan tidak ada startup superuser prompt.

## Blockers
- WP-03 diblokir sampai WP-01 membuktikan protocol telephony root atau approved alternative.
- Final acceptance diblokir sampai matrix perangkat WP-09 memiliki evidence.
- Host test sebelumnya hang pada MockK/ByteBuddy external attach di `AppReviewManagerTest`; investigasi WP-08 dan jangan dianggap PASS.

## Planner Notes
- INDEX memindai 274 file tetapi 0 simbol Kotlin; targeted source search diizinkan oleh Rules.
- Tidak ada Room schema change.
- UI menunggu contract/coordinator stabil.
- Rerun Planner setelah setiap 3 WP `done` sesuai kontrak.
