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

## Harness spike yang diimplementasikan (WP-01 rework)

Harness berada di source set `fullDebug` saja, sehingga tidak pernah ikut ke `fullRelease`, `play`, atau `lite`:

| File | Isi |
|---|---|
| `app/src/fullDebug/java/com/leekleak/trafficlight/spike/RootSpikeProtocol.kt` | Allowlist, parser, dan formatter evidence (logika murni, diuji unit) |
| `app/src/fullDebug/java/com/leekleak/trafficlight/spike/RootSpikeActivity.kt` | Activity harness: satu tombol, satu aksi eksplisit |
| `app/src/fullDebug/AndroidManifest.xml` | Activity sebagai launcher terpisah berlabel "Root Telephony Spike" |
| `app/src/testFullDebug/java/com/leekleak/trafficlight/spike/RootSpikeProtocolTest.kt` | Unit test parser/evidence |

libsu 6.0.0 hanya masuk sebagai `fullDebugImplementation`. Tidak ada `su` path detection di mana pun: pada KernelSU Next default-unmount, keberadaan `su` di `PATH` tidak dapat dibedakan dari "belum granted", sehingga ketersediaan root hanya boleh dibuktikan lewat grant eksplisit. `Shell.getShell()` dipanggil tepat satu kali, di dalam handler tombol.

Allowlist probe (konstanta, tanpa input pengguna):

```text
id -u
getenforce
content query --uri content://telephony/siminfo --projection _id:sim_id:carrier_name:imsi
dumpsys isub
```

Probe injeksi kegagalan (terpisah dari allowlist produksi, tidak mengubah state):

```text
false
sleep 5
```

`false` membuktikan kategori `command_failure` (exit code non-zero), dan `sleep 5` dengan deadline 1 detik membuktikan kategori `timeout` serta pathway pembatalan job dan penutupan shell.

Dua jalur baca dipakai agar variasi API/transaction terlihat di evidence: `content query` ke provider `telephony/siminfo` (jalur kanonik, kolom `_id`, `sim_id`, `carrier_name`, `imsi`) dan `dumpsys isub` (jalur service dump). Sumber record dipilih deterministik: provider menang bila menghasilkan record, `isub` hanya dipakai bila provider kosong, sehingga satu subscription tidak pernah terhitung dua kali.

Pada API 29+, evidence ditulis ke `Download/traffic-light-root-spike-attempt-<N>.txt`; API 28 memakai direktori external-files aplikasi tanpa runtime permission. File berupa teks biasa tanpa nilai IMSI, tanpa output mentah, dan tanpa string command. Nomor attempt ada di nama file dan isi sehingga rangkaian grant, denial, revoke, dan retry tidak tertukar.

## Prosedur verifikasi perangkat

Jalankan pada build Full terinstrumentasi, lalu catat hanya evidence aman:

1. Cold start aplikasi utama; pastikan tidak ada prompt superuser.
2. Buka launcher **Root Telephony Spike**; pastikan masih tidak ada prompt dan status awal menyatakan belum ada root request.
3. Tekan **Request root and run spike**; verifikasi prompt baru muncul setelah aksi tersebut.
4. Uji grant, denial, revoke, dan retry. Setelah setiap attempt, salin/rename `Download/traffic-light-root-spike.txt` sebelum menjalankan attempt berikutnya agar evidence tidak tertukar.
5. Setelah grant, verifikasi `shellStatus=root_uid_0`, probe `uid` exit code 0, dan `shellStatusInt` adalah status root libsu.
6. Cocokkan `recordCount` dan slot non-sensitif dengan Settings/perangkat (dua SIM aktif di perangkat uji ini).
7. Verifikasi subscription ID, slot, carrier, dan keberadaan (bukan nilai) subscriber ID.
8. Verifikasi `failure_injection` menghasilkan `command_failure` dan `timeout_injection` menghasilkan `timedOut=true`.
9. Catat Android/OEM, SELinux, exit code, kategori parser, `providerRecordCount`, `isubRecordCount`, dan `recordSource`.
10. Pastikan file evidence tidak memuat IMSI/subscriber ID value, output mentah, command, binder token, atau data sensitif lain.

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
