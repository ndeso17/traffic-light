# Repository Contract — Traffic Light

## Scope
Kontrak ini berlaku untuk seluruh pekerjaan agent pada repository Traffic Light.

## Sumber Kebenaran
Urutan prioritas:
1. `AGENTS.md`
2. `.work/PRD.md`
3. `.work/Architecture.md`
4. `.work/Design.md`
5. `.work/Schema.md`
6. `.work/Rules.md`
7. `.work/INDEX.md`
8. Source code dan konfigurasi Gradle
9. `README.md` dan dokumentasi lain

Jika terjadi konflik, ikuti sumber yang lebih tinggi dan catat konflik.

## Dokumen Wajib Baca
Sebelum perencanaan atau implementasi, baca seluruh dokumen `.work/` di atas. Gunakan `.work/INDEX.md` untuk menemukan simbol; buka source spesifik hanya bila diperlukan.

## Artifact dan Pipeline
- Seluruh artifact proses disimpan di `.work/`.
- PLAN wajib dibuat memakai `~/.agents/scripts/work-file PLAN "<request>"`; nama statis `PLAN.md` dilarang.
- Format nama artifact PLAN: `.work/PLAN_<nama_plan>_<YYYYMMDD_HHMMSS>.md`; nama plan dinormalisasi ke lowercase `snake_case`.
- Pipeline: Planner → Executor → QC → Auditor → Reviewer.
- Planner/QC/Auditor/Reviewer tidak menulis kode aplikasi.
- Executor mengerjakan tepat satu WP.
- Status sah: `todo`, `wip`, `qc`, `rework`, `done`.
- Alur normal: `todo → wip → qc → done`; kegagalan QC: `qc → rework → wip → qc`.
- Setiap task memakai ID `T-NNN`, tag requirement, dependency, acceptance criteria, risiko, mitigasi, dan traceability.
- WP `done` tidak boleh dihapus atau direnumber.
- Planner dievaluasi ulang setelah 3 WP selesai atau ketika PRD/arsitektur/kontrak berubah.

## Build dan Testing
**Build dan test di host/lokal dilarang.** Host pengembangan tidak boleh menjalankan `gradlew`
(OOM/reboot dan hasilnya bukan otoritas). Otoritas build, unit test, dan quality gate adalah
GitHub Actions.

Alur wajib:

1. Commit perubahan (satu WP = minimal satu commit).
2. Push ke branch kerja.
3. Tunggu workflow CI selesai; baca job dan annotation.
4. Unduh artifact ke `.artefak/` (gitignored) sebelum mengklaim gate:

```bash
gh run list --branch <branch> --limit 5
gh run download <run-id> -D .artefak/<label>
```

Bukti gate adalah file di `.artefak/` (XML test, APK, log dependency), bukan angka yang
diketik ulang. `.artefak/` tidak boleh di-commit.

Quality gate minimum (dijalankan di CI, bukan host):
1. Unit test modul yang berubah.
2. `test` seluruh varian (baseline penuh), dua kali untuk deteksi flaky.
3. Build seluruh flavor terdampak (`full`, `play`, `lite`).
4. Perubahan root wajib membuktikan Full dapat build serta Play/Lite tidak membawa dependency root.
5. Verifikasi perangkat nyata untuk prompt superuser, denial/revoke, Magisk, KernelSU/KernelSU Next, Shizuku, dan basic fallback.

Test timeout/hang (termasuk MockK/ByteBuddy attach) tidak boleh diklaim PASS.

## Privileged Access
- Gunakan abstraksi domain `PrivilegedAccessProvider`; jangan jadikan Shizuku sinonim akses advanced.
- Prioritas backend: Root → Shizuku → Basic.
- Root hanya tersedia pada flavor Full dan memakai libsu.
- Root tidak boleh diminta saat startup, boot, widget, atau service startup; hanya dari aksi eksplisit pengguna.
- Binary `su` tersedia tidak berarti grant tersedia.
- Denial, revoke, timeout, command failure, dan malformed output harus fail-safe.
- Perintah root berupa allowlist internal, tanpa interpolasi input pengguna, memiliki timeout/cancellation, dan output tervalidasi.
- Subscriber ID, output sensitif, command sensitif, dan binder token tidak boleh dicatat.
- Shizuku dan basic fallback wajib tetap berfungsi.
- Play/Lite tidak boleh mengandung libsu atau jalur eksekusi root.

## Git dan Hygiene
- Jangan membatalkan perubahan pengguna.
- Jangan commit secret, APK, `local.properties`, keystore, atau SDK path.
- Debug signing bukan bukti kesiapan produksi.
- Jangan mengubah fitur di luar scope PLAN aktif.
