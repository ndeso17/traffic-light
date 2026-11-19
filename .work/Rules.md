# Rules — Traffic Light

## Wajib Baca
`INDEX.md`, `PRD.md`, `Architecture.md`, `Design.md`, `Schema.md`, dan `Rules.md` sebelum pekerjaan.

## Kotlin/Android
- Ikuti style/package existing.
- Pekerjaan blocking memakai coroutine dan bukan Main dispatcher.
- UI menerima domain state, bukan objek backend.
- State lintas layer memakai Flow/StateFlow.
- Dependency flavor-specific ditempatkan pada source set/modul yang tepat.

## Privileged Access
- Root hanya Full, prioritas di atas Shizuku; Basic selalu ada.
- Jangan prompt root saat startup/boot/widget/service.
- `su` tersedia bukan bukti grant.
- Jangan deteksi root manager berdasarkan package sebagai sumber kebenaran.
- Gunakan libsu, command constant/allowlisted, tanpa input pengguna, dengan timeout.
- Validasi exit code dan output; jangan log data sensitif.
- Status grant tidak boleh disimpan sebagai source of truth.
- Libsu/jalur root dilarang di Play/Lite.

## Naming
Konsep generik: `Privileged*` atau `AdvancedAccess*`. `Shizuku*` dan `Root*` hanya untuk backend spesifik.

## Testing
Wajib menguji priority, grant, denial, revoke, timeout, malformed output, Root→Shizuku fallback, Basic fallback, tidak ada startup prompt, preference migration, dan DataPlan reconciliation. Manual: Magisk, KernelSU, KernelSU Next, Shizuku, non-root.

## Security
Tidak ada arbitrary/remote shell, daemon root permanen, perubahan SELinux, exported root service, atau detail sensitif pada production error.

## Build
Build dan test **dilarang** dijalankan di host/lokal. Lihat `AGENTS.md`: otoritas build/test
adalah GitHub Actions, dan artifact wajib diunduh ke `.artefak/` (gitignored) sebagai bukti.

```bash
git push origin <branch>
gh run list --branch <branch> --limit 5
gh run download <run-id> -D .artefak/<label>
```

`.artefak/` tidak boleh di-commit. Angka gate yang diklaim harus berasal dari file artifact.

## Larangan
Jangan implementasi sebelum PLAN; jangan aktifkan root di Play/Lite; jangan hapus Shizuku; jangan ubah Room tanpa migration; jangan klaim timeout/hang sebagai PASS.

## Catatan INDEX
Indexer generik telah memindai 274 file tetapi menemukan 0 simbol karena parser tidak mendukung Kotlin project ini. INDEX tetap menjadi peta file/corpus, dan eksplorasi simbol spesifik boleh memakai pencarian source dengan kekurangan coverage ini dicatat secara eksplisit.
