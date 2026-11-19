# Root Telephony Spike — WP-01

## Keputusan

**Device evidence lengkap; WP-01 diajukan ke QC.** Seluruh field domain berhasil dibaca sebagai UID 0 pada perangkat fisik multi-SIM (moto g57 power, Android 16 / SDK 36, KernelSU Next), dan jalur grant, denial, serta revoke/retry terbukti dari harness Full-only tanpa prompt saat startup.

Satu gate repository masih **BLOCKED** dan tidak diklaim PASS: `bash gradlew test` tidak dapat menghasilkan baseline hijau di environment executor (MockK/ByteBuddy external attach di `:playIntegration:testDebugUnitTest` dan kegagalan fetch `org.robolectric:android-all-instrumented`), sehingga dijalankan ulang lewat job CI terpisah `full-test-baseline`. Dukungan Magisk, KernelSU, dan OEM lain tetap menjadi blocker release (WP-09).

## Status per 2026-11-19

| Item | Status | Bukti |
|---|---|---|
| T-002 no-prompt initialization, grant, denial | PASS | 8 attempt perangkat; `startupShellCached=false` dan `startupShellRequest=false` pada semuanya |
| T-003 subscription ID/slot/carrier/subscriber via UID 0 | PASS | `shellStatus=root_uid_0`, 2 record aktif, `recordSource=provider` |
| T-004 SELinux, exit code, timeout, protocol, redaksi | PASS | `selinux=Enforcing`, `command_failure`, `timeout`, protocol v2 |
| Revoke/retry | PASS | attempt 7 grant → attempt 8 `non_root` dengan `shellCachedBefore=false` setelah grant dicabut di root manager |
| `bash gradlew test` baseline | BLOCKED (environment) | Lihat bagian "Gate repository" |

### Mengapa `_id` adalah subscription ID (bukan asumsi)

Dibuktikan dari sumber platform `android16-release`, bukan dari kebiasaan:

| Konstanta platform | Nilai |
|---|---|
| `Telephony.SimInfo.CONTENT_URI` | `content://telephony/siminfo` |
| `SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID` | `"_id"` |
| `SimInfo.COLUMN_SIM_SLOT_INDEX` | `"sim_id"` |
| `SimInfo.COLUMN_CARRIER_NAME` | `"carrier_name"` |
| `SubscriptionManager.CONTENT_URI` | `SimInfo.CONTENT_URI` |
| `SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID` | `SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID` |
| `SubscriptionManager.SIM_SLOT_INDEX` / `CARRIER_NAME` | kolom `sim_id` / `carrier_name` |

`SubscriptionManager` sendiri memberi alamat per subscription lewat `Uri.withAppendedPath(CONTENT_URI, String.valueOf(subscriptionId))`, yaitu path `siminfo/<subId>` — konsisten dengan `_id` sebagai subId. Karena itu projection produksi (`_id:sim_id:carrier_name:imsi`) sudah memakai nama kolom platform yang benar, dan tidak ada kolom `subscription_id` yang perlu diprobe.

### Temuan: kolom `subscription_id` tidak ada

Probe `--projection _id:subscription_id:sim_id:carrier_name:imsi` mengembalikan `lineCount=1`/`recordCount=0` (`subscriptionIdColumn=no_rows`, `providerMarkers=subscriptionId=0`). Ini bukan kegagalan harness: kolom tersebut memang tidak ada pada tabel `siminfo`, sesuai konstanta platform di atas. Probe ini dipertahankan sebagai bukti negatif, bukan sebagai jalur data.

### Temuan: `dumpsys isub` Android 16 belum dikenali parser

`probe[isub]` = `empty_or_malformed` (94 baris, 0 record) dengan fingerprint aman `isubMarkers=subscriptionInfoInternal=0,subscriptionInfo=0,mSubscriptionInfo=0,slotIndexToSubId=0,simSlotIndex=10,subscriptionId=10`. Artinya dump memuat 10 pasangan `simSlotIndex`/`id` tetapi tanpa penanda kelas `SubscriptionInfo*` yang dikenal parser.

