# QC Report — WP-01 (Re-audit)

> **Status: SUPERSEDED untuk verdict.** Ronde berikutnya: `.work/QC-WP-01-r3.md` (PASS_WITH_NOTES, commit `f613dd7`). Dokumen ini dipertahankan sebagai riwayat FAIL R6–R8.

Tanggal: 2026-11-19  
Commit diaudit: `b0fabbf43f63573a7667848ebb58f8703082a50e`  
Branch: `ci/full-debug-apk`

## Verdict: FAIL — REWORK

Rework R1–R5 lama pada dasarnya telah ditutup: harness Full Debug tersedia, evidence perangkat KernelSU Next membuktikan UID 0 dan dua subscription, denial/revoke/retry fail-safe, 41 test lulus, APK dapat diverifikasi, dan full test suite lulus dua kali di CI.

WP-01 belum dapat menjadi `done` karena quality gate minimum `AGENTS.md` dan NFR-04 belum tercakup sepenuhnya. Evidence perangkat hanya berasal dari satu moto g57 power, Android 16, KernelSU Next. Belum ada evidence Magisk, KernelSU, Shizuku non-root, Basic fallback/perangkat tanpa privilege, maupun Android minimum yang didukung. PLAN sendiri menandai T-001 dan T-004 dengan NFR-04, sedangkan `.work/ROOT-SPIKE.md` mendefinisikan matrix minimum yang belum dipenuhi.

## Pre-flight

| Pemeriksaan | Hasil | Bukti |
|---|---|---|
| WP terkecil berstatus `qc` | PASS | WP-01 adalah satu-satunya WP berstatus `qc`; WP-02 dan seterusnya masih `todo`. |
| HEAD dan remote branch | PASS | HEAD dan `refs/heads/ci/full-debug-apk` sama-sama `b0fabbf`; `main` tetap `eefc2f2`. |
| Commit yang diklaim | PASS | `8bac369` memperkuat protocol, `67cbc9b` menambah baseline CI, `b0fabbf` adalah handoff dokumentasi. |
| Tree hygiene | NOTE | Dokumen kontrak `.work/*.md`, `AGENTS.md`, report ini, dan `graphify-out/` masih untracked. Tracking ref lokal `origin/ci/full-debug-apk` stale, tetapi `git ls-remote` membuktikan remote aktual berada di `b0fabbf`. |
| Identitas commit | PASS | Commit terakhir dibuat oleh `ndeso17 <khilmyfirdausr171102@gmail.com>`; kontrak tidak menetapkan identitas khusus. |

## Gate yang dijalankan QC

| Gate | Perintah / sumber | Hasil | Bukti |
|---|---|---|---|
| Unit test spike via Gradle | `ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk bash gradlew :app:testFullDebugUnitTest --tests 'com.leekleak.trafficlight.spike.*' --console=plain --rerun-tasks` | PASS | `BUILD SUCCESSFUL in 1m 45s`; XML: `RootSpikeProtocolTest` 40/40 dan `RootSpikeShellRunnerTest` 1/1, nol skipped/failure/error. |
| Compile/test independen | K2JVMCompiler + JUnitCore terhadap source saat ini | PASS | `OK (41 tests)`. |
| Stability/full suite | GitHub Actions API, run `44506300028`, commit `67cbc9b` | PASS | Job `full-test-baseline` sukses; run 1 sukses dalam 128s, run 2 sukses dalam 51s, enforcement sukses. Tidak ada timeout/hang yang dinilai PASS. |
| Build Full Debug | GitHub Actions API, run `44506300028` | PASS | `build-full-debug` sukses; assemble, unit test spike, flavor isolation, libsu scope, packaging, dan publish seluruhnya sukses. |
| Konsistensi HEAD vs CI | `git diff 67cbc9b..b0fabbf -- app core .github gradle ...` | PASS | Tidak ada perubahan kode/build/workflow setelah commit yang diuji; selisih hanya dua dokumen WP-01. |
| APK publik/checksum | Download release `root-spike-latest` | PASS | APK 52,119,036 byte; SHA-256 lokal cocok dengan sidecar: `5f72b184365a6fbaa99dbecc2bf771ecb5f3aefaa0b59241e243aa41db1525c0`. Asset diperbarui saat run CI final. |
| Packaging APK | `aapt2 dump xmltree`; pencarian string dex | PASS | Manifest memuat launcher `RootSpikeActivity`; dex memuat `RootSpikeActivity`, `RootSpikeProtocol`, `RootSpikeShellRunner`, libsu `Shell`, serta protocol v2. |
| Anti-ghost protocol | Mutasi expected `protocolVersion=2` → `protocolVersion=999` pada salinan test | PASS | Suite merah tepat pada test protocol version: 41 dijalankan, 1 gagal. |
| Anti-ghost allowlist | Mutasi `requireAllowlisted` agar menerima semua command | PASS | Suite merah tepat pada `non allowlisted command is rejected`: 41 dijalankan, 1 gagal. |
| Test deletion/weakening | Hitung `@Test` per commit dan diff penghapusan | PASS | 27 protocol tests pada `1228363`; 27+1 pada `b4671b9`; 40+1 sejak `8bac369`; tidak ada test function yang dihapus. |
| File terlarang tracked | `git ls-files` untuk APK, keystore/JKS, `local.properties`, `.env`, `graphify-out` | PASS | Tidak ada file terlarang yang tracked. |
| Secret scan scoped | Grep tracked spike/workflow files | PASS | Tidak ditemukan credential literal; `GH_TOKEN: ${{ github.token }}` adalah secret context GitHub Actions, bukan secret hardcoded. |
| Diff hygiene | `git diff --check` | PASS | Tidak ada whitespace error. |

