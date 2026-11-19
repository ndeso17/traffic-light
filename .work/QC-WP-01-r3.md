# QC Report — WP-01 (Re-audit ronde 3)

Tanggal: 2026-11-19
Commit diaudit: `f613dd7` (`docs(WP-01): scope NFR-04 device matrix to WP-09 and narrow evidence claim`)
Branch: `ci/full-debug-apk`
PLAN aktif: `.work/PLAN_advanced_access_root_nfr04_scope_revision_20261119_115648.md`
Ronde sebelumnya: `.work/QC-WP-01.md` (FAIL, REWORK R6–R8)

## Verdict: PASS_WITH_NOTES

Rework R6–R8 ditutup dengan cara yang sah:

- **R6/R7** tidak ditutup oleh Executor, melainkan oleh **revisi scope Planner (D-01)** atas instruksi manusia. Ini memenuhi syarat yang ditulis QC sebelumnya ("QC tidak boleh secara sepihak memindahkan requirement bertag NFR-04"); perpindahan requirement sekarang tercatat sebagai keputusan Planner di `.work/PLAN_advanced_access_root_nfr04_scope_revision_20261119_115648.md` baris 27 dan traceability baris 339.
- **R8** ditutup oleh Executor dengan mempersempit klaim dan menambah tabel cakupan teruji vs belum teruji di `.work/ROOT-SPIKE.md`.

WP-01 dapat menjadi `done` sebagai **spike**. Final acceptance/release tetap diblokir oleh WP-09, bukan oleh WP-01.

## Pre-flight

| Pemeriksaan | Hasil | Bukti |
|---|---|---|
| WP `qc` tunggal | PASS | Hanya WP-01 berstatus `qc`; WP-02–WP-09 `todo` (`grep -n "^Status:"`). |
| HEAD vs klaim | PASS | HEAD `f613dd7` adalah commit yang diklaim Executor; tiga file berubah (dua PLAN + ROOT-SPIKE). |
| Perubahan di luar dokumentasi | PASS | `git diff --stat 67cbc9b..HEAD -- app core .github gradle build.gradle.kts settings.gradle.kts` → kosong; tidak ada source/test/Gradle/workflow yang berubah sejak commit `67cbc9b` yang lulus full suite CI. |
| PLAN lama | PASS | Diberi banner **Superseded** dan tetap menyimpan Log R1–R5 serta evidence perangkat; tidak ada WP `done` yang dihapus/direnumber. |
| Kontrak | PASS | `AGENTS.md` tetap menjadi sumber kebenaran tertinggi; artefak ditulis di `.work/`. |

## Gate yang dijalankan QC (bukan salinan Executor)

| Gate | Perintah | Hasil | Bukti |
|---|---|---|---|
| Unit test spike run 1 | `ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk bash gradlew :app:testFullDebugUnitTest --tests 'com.leekleak.trafficlight.spike.*' --console=plain --rerun-tasks` | PASS | `BUILD SUCCESSFUL in 1m 27s`; XML: `RootSpikeProtocolTest` tests=40 failures=0 errors=0 skipped=0, `RootSpikeShellRunnerTest` tests=1 failures=0. |
| Unit test spike run 2 (stability) | perintah sama, eksekusi ulang penuh | PASS | `BUILD SUCCESSFUL in 1m 18s`, `83 actionable tasks: 83 executed`; XML identik: 40/1 test, nol failure/error/skipped. Tidak ada flakiness atau hang. |
| Bukti kode = bukti CI | `git diff --stat 67cbc9b..HEAD` untuk `app`, `core`, `.github`, `gradle` | PASS | Diff kosong ⇒ baseline full suite CI `44506300028` masih berlaku untuk kode saat ini. |
| Anti-ghost test | Perbandingan jumlah `@Test` baseline vs sekarang | PASS | Tidak ada perubahan file test (`git diff --stat 67cbc9b..HEAD -- app/src/test app/src/testFullDebug` kosong), sehingga 41 test adalah himpunan yang sama dengan yang lulus anti-ghost pada audit sebelumnya. |
| Klaim `minSdk 28` | `grep -n "minSdk" app/build.gradle.kts` | PASS | `app/build.gradle.kts:31: minSdk = 28` — angka di dokumen benar. |
| Metadata evidence perangkat | Baca langsung `Download/traffic-light-root-spike-attempt-7-1795079475249.txt` | PASS | `androidRelease=16`, `sdkInt=36`, `model=moto g57 power`, `selinux=Enforcing`, `recordCount=2` — cocok dengan tabel cakupan di dokumen. |
| Redaksi evidence | `grep -lE 'imsiValue=|subscriberIdValue=|carrierNameValue=|rawOutput='` pada 19 file evidence | PASS | Tidak ada satu pun file memuat key nilai sensitif; hanya presence/count/exit-code/kategori. |
| Klaim "README tanpa klaim root" | `grep -niE "root|magisk|kernelsu|shizuku" README.md` | PASS | README hanya menyebut Shizuku, iPerf3, dan permission Internet; tidak ada klaim kompatibilitas root yang perlu dikoreksi. |
| File terlarang tracked | `git ls-files` + pola APK/AAB/JKS/keystore/`local.properties`/`.env`/`graphify-out` | PASS | Tidak ada. |
| Secret scan pada file yang diubah | Grep pola token/kredensial pada `ROOT-SPIKE.md` dan PLAN aktif | PASS | Tidak ditemukan. |
| Diff hygiene | `git diff --check` | PASS | Exit 0, tanpa output. |

