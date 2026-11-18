# Root Telephony Spike — WP-01

## Keputusan

**REWORK / production backend blocked.** Tidak ada perangkat Android yang terhubung ke environment executor, sehingga grant libsu, perilaku prompt, UID root, SELinux, dan pembacaan seluruh `SubscriptionRecord` belum dapat dibuktikan. WP-03 tidak boleh dimulai berdasarkan dokumen ini.

## Target dan data minimum (T-001)

Matrix verifikasi minimum harus mencakup perangkat fisik multi-SIM pada:

| Dimensi | Target minimum |
|---|---|
| Android | Versi terendah yang didukung aplikasi dan sedikitnya satu versi Android terkini |
| Root manager | Magisk, KernelSU, KernelSU Next (masing-masing versi dicatat) |
| OEM / device | Model dan build fingerprint dicatat; hasil satu OEM tidak digeneralisasi |
| SELinux | Mode dicatat tanpa mengubah enforcement |
| SIM | Sedikitnya satu perangkat dengan dua subscription aktif |

Setiap subscription harus menghasilkan tepat empat field domain berikut:

- `subscriptionId: Int`
- `simSlotIndex: Int`
- `carrierName: String`
- `subscriberId: String?`

`subscriberId` boleh `null` bila platform/operator tidak menyediakannya, tetapi hasil tersebut harus dibedakan dari kegagalan protocol. Nilai subscriber ID dan output mentah yang mungkin memuatnya tidak boleh masuk log, screenshot, artifact, atau pesan error.

## Harness dan batas keamanan

Spike perangkat berikutnya harus memakai harness Full-only berbasis libsu dengan batas berikut:

1. Initialization hanya memeriksa availability tanpa memanggil shell yang meminta grant.
2. `Shell.getShell()` atau operasi libsu yang dapat meminta grant hanya dipanggil setelah aksi eksplisit pengguna.
3. Command berasal dari konstanta allowlist internal; tidak menerima argumen atau interpolasi input pengguna.
4. Eksekusi memiliki timeout dan cancellation; exit code non-zero adalah kegagalan.
5. Parser menerima protocol berversi dengan batas jumlah record, panjang baris, dan panjang field.
6. Log hanya memuat kategori hasil, durasi, exit-code class, Android/API/OEM/root-manager metadata, dan jumlah record. Command, binder token, output mentah, serta subscriber ID dilarang dicatat.
7. Semua kegagalan harus kembali ke Shizuku lalu Basic; tidak boleh retry loop otomatis.

Format evidence aman yang diharapkan:

```text
protocolVersion=<integer>
recordCount=<integer>
record[<index>].subscriptionIdPresent=<true|false>
record[<index>].slotValid=<true|false>
record[<index>].carrierPresent=<true|false>
record[<index>].subscriberIdPresent=<true|false>
```

Format di atas adalah ringkasan evidence, bukan protocol produksi, dan sengaja tidak memuat nilai sensitif.

## Prosedur verifikasi perangkat

Jalankan pada build Full terinstrumentasi, lalu catat hanya evidence aman:

1. Cold start aplikasi; pastikan tidak ada prompt superuser.
2. Buka rationale Advanced access dan lakukan aksi enable eksplisit.
3. Verifikasi prompt baru muncul setelah aksi tersebut.
4. Uji grant, denial, revoke, dan retry.
5. Setelah grant, verifikasi proses command berjalan sebagai UID 0 dan menghasilkan exit code 0.
6. Cocokkan jumlah dan identitas non-sensitif subscription dengan Settings/perangkat.
7. Verifikasi subscription ID, slot, carrier, dan keberadaan (bukan nilai) subscriber ID.
8. Ulangi setelah timeout buatan, command failure, malformed output, dan revoke.
9. Catat API/transaction yang dipakai, Android/OEM, SELinux, exit code, timeout, dan kategori parser result.
10. Pastikan fallback Shizuku dan Basic tetap dapat digunakan.

## Evidence executor saat ini

Perintah:

```text
adb devices -l
```

Output mentah:

```text
List of devices attached
```

Interpretasi: ADB tersedia, tetapi tidak ada emulator atau perangkat fisik terhubung. Karena itu:

- T-002 belum terverifikasi: libsu grant dan no-prompt initialization memerlukan perangkat.
- T-003 belum terverifikasi: UID root dan seluruh field subscription memerlukan perangkat rooted multi-SIM.
- T-004 hanya memiliki rancangan evidence; variasi API/transaction, SELinux, exit code, timeout, dan protocol aktual belum dapat dicatat.

## Syarat keluar REWORK

WP-01 dapat diajukan kembali ke QC hanya setelah matrix memiliki sekurangnya satu evidence perangkat rooted multi-SIM yang membuktikan seluruh field, no-prompt saat initialization, prompt setelah aksi eksplisit, exit-code/timeout/parser behavior, dan redaksi data sensitif. Dukungan Magisk, KernelSU, dan KernelSU Next yang belum diuji tetap menjadi blocker release dan dilanjutkan pada WP-09.