Catatan: full Gradle suite tidak dijalankan ulang pada host lokal karena batas resource/OOM yang telah disepakati. Bukti otoritatif untuk full suite adalah dua eksekusi pada runner CI bersih. QC hanya menjalankan suite spike terfokus; suite tersebut selesai, tidak hang.

## Checklist WP-01

| Task | Hasil | Bukti independen QC |
|---|---|---|
| T-001 — target dan data minimum | PARTIAL / FAIL | `.work/ROOT-SPIKE.md` mendokumentasikan matrix dan empat field minimum, tetapi matrix aktual baru satu Android/OEM/root manager. Android minimum (API 28, sesuai `app/build.gradle.kts`), Magisk, dan KernelSU belum diuji. Karena task membawa NFR-04, dokumentasi target saja belum cukup. |
| T-002 — grant dan no-prompt initialization | PASS | Static audit: `onCreate` hanya memanggil `Shell.getCachedShell`; `Shell.getShell()` hanya berada pada jalur click `runAfterExplicitAction`. Evidence protocol v2 mencatat `startupShellCached=false`, explicit action, grant, denial, dan fresh authorization. |
| T-003 — subscription melalui UID root | PASS | Attempt 1/7 protocol v2: `shellStatus=root_uid_0`, provider sukses, dua record, slot 0 dan 1, carrier hadir, subscriber-ID presence true. Sumber AOSP Android 16 mengonfirmasi `_id`, `sim_id`, dan `carrier_name` sebagai kolom platform. Nilai sensitif tidak direkam. |
| T-004 — API/SELinux/failure/timeout/protocol | PARTIAL / FAIL | Pada KernelSU Next: SELinux Enforcing, provider canonical sukses, `dumpsys isub` malformed, failure exit 1, timeout exit -1, parser bounds dan protocol redaction teruji. Variasi kompatibilitas NFR-04 lintas Magisk/KernelSU/Shizuku/Basic dan Android minimum belum ada. |
| T-005 — keputusan mekanisme/fallback | PASS untuk keputusan spike | GO bersyarat dan fallback Shizuku → Basic terdokumentasi. Ini keputusan desain, bukan bukti runtime fallback; verifikasi runtime tetap berada pada WP berikutnya/WP-09. |

## Requirement dan acceptance

| Requirement / kriteria | Hasil | Penilaian |
|---|---|---|
| FR-01 | PASS untuk spike | Availability tidak disimpulkan dari binary/package `su`; grant dievaluasi setelah aksi eksplisit dan UID 0 menjadi bukti root. |
| FR-02 / SC-05 | PASS | Kode tidak membuka shell saat activity startup; grant path hanya dipanggil dari listener tombol. Evidence pengguna konsisten dengan audit kode. |
| FR-03 / SC-01 | PASS untuk perangkat yang diuji | Dua subscription nyata terbaca melalui UID 0 dengan seluruh field minimum/presence. |
| FR-05 / NFR-03 | PASS untuk keputusan; runtime deferred | Failure, denial, revoke, timeout fail-safe pada harness. Runtime Shizuku/Basic production belum dibuat dalam WP ini dan ditangani WP-03–WP-05/WP-09. |
| NFR-01 | PASS | Command konstan dan allowlisted, tidak menerima input pengguna, timeout/cancel tersedia, exit/output divalidasi, parser dibatasi 512 baris/4096 karakter/8 record. Negative tests dan anti-ghost efektif. |
| NFR-02 | PASS untuk evidence spike | Evidence hanya menyimpan presence/metadata aman; audit empat file utama tidak menemukan IMSI/subscriber-ID value, nilai subscription ID, nama carrier, raw output, command string, atau credential. Enkripsi/HMAC produksi bukan implementasi WP-01. |
| NFR-04 | FAIL | `AGENTS.md` mewajibkan evidence Magisk, KernelSU/KernelSU Next, Shizuku, dan Basic fallback. Baru KernelSU Next pada satu OEM/Android 16 yang tersedia. |
| Acceptance: prompt explicit dan record terbukti | PASS pada perangkat yang diuji | Jalur explicit action dan dua record terbukti. |
| Acceptance: no input/log sensitif | PASS | Static audit, unit tests, anti-ghost, dan audit evidence mendukung klaim. |