Konsekuensi: jalur `isub` **tidak dipakai** untuk evidence mana pun (`recordSource=provider`), dan karena itu tidak memblokir T-003. Jalur ini tetap dipertahankan sebagai fallback diagnostik; dukungan format Android 16 dipindahkan ke WP-09 (matrix perangkat) agar tidak menambah round-trip build pada spike. Nilai subscriber ID tidak pernah ditulis, sehingga dump mentah tidak boleh dicatat untuk menebak format.

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

Format evidence aman yang diharapkan (protocolVersion=2):

```text
protocolVersion=<integer>
attempt=<integer>
startupShellRequest=false
startupShellCached=<true|false>
authorizationRequestedByExplicitAction=<true|false>
shellCachedBefore=<true|false>
suVisibleToApp=<true|false>          # diagnostik saja, bukan keputusan availability
shellStatus=<root_uid_0|root_uid_unverified|non_root|unknown>
shellStatusInt=<integer>
libsuGrantState=<granted|denied_or_unavailable|shell_unavailable>
selinux=<Enforcing|Permissive|Disabled|unknown>
probe[<id>].category=<success|command_failure|timeout|empty_or_malformed|malformed>
probe[<id>].exitCode=<integer>
probe[<id>].timedOut=<true|false>
probe[<id>].lineCount=<integer>
probe[<id>].stderrLineCount=<integer>
probe[<id>].recordCount=<integer>
recordSource=<provider|isub|none>
recordCount=<integer>
subscriptionIdColumn=<present|absent|no_rows|query_failed|not_probed>
subscriptionIdMatchesRowId=<true|false|unknown>
providerMarkers=<key=count,...>      # nama key + jumlah, tanpa nilai
isubMarkers=<key=count,...>          # nama key + jumlah, tanpa nilai
record[<index>].subscriptionIdPresent=<true|false>
record[<index>].slotValid=<true|false>
record[<index>].carrierPresent=<true|false>
record[<index>].carrierValueSafe=<true|false>
record[<index>].subscriberIdPresent=<true|false>
```

Format di atas adalah ringkasan evidence, bukan protocol produksi. Tidak ada nilai subscription ID, nilai subscriber ID, nama carrier, output mentah, maupun string command di dalamnya; `providerMarkers`/`isubMarkers` hanya menghitung kemunculan nama key.

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

Kontrak status shell libsu 6 (dipakai untuk membaca evidence): `UNKNOWN = -1`, `NON_ROOT_SHELL = 0`, `ROOT_SHELL = 1`. Tetapi status itu **tidak boleh dibaca setelah shell ditutup**: `ShellImpl.release()` menulis `status = -1`, sehingga shell yang terbukti `id -u == 0` bisa tercatat `shellStatusInt=-1` bila probe timeout menutup shell lebih dulu. Karena itu harness mengambil `shell.status` sekali di awal (`acquiredStatus`) dan menjadikan `id -u == 0` sebagai otoritas label `root_uid_0`.

Setiap attempt menutup shell yang dipakai. Shell yang dibiarkan hidup akan menutupi revoke pada attempt berikutnya dan membocorkan proses root; attempt berikutnya harus mengevaluasi ulang keputusan otorisasi.

Catatan wiring dependensi (dibuktikan lewat CI): AGP 9.5.1 membuat source set gabungan `fullDebug` beserta configuration-nya pada fase variant creation, yaitu setelah blok `dependencies` dievaluasi. Deklarasi `"fullDebugImplementation"(...)` di dalam blok `dependencies` karena itu gagal dengan `Configuration with name 'fullDebugImplementation' not found`. Dependensi ditambahkan secara lazy melalui `configurations.configureEach` yang hanya menyentuh `fullDebugImplementation` dan `testFullDebugImplementation`, sehingga `fullRelease`, `play`, dan `lite` tetap tanpa libsu. Workflow CI memverifikasi tiga hal: libsu ada di `fullDebugRuntimeClasspath`, libsu tidak ada di `playDebugRuntimeClasspath`/`liteDebugRuntimeClasspath`, dan simbol `RootSpikeActivity`/`RootSpikeProtocol`/`Shell` benar-benar masuk ke dex APK.

