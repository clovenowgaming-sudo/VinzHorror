# Hollow Ward

Horror 3D first-person maze game buat Android. Native Kotlin + OpenGL ES 2.0
(bukan Unity) — ringan, kompatibel device lawas, full dibangun di Termux
tanpa Android Studio, di-build jadi APK lewat GitHub Actions.

## Konsep

Kamu bangun di asylum yang ditinggalkan, hanya berbekal senter baterai
terbatas. Ada satu penghuni lain di labirin yang sama. Dia dengar langkahmu
dari dekat, dan dia lihat kamu kalau senter kamu menyorot ke arahnya dari
jarak jauh. Kalau dia nangkep kamu — jumpscare, layar merah, getar, respawn.

## Yang beneran "asli" di sini (bukan placeholder kotak)

- **Model 3D**: file `.obj` nyata (posisi + normal + UV) — lantai, langit-langit,
  dinding modular, pintu, monster humanoid low-poly, pipa & peti (prop
  cadangan), viewmodel senter. Digenerate prosedural lewat
  `tools/gen_models.py`, bukan ditulis tangan satu-satu, tapi hasilnya file
  mesh sungguhan yang dipakai renderer.
- **Texture**: PNG diffuse + normal map asli (bukan warna solid) untuk beton
  retak, logam karatan, ubin lantai, dan tekstur organik si monster —
  digenerate prosedural lewat `tools/gen_textures.py` pakai fBm noise +
  normal-from-height, karena aku nggak bisa unduh foto tekstur berlisensi di
  sandbox ini. Hasilnya tetap file gambar sungguhan dengan detail permukaan
  nyata, bukan flat-shaded.

## Rendering

- OpenGL ES 2.0, satu shader program: Blinn-ish diffuse + spotlight senter
  dengan cone & attenuation jarak, normal mapping tangent-space on-the-fly
  (dihitung dari world normal, nggak butuh tangent attribute karena semua
  geometri axis-aligned), fog eksponensial buat atmosfer & menyembunyikan
  draw-distance cutoff.
- 3 tingkat **Graphics Quality** (Low/Medium/Extreme) di menu Settings —
  ngatur fog density, normal map on/off, dan draw distance. `propDensity`
  di `GraphicsQuality` sudah disiapkan tapi belum dipakai nge-scatter
  pipa/peti — itu next step kalau mau nambah detail di Extreme.

## Struktur

```
app/src/main/java/com/vinz/horror/
  ObjLoader.kt, GpuMesh.kt, TextureUtil.kt, Shaders.kt   -- engine dasar
  Maze.kt, Player.kt, Monster.kt, GraphicsQuality.kt     -- gameplay
  GameRenderer.kt                                        -- loop render + logic
  GameView.kt, HudView.kt, MainActivity.kt               -- input & UI (programatik, no XML)
app/src/main/assets/models/   -- .obj hasil gen_models.py
app/src/main/assets/textures/ -- .png hasil gen_textures.py
tools/gen_models.py, tools/gen_textures.py -- generator aset, reproducible
```

## Build

Dibangun di GitHub Actions (`.github/workflows/build.yml`): setup JDK 17 +
Android SDK 34 + Gradle 8.6, lalu `gradle assembleDebug`. APK hasil ada di
artifact run tersebut, nama `VinzHorror-debug-apk`.

## Kontrol

- Separuh layar kiri: drag = gerak (virtual joystick)
- Separuh layar kanan: drag = lihat sekeliling
- Tombol LIGHT: toggle senter (nguras baterai saat nyala, ngecas pelan saat mati)
- Tombol MENU: balik ke menu utama
