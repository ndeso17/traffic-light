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

Kontrak API libsu 6 yang ditemukan lewat device (attempt 1–3): `Shell.Job` **tidak** menampung output kecuali pemanggil mendaftarkan sink lewat `Shell.Job.to(list)`. Tanpa itu, `Result` tetap membawa exit code tetapi seluruh baris output dibuang, sehingga `id -u` terlihat "sukses" tanpa output. `RootSpikeShellRunner` mendaftarkan sink stdout dan stderr sebelum job dieksekusi; `RootSpikeShellRunnerTest` memalsukan kontrak libsu tersebut agar regresi ini tertangkap di unit test, bukan hanya di perangkat.

Kontrak status shell libsu 6 (dipakai untuk membaca evidence): `UNKNOWN = -1`, `NON_ROOT_SHELL = 0`, `ROOT_SHELL = 1`. Jadi `shellStatusInt=1` berarti shell root benar-benar diperoleh, dan `shellStatusInt=0` berarti fallback non-root (denial).

Setiap attempt menutup shell yang dipakai. Shell yang dibiarkan hidup akan menutupi revoke pada attempt berikutnya dan membocorkan proses root; attempt berikutnya harus mengevaluasi ulang keputusan otorisasi.

Catatan wiring dependensi (dibuktikan lewat CI): AGP 9.5.1 membuat source set gabungan `fullDebug` beserta configuration-nya pada fase variant creation, yaitu setelah blok `dependencies` dievaluasi. Deklarasi `"fullDebugImplementation"(...)` di dalam blok `dependencies` karena itu gagal dengan `Configuration with name 'fullDebugImplementation' not found`. Dependensi ditambahkan secara lazy melalui `configurations.configureEach` yang hanya menyentuh `fullDebugImplementation` dan `testFullDebugImplementation`, sehingga `fullRelease`, `play`, dan `lite` tetap tanpa libsu. Workflow CI memverifikasi tiga hal: libsu ada di `fullDebugRuntimeClasspath`, libsu tidak ada di `playDebugRuntimeClasspath`/`liteDebugRuntimeClasspath`, dan simbol `RootSpikeActivity`/`RootSpikeProtocol`/`Shell` benar-benar masuk ke dex APK.

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
4. Uji grant, denial, revoke, dan retry. Setiap attempt menghasilkan file berbeda bernama `traffic-light-root-spike-attempt-<N>.txt` agar evidence tidak tertukar.
5. Setelah grant, verifikasi `shellStatus=root_uid_0`, probe `uid` exit code 0, dan `shellStatusInt` adalah status root libsu.
6. Cocokkan `recordCount` dan slot non-sensitif dengan Settings/perangkat (dua SIM aktif di perangkat uji ini).
7. Verifikasi subscription ID, slot, carrier, dan keberadaan (bukan nilai) subscriber ID.
8. Verifikasi `failure_injection` menghasilkan `command_failure` dan `timeout_injection` menghasilkan `timedOut=true`.
9. Catat Android/OEM, SELinux, exit code, kategori parser, `providerRecordCount`, `isubRecordCount`, dan `recordSource`.
10. Pastikan file evidence tidak memuat IMSI/subscriber ID value, output mentah, command, binder token, atau data sensitif lain.

## Evidence executor saat ini

### Attempt perangkat 1–3 (commit `1228363`, moto g57 power, Android 16 / SDK 36)

| Attempt | `shellStatus` | `shellStatusInt` | `suVisibleToApp` | `probe[uid]` |
|---|---|---|---|---|
| 1 | `non_root` | 0 (`NON_ROOT_SHELL`) | false | tidak dijalankan |
| 2 | `root_uid_not_zero` | 1 (`ROOT_SHELL`) | true | `exitCode=0`, `lineCount=0`, `category=malformed` |
| 3 | `root_uid_not_zero` | 1 (`ROOT_SHELL`) | true | `exitCode=0`, `lineCount=0`, `category=malformed` |

Interpretasi:

- Denial (attempt 1) fail-safe: tidak ada crash, tidak ada prompt berulang, fallback `NON_ROOT_SHELL`, dan evidence tetap ditulis.
- Grant (attempt 2–3) berhasil: libsu melaporkan `ROOT_SHELL`, sehingga prompt dan grant terbukti terjadi **hanya** setelah aksi eksplisit tombol.
- Anomali `lineCount=0` dengan `exitCode=0` bukan kegagalan grant, melainkan bug harness: libsu 6 tidak menampung output tanpa `Shell.Job.to(list)`. Sudah diperbaiki dan dikunci unit test (`RootSpikeShellRunnerTest`).
- Attempt 3 memakai `shellCachedBefore=true` karena jalur `root_uid_not_zero` tidak menutup shell. Sudah diperbaiki: shell ditutup di semua jalur.
- T-002/T-003 tetap belum lengkap: grant terbukti, tetapi UID root, SELinux, dan record SIM belum terbaca karena bug capture di atas.

### Build terverifikasi (CI run `44438632587`, commit `1228363`)

- `:app:assembleFullDebug` menghasilkan APK dan artifact `trafficlight-full-debug-1228363401f0462ec5b06291ecd0cb98747e4862` (artifact ID `14490296132`, 18,926,284 byte).
- `:app:testFullDebugUnitTest --tests "com.leekleak.trafficlight.spike.*"` lulus (27 test protocol/evidence).
- `fullDebugRuntimeClasspath` memuat `com.github.topjohnwu.libsu:core:6.0.0`.
- `playDebugRuntimeClasspath` dan `liteDebugRuntimeClasspath` tidak memuat libsu/topjohnwu.
- APK dex memuat `RootSpikeActivity`, `RootSpikeProtocol`, dan `com.topjohnwu.superuser.Shell`; merged manifest memuat activity dan kategori launcher.

Verifikasi lokal non-Gradle juga mengompilasi source harness terhadap `android-37.2/android.jar` + libsu dan menjalankan JUnitCore dengan hasil `OK (27 tests)`. Build Gradle lokal sengaja tidak dijalankan karena batas resource environment; build otoritatif dilakukan di GitHub Actions.

Perintah pemeriksaan koneksi perangkat:

```text
adb devices -l
```

Output mentah:

```text
List of devices attached
```

Interpretasi: environment executor tidak memiliki perangkat ADB. APK siap dipasang manual, tetapi bukti perilaku perangkat tetap harus dikumpulkan. Karena itu:

- T-002 belum terverifikasi: libsu grant dan no-prompt initialization memerlukan perangkat.
- T-003 belum terverifikasi: UID root dan seluruh field subscription memerlukan perangkat rooted multi-SIM.
- T-004 hanya memiliki rancangan evidence; variasi API/transaction, SELinux, exit code, timeout, dan protocol aktual belum dapat dicatat.

## Syarat keluar REWORK

WP-01 dapat diajukan kembali ke QC hanya setelah matrix memiliki sekurangnya satu evidence perangkat rooted multi-SIM yang membuktikan seluruh field, no-prompt saat initialization, prompt setelah aksi eksplisit, exit-code/timeout/parser behavior, dan redaksi data sensitif. Dukungan Magisk, KernelSU, dan KernelSU Next yang belum diuji tetap menjadi blocker release dan dilanjutkan pada WP-09.