Allowlist probe (konstanta, tanpa input pengguna):

```text
id -u
getenforce
content query --uri content://telephony/siminfo --projection _id:sim_id:carrier_name:imsi
content query --uri content://telephony/siminfo --projection _id:subscription_id:sim_id:carrier_name:imsi
dumpsys isub
```

Entri keempat bukan jalur data, melainkan bukti negatif: membuktikan kolom `subscription_id` tidak diekspos provider. `_id` yang dipakai entry ketiga adalah `SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID`.

Probe injeksi kegagalan (terpisah dari allowlist produksi, tidak mengubah state):

```text
false
sleep 5
```

`false` membuktikan kategori `command_failure` (exit code non-zero), dan `sleep 5` dengan deadline 1 detik membuktikan kategori `timeout` serta pathway pembatalan job dan penutupan shell.

Dua jalur baca dipakai agar variasi API/transaction terlihat di evidence: `content query` ke provider `telephony/siminfo` (jalur kanonik, kolom `_id`, `sim_id`, `carrier_name`, `imsi`) dan `dumpsys isub` (jalur service dump). Sumber record dipilih deterministik: provider menang bila menghasilkan record, `isub` hanya dipakai bila provider kosong, sehingga satu subscription tidak pernah terhitung dua kali.

Pada API 29+, evidence ditulis ke `Download/traffic-light-root-spike-attempt-<N>-<epochMillis>.txt`; API 28 memakai direktori external-files aplikasi tanpa runtime permission. Nama file memuat attempt dan timestamp sehingga attempt yang dijalankan berulang dalam satu detik tetap tidak saling menimpa. File berupa teks biasa tanpa nilai IMSI, tanpa output mentah, dan tanpa string command.

## Prosedur verifikasi perangkat

Jalankan pada build Full terinstrumentasi, lalu catat hanya evidence aman:

1. Cold start aplikasi utama; pastikan tidak ada prompt superuser.
2. Buka launcher **Root Telephony Spike**; pastikan masih tidak ada prompt dan status awal menyatakan belum ada root request.
3. Tekan **Request root and run spike**; verifikasi prompt baru muncul setelah aksi tersebut.
4. Uji grant, denial, revoke, dan retry. Setiap attempt menghasilkan file berbeda bernama `traffic-light-root-spike-attempt-<N>-<epochMillis>.txt` agar evidence tidak tertukar.
5. Setelah grant, verifikasi `shellStatus=root_uid_0`, probe `uid` exit code 0 dengan `lineCount=1`, dan `shellStatusInt` diambil sebelum shell ditutup.
6. Cocokkan `recordCount` dan slot non-sensitif dengan Settings/perangkat (dua SIM aktif di perangkat uji ini).
7. Verifikasi subscription ID (`_id`), slot (`sim_id`), carrier (`carrier_name`), dan keberadaan (bukan nilai) `imsi`.
8. Verifikasi `failure_injection` menghasilkan `command_failure` dan `timeout_injection` menghasilkan `timedOut=true`.
9. Catat Android/OEM, SELinux, exit code, kategori parser, `providerRecordCount`, `isubRecordCount`, `recordSource`, dan marker fingerprint.
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

Interpretasi: environment executor tidak memiliki perangkat ADB, sehingga pemasangan dan interaksi dilakukan manual oleh pengguna. Ketiadaan ADB **bukan lagi blocker evidence** karena file protocol v2 dari perangkat telah dibaca langsung dari shared storage dan diringkas di bawah.

### Evidence protocol v2 — build `8bac369`, CI run `44481850855`