## Evidence perangkat yang divalidasi

File utama:

- `/storage/emulated/0/Download/traffic-light-root-spike-attempt-1-1795076285676.txt`
- `/storage/emulated/0/Download/traffic-light-root-spike-attempt-6-1795079431633.txt`
- `/storage/emulated/0/Download/traffic-light-root-spike-attempt-7-1795079475249.txt`
- `/storage/emulated/0/Download/traffic-light-root-spike-attempt-8-1795079521954.txt`

Rangkaian terbukti:

1. Grant: UID 0, SELinux Enforcing, provider dua record.
2. Denial: non-root, tidak menjalankan privileged probes.
3. Retry/grant: fresh shell, UID 0.
4. Grant dicabut di root manager lalu retry: fresh shell kembali non-root.

Audit redaksi tidak menemukan nilai IMSI/subscriber ID, nilai subscription ID, nama carrier, output mentah, command string, binder token, atau credential. String seperti `subscriptionId=0` pada marker adalah **jumlah marker**, bukan nilai subscription ID.

## Temuan

| Severity | Temuan | Dampak |
|---|---|---|
| HIGH | Coverage perangkat wajib NFR-04 belum lengkap: Magisk, KernelSU, Shizuku-only, Basic/no-privilege, dan Android minimum belum diverifikasi. | WP-01 belum memenuhi seluruh tag requirement dan quality gate minimum kontrak; tidak boleh `done`. |
| MEDIUM | `.work/ROOT-SPIKE.md` menyatakan device evidence “lengkap”, tetapi pada baris berikutnya mengakui manager/OEM/Android lain belum diuji. | Klaim harus dipersempit menjadi lengkap hanya untuk kombinasi KernelSU Next yang diuji. |
| MEDIUM | Parser `dumpsys isub` Android 16 belum memahami format perangkat (94 baris, 0 record). | Tidak memblokir provider canonical pada perangkat ini, tetapi fallback diagnostik belum portable dan harus dicakup matrix kompatibilitas. |
| LOW | Kontrak dan report proses masih untracked; `graphify-out/` juga untracked. | Bukti kontrak tidak ikut commit/clone dan working tree tetap noisy. Jangan stage output generator tanpa keputusan pengguna. |
| INFO | CI final menguji commit `67cbc9b`, bukan commit dokumentasi `b0fabbf`; tidak ada perubahan kode/workflow di antara keduanya. | Tidak ada gap binary untuk WP-01. |

## Rework baru

- [ ] **REWORK-R6:** Lengkapi matrix perangkat nyata sesuai `AGENTS.md`/NFR-04: minimal Magisk, KernelSU, KernelSU Next, Shizuku non-root, dan Basic/perangkat tanpa privilege. Catat OS, manager/version, OEM, SELinux, startup, grant/deny/revoke/retry yang relevan tanpa data sensitif.
- [ ] **REWORK-R7:** Verifikasi versi Android minimum yang didukung aplikasi, atau revisi PLAN/requirement secara eksplisit melalui Planner bila coverage tersebut memang dimaksudkan hanya sebagai gate WP-09. QC tidak boleh secara sepihak memindahkan requirement bertag NFR-04 dari WP-01.
- [ ] **REWORK-R8:** Persempit klaim “device evidence lengkap” menjadi “lengkap untuk kombinasi yang diuji” sampai matrix R6/R7 terpenuhi; pertahankan parser `dumpsys isub` Android 16 sebagai known gap.

## Keputusan proses

- WP-01 diubah `qc → rework`.
- Tidak ada kode aplikasi yang diubah oleh QC.
- Tidak ada commit atau push karena verdict FAIL.
- WP-02 tetap terblokir oleh dependensi WP-01 sampai rework kembali diajukan ke QC atau Planner secara eksplisit merevisi scope/dependensi.