Catatan: full `bash gradlew test` tidak dijalankan QC pada host lokal (batas resource/OOM yang sudah tercatat). Otoritasnya adalah dua eksekusi CI pada runner bersih, dan QC membuktikan tidak ada perubahan kode sejak commit tersebut. QC hanya menjalankan suite spike terfokus, dan suite itu selesai tanpa hang.

## Checklist WP-01

| Task | Hasil | Bukti independen QC |
|---|---|---|
| T-001 target platform + data minimum | PASS | `.work/ROOT-SPIKE.md` §Target memuat tabel target **dan** kolom "Status pada WP-01"; `minSdk 28` eksplisit ditandai **belum teruji**; matrix NFR-04 dirujuk ke WP-09. |
| T-002 no-prompt initialization, grant, denial | PASS (diwarisi, diverifikasi ulang) | Kode tidak berubah sejak audit sebelumnya; evidence attempt 1–8 tetap ada di disk dan konsisten (`startupShellRequest=false`, `startupShellCached=false`). |
| T-003 subscription via UID 0 | PASS (diwarisi, diverifikasi ulang) | Evidence attempt 7 dibaca ulang QC: `androidRelease=16`, `sdkInt=36`, provider `recordCount=2`, `shellStatus=root_uid_0`. |
| T-004 SELinux/exit/timeout/protocol/redaksi | PASS untuk kombinasi teruji | Status tabel menyebut "PASS untuk kombinasi teruji"; variasi manager/Android dipindahkan ke WP-09, bukan diklaim. |
| T-005 keputusan GO/REWORK | PASS | Dokumen tetap menyatakan GO untuk desain backend, bukan approval release. |
| T-042 persempit klaim + known gap | PASS | Baris 5 kini "lengkap **untuk kombinasi yang diuji**"; tabel "Cakupan evidence (kombinasi teruji vs belum diuji)" ditambahkan; `dumpsys isub` Android 16 tetap dicatat sebagai known gap; known gaps ditambah API 28, Shizuku-only, Basic. |

## Requirement dan acceptance

| Requirement / kriteria | Hasil | Penilaian |
|---|---|---|
| FR-01, FR-02, SC-05 | PASS | Tidak berubah, dan dokumen tidak menambah klaim runtime baru. |
| FR-03 / SC-01 | PASS untuk kombinasi teruji | Evidence perangkat diverifikasi ulang QC langsung dari file. |
| NFR-01, NFR-02 | PASS | Evidence aman (tanpa nilai sensitif), command allowlist, tanpa input pengguna — tidak ada perubahan kode yang melemahkan. |
| NFR-04 | PASS untuk scope WP-01 (dialihkan sah) | Requirement kini dipetakan ke WP-09 pada traceability Planner; WP-01 tidak lagi mengklaimnya. Ini keputusan Planner D-01 atas instruksi manusia, bukan perpindahan sepihak QC. |
| Acceptance: prompt eksplisit + record terbukti | PASS pada kombinasi uji | Tidak ada regresi bukti. |
| Acceptance: no input/log sensitif | PASS | Audit redaksi ulang atas 19 file evidence bersih. |
| Acceptance: klaim tidak melebihi bukti | PASS (baru) | Inilah isi R8 dan sudah terpenuhi. |

## Temuan

| Severity | Temuan | Dampak |
|---|---|---|
| MEDIUM | Identitas/versi root manager (KernelSU Next) tidak berasal dari protocol evidence, melainkan dari konteks pengguna; harness sengaja tidak merekamnya. Dokumen kini mengakuinya ("versi tidak dicatat harness"), tetapi kolom itu tetap bagian matrix NFR-04. | Tidak memblokir spike; **wajib** dipenuhi T-038/WP-09 dengan mencatat manager + versi dari perangkat, bukan dari ingatan. |
| LOW | Dokumen kontrak `.work/` (`AGENTS.md`, `PRD.md`, `Architecture.md`, `Design.md`, `Schema.md`, `Rules.md`, `INDEX.md`) dan report QC ronde sebelumnya masih untracked, padahal PLAN mengutipnya sebagai sumber kebenaran. | Clone bersih tidak dapat memverifikasi kutipan sumber kebenaran. Sama dengan temuan LOW audit sebelumnya dan belum diputuskan manusia. `graphify-out/` tetap tidak boleh di-stage tanpa keputusan. |
| INFO | Verifikasi NFR-04 kini sepenuhnya bergantung pada WP-09 yang membutuhkan perangkat fisik pengguna. | Konsisten dengan D-01 dan PLAN Blockers; final acceptance tetap diblokir sampai WP-09 lulus. |

## Catatan untuk WP-09

- T-038/T-043 harus mencatat root manager **dan versinya** pada tabel matrix, karena bukti WP-01 tidak memuatnya.
- Parser `dumpsys isub` Android 16 masih `empty_or_malformed` (94 baris, 0 record); jalur ini bukan sumber record, tetapi tetap known gap yang harus dicatat, bukan dihapus.

## Keputusan proses

- WP-01: `qc → done` sebagai spike; bukan approval release.
- WP-03 kini dapat dikerjakan setelah WP-02 (`todo`), sesuai urutan dependensi.
- Final acceptance tetap diblokir WP-09 (matrix perangkat) dan blocker host test MockK/ByteBuddy yang belum dituntaskan WP-08.
- QC tidak mengubah kode aplikasi maupun test.