Perangkat: moto g57 power, Android 16 / SDK 36, dua SIM aktif, SELinux Enforcing, rooted DroidSpace dengan KernelSU Next default-unmount. Root manager diidentifikasi dari konteks perangkat pengguna; nama/versinya tidak ditulis harness agar protocol tidak bergantung pada manager tertentu.

| Attempt | Aksi pengguna / hasil | `shellStatus` | `shellStatusInt` | `shellCachedBefore` | Record |
|---|---|---|---:|---|---:|
| 1 | grant | `root_uid_0` | 1 | false | 2 |
| 2 | denial | `non_root` | 0 | false | 0 |
| 3 | grant/retry | `root_uid_0` | 1 | false | 2 |
| 4–6 | denial/retry non-root | `non_root` | 0 | false | 0 |
| 7 | grant | `root_uid_0` | 1 | false | 2 |
| 8 | grant dicabut di root manager, lalu Retry | `non_root` | 0 | false | 0 |

Attempt kunci dan file sumber:

- Grant: `Download/traffic-light-root-spike-attempt-1-1795076285676.txt` dan attempt 7.
- Denial: attempt 2/4/5/6 (`denied_or_unavailable`, fail-safe, tidak menjalankan probe privileged).
- Revoke: attempt 7 grant → pengguna mencabut grant di root manager → attempt 8 `non_root`; `shellCachedBefore=false` membuktikan cached root shell tidak menyamarkan revoke.
- Seluruh attempt: `startupShellRequest=false`, `startupShellCached=false`, `authorizationRequestedByExplicitAction=true`.

Evidence grant attempt 1 dan 7 konsisten:

```text
shellStatus=root_uid_0
shellStatusInt=1
libsuGrantState=granted
selinux=Enforcing
probe[uid].category=success exitCode=0 lineCount=1
probe[provider].category=success exitCode=0 recordCount=2
probe[isub].category=empty_or_malformed exitCode=0 lineCount=94 recordCount=0
probe[failure_injection].category=command_failure exitCode=1
probe[timeout_injection].category=timeout exitCode=-1 timedOut=true
providerRecordCount=2
recordSource=provider
recordCount=2
```

Dua record memenuhi field minimum tanpa mengungkap nilainya: slot 0 dan 1 valid, carrier hadir, dan subscriber ID hadir. `record[1].carrierValueSafe=false` berarti nama carrier kedua mengandung karakter di luar regex evidence; nilainya tidak dicatat dan `carrierPresent=true`, jadi ini temuan sanitasi, bukan kehilangan field.

Audit privasi atas file protocol v2: tidak ada nilai `_id`, IMSI/subscriber ID, nama carrier, output mentah, command, binder token, atau credential. Hanya metadata perangkat non-sensitif, exit code, kategori, line count, marker count, slot index, dan boolean presence.

### Build/gate terakhir

- GitHub Actions run `44481850855`, commit `8bac369`: **SUCCESS**.
- `:app:assembleFullDebug`, 41 unit test spike, isolasi Play/Lite, scope libsu FullDebug, packaging harness, dan publikasi APK: PASS.
- Verifikasi lokal non-Gradle: K2JVMCompiler + JUnitCore → `OK (41 tests)`; `git diff --check` PASS.
- Full `bash gradlew test`: **BLOCKED / tidak diklaim PASS**, sesuai REWORK-R4. Baseline sebelumnya gagal karena MockK/ByteBuddy external attach dan fetch artifact Robolectric; build Gradle lokal tidak diulang karena host pernah OOM/reboot.

## Syarat keluar REWORK

Syarat evidence perangkat untuk T-002–T-004 terpenuhi. WP-01 dapat diajukan kembali ke QC dengan keputusan teknis **GO untuk melanjutkan desain backend WP-03**, tetap fail-safe ke Shizuku → Basic. Ini bukan approval release: coverage Magisk, KernelSU, KernelSU Next lain, versi Android minimum, dan OEM lain tetap wajib di WP-09. Gate full test repository tetap blocker environment terbuka yang harus dinilai QC, bukan disamarkan sebagai PASS.
