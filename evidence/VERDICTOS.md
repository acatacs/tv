# Viabilidad navegador (Brave / Firefox) + ajustes de animación — TV Kalley R10G "MOKA"

Fecha: 2026-10-26 · Dispositivo: 192.168.1.74:5555 · Sesión NO root (uid 2000) · **Solo lectura, nada instalado, ningún setting modificado**

## 1. Hechos del dispositivo (medidos)

| Métrica | Valor | Comando |
|---|---|---|
| `ro.product.cpu.abi` | `armeabi-v7a` | `getprop ro.product.cpu.abi` |
| `ro.product.cpu.abilist` | `armeabi-v7a,armeabi` | `getprop ro.product.cpu.abilist` |
| `ro.product.cpu.abilist64` | (vacío) | `getprop ro.product.cpu.abilist64` |
| `/system/lib64` | **No existe** (`ls: No such file or directory`) | `ls /system/lib64` |
| `/system/lib` | 640 entradas | `ls /system/lib \| wc -l` |
| `ro.build.fingerprint` | `KALLEY/KALLEY_FFM/R10G:14/UKR1.260612.001/26062418:user/release-keys` | `getprop ro.build.fingerprint` |
| `ro.build.characteristics` | `nosdcard,tv` | `getprop ro.build.characteristics` |
| `ro.build.date` | `Thu Jul 16 20:04:11 CST 2026` | `getprop ro.build.date` |
| Android | 14 / SDK 34 | `getprop ro.build.version.sdk` |
| GPU | PowerVR BXE-4-32, OpenGL ES 3.2 (`ro.opengles.version=196610`=0x30002) | `dumpsys SurfaceFlinger` |
| RAM | Total 1,895,540K (~1.8 GB); libres ~556 MB | `dumpsys meminfo` |
| CPU | 4× ARMv7, load avg ~18 | `cat /proc/cpuinfo`, `cat /proc/loadavg` |
| Play Store | `com.android.vending` versionName `53.4.17-34` | `dumpsys package com.android.vending` |
| Espacio /data | 9.1 G total, **6.5 G libres** (28%) | `df -h /data` |
| Features | `android.software.leanback`, `android.software.leanback_only` (63 features) | `pm list features` |
| Animaciones | `window_animation_scale=1.0`, `transition_animation_scale=1.0`, `animator_duration_scale=null` (→1.0) | `settings get global ...` |
| Entrada | `RC860U Mouse` (CURSOR/MIC), teclado+DPAD, **sin** `android.hardware.touchscreen` | `dumpsys input` |
| Navegadores instalados | **ninguno** (solo `com.google.android.webview` 155.0.8059.43) | `pm list packages` |

**Conclusión de plataforma: 32-bit ARM puro, sin soporte arm64-v8a en el sistema ni en WebView/GMS/vending (`primaryCpuAbi=armeabi-v7a` en los tres).**

## 2. Veredictos

| Objetivo | Veredicto | Prueba |
|---|---|---|
| **Firefox Android 32-bit** | **INSTALABLE** | Fennec F-Droid **158.0.2** (`org.mozilla.fennec_fdroid`, versionCode 1580200) contiene **solo** `lib/armeabi-v7a/` (15 .so: libxul 59.998.309 B, libmegazord, libfreebl3, libnss3…) y **ningún** `lib/arm64-v8a` → casa con `ro.product.cpu.abi=armeabi-v7a`. `minSdkVersion=26` ≤ 34. `uses-feature glEsVersion=131072` (GLES 2.0) required=True ≤ GLES 3.2 del TV. |
| **uBlock Origin** | **SOPORTADO** | AMO API v5 `addon/ublock-origin/`: `current_version.compatibility = {"firefox":{"min":"115.0"},"android":{"min":"115.0"}}`; `promoted=[{"apps":["firefox","android"],"category":"recommended"}]`. XPI 1.75.0 `browser_specific_settings.gecko_android.strict_min_version = "115.0"`. Firefox 158 > 115. |
| **Privacy Badger** | **SOPORTADO** | AMO `addon/privacy-badger17/`: `current_version.compatibility = {"firefox":{"min":"128.0"},"android":{"min":"128.0"}}`; `promoted=[{"apps":["firefox","android"],"category":"recommended"}]`. XPI 2026.9.15 `gecko_android.strict_min_version = "128.0"`. Firefox 158 > 128. |
| **Brave 32-bit** | **INSTALABLE (pero sin extensiones)** | Release estable **v1.98.60** publica `BraveMonoarm.apk` (231.232.279 B). SHA-256 local `16992661095c901b017fe6e01a1d663925e22d3a6caec6d814a61e829d817e1b` **=** el `.sha256` oficial. El APK contiene **solo** `lib/armeabi-v7a/` (libchrome.so, libchromium_android_linker.so, libcrashpad_handler_trampoline.so, libwg-go.so), sin `lib64`. `minSdkVersion=29` ≤ 34; `glEsVersion=196608` (GLES 3.0) ≤ 3.2. Repo F-Droid oficial de Brave: 97 APKs `armeabi-v7a` (último `BraveMonoarm_1.97.59.apk`). |
| **Brave + uBlock/Privacy Badger** | **NO INSTALABLE** | Brave en Android **no soporta extensiones**. En desarrollo como *flag experimental* solo en canal Canary (issue brave/brave-browser#59354, Sergey Zhukovsky, 2026-10). No hay uBO ni Privacy Badger instalables en 1.98.60 estable. |
| **Chrome de terceros** | no instalado | `pm list packages` no devuelve `com.android.chrome`. El WebView de sistema es `com.google.android.webview` 155.0.8059.43, `primaryCpuAbi=armeabi-v7a`. |

### El cruce 1 vs 2 NO produce bloqueo duro
Firefox 158 (32-bit) **sí** cumple los mínimos de AMO: uBO exige Android ≥115 y Privacy Badger ≥128; 158 ≥ 128. La cadena Firefox 32-bit → uBO + Privacy Badger es viable hoy.

## 3. Plan de instalación (propuesta, NO ejecutada)

### Opción A — Firefox (recomendada: es la única con extensiones)
```bash
# 1) Descargar el APK 32-bit de F-Droid (sin Play, sin instalar F-Droid)
curl -L -o /tmp/fennec_arm.apk \
  https://f-droid.org/repo/org.mozilla.fennec_fdroid_1580200.apk
#   versionCode 1580200, versionName 158.0.2, nativecode armeabi-v7a, minSdk 26
#   (F-Droid index-v1.json: /repo/org.mozilla.fennec_fdroid_1580200.apk)

# 2) Firmar e instalar por ADB
adb -s 192.168.1.74:5555 install /tmp/fennec_arm.apk
# Si el sistema exige firma: resignar con apksigner (apk ya viene firmado por F-Droid).

# 3) Las extensiones se instalan DENTRO de Firefox (no por ADB):
#    Menú ⋮ → Extensiones → buscar "uBlock Origin" y "Privacy Badger" → Activar
#    Requiere red; AMO sirve las dos para el target android según compatibility.
```
Play Store **también** sirve Firefox 32-bit (Mozilla mantiene soporte ARM 32-bit; el recorte de Firefox 144 solo afecta a x86-32 y sube minSdk a Android 8.0 — blog Mozilla Future Releases 2025-09-15). El APK de F-Droid es la vía más limpia para un APK de un solo ABI.

### Opción B — Brave (alternativa sin extensiones)
```bash
curl -L -o /tmp/BraveMonoarm.apk \
  https://github.com/brave/brave-browser/releases/download/v1.98.60/BraveMonoarm.apk
# SHA-256 verificado contra el .sha256 oficial:
#   16992661095c901b017fe6e01a1d663925e22d3a6caec6d814a61e829d817e1b
adb -s 192.168.1.74:5555 install /tmp/BraveMonoarm.apk
# Config Shields: brave://settings/shields → bloqueo de anuncios/trackers ON
#                  brave://settings/content → "Reduce device identifier" / "block scripts" 
#                  brave://settings/performance → "Efficiency mode" (desactiva carga de ads)
```

## 4. Escalas de animación: actual vs recomendado

Valores actuales medidos: `window_animation_scale=1.0`, `transition_animation_scale=1.0`, `animator_duration_scale=null` (Android lo interpreta como 1.0).

| Preferencia | Actual | Recomendado | Efecto | Riesgo |
|---|---|---|---|---|
| `window_animation_scale` | 1.0 | **0.5** | Recorta a la mitad la apertura/cierre de ventanas | Bajo — Feedback aún perceptible |
| `transition_animation_scale` | 1.0 | **0.5** | Menos latencia percibida al cambiar de app/panel | Bajo |
| `animator_duration_scale` | null (1.0) | **0.5** | Menos frames de animación de propiedades (ListView, ripples) | Bajo |
| (opcional, agresivo) los tres | 1.0 | 0.0 | Respuesta instantánea | Medio — se pierde el feedback visual de "cargando" |

Comando **no ejecutado** (queda a decisión del usuario):
```bash
adb -s 192.168.1.74:5555 shell settings put global window_animation_scale 0.5
adb -s 192.168.1.74:5555 shell settings put global transition_animation_scale 0.5
adb -s 192.168.1.74:5555 shell settings put global animator_duration_scale 0.5
```

**Riesgo con control remoto (argumentado):**
1. El TV es `leanback_only` con `RC860U Mouse` (CURSOR/MIC). El ratón mueve un **cursor**; el DPAD mueve el foco. Las animaciones de ventana son precisamente las que el usuario ve como "el sistema tarda" cuando navega con DPAD, porque cada cambio de app/panel lanza una transición.
2. Con `load avg 18` sobre 4 cores y 556 MB libres de 1.8 GB, cada capa de animación añade frames que compiten con el compositor. Bajar a 0.5 no elimina la animación: **reduce el número de frames que hay que componer** en una CPU ya saturada. Es la palanca con mejor relación esfuerzo/beneficio.
3. **No bajar a 0.0 de forma ciega**: con uBO instalado y sin pantalla táctil, el usuario puede interpretar un cambio instantáneo de estado como "el navegador no ha cargado". Un 0.5 conserva feedback. Si aun así se elige 0.0, conviene activar `show_touches` (o `pointer_location`) para depurar, sabiendo que en este TV no hay hardware táctil.
4. `animator_duration_scale=null` es el valor por defecto de fábrica; fijarlo a 0.5 es inocuo y explícito.

## 5. Ajustes de render dentro del navegador (propuestos, no aplicados)

Extraídos del propio `omni.ja` de Firefox 158.0.2 (`armeabi-v7a/greprefs.js`, `defaults/pref/armeabi-v7a/geckoview-prefs.js`).

### Firefox (about:config) — seguros
| Pref | Valor | Por qué |
|---|---|---|
| `dom.ipc.processPrelaunch.enabled` | false | El prelaunch de procesos de contenido reserva RAM; con 1.8 GB es counterproductive. |
| `browser.sessionstore.max_tabs_undo` | 0-5 | Reduce RAM de sesión. (actual 10) |
| `dom.max_script_run_time` | 10 | actual 20; corta scripts colgados. |
| `browser.tabs.unloadOnLowMemory` | true | Libera pestañas inactivas. |
| `privacy.resistFingerprinting` | true | menos variación de canvas → menos recomposición. |

### Firefox — SOMETIDOS A PRUEBA (no aplicar sin medir)
| Pref | Riesgo |
|---|---|
| `gfx.webrender.software` / `gfx.webrender.all=false` | El TV **sí tiene** GPU (PowerVR BXE-4-32, GLES 3.2). Forzar render por software **empeoraría** el scroll. Solo como último recurso si se observan glitches. |
| `layers.acceleration.disabled=true` | Desactiva la aceleración por hardware → render en CPU. Contraproducente aquí. |
| `gfx.canvas.accelerated=false` | Afecta solo a canvas 2D; ahorro marginal, riesgo de manchas en gráficas. |

### Brave (chrome://flags) — solo experimental
- Brave 1.98.60 en Android no expone flags de extensiones utilizables; el soporte está en desarrollo (veredicto NO INSTALABLE arriba).

## 6. LIMITACIONES

1. **Sin root** (uid 2000): no se pudo inspeccionar `/data/data` ni el contenido de WebView/GMS.
2. **No se instaló nada ni se cambió ningún setting** — por instrucción explícita. Los veredictos de "instalable" se derivan de la **inspección binaria** de los APK oficiales (manifiesto AXML parseado + directorio central del ZIP), no de una instalación real.
3. **La descarga completa del APK de Firefox 125.572.718 B no se completó** por el ancho de banda del enlace; se verificó vía **ranged reads** del directorio central ZIP (EOCD + CD) y extracción puntual de `AndroidManifest.xml`, `assets/omni.ja` (índice) y `greprefs.js`. La evidencia de ABIs (15 .so en `armeabi-v7a`, 0 en arm64) sale del CD completo.
4. **La URL de descarga oficial de Firefox para Android no es resoluble por HTTP directo** (`download.mozilla.org?product=firefox-android*` → 404; `archive.mozilla.org/pub/firefox/releases/<v>/android-arm/` → 404). Mozilla solo publica el APK Android vía Play Store y F-Droid. Se usó F-Droid.
5. No se midió el **rendimiento real** de render (sin instalar). Las recomendaciones de GPU son teóricas, no benchmarked.
6. `animator_duration_scale=null` no se pudo resolver a un valor efectivo sin `settings` de lectura escalonada; se asume el default de Android (1.0).
7. La **interfaz TV de Brave/Firefox es inexistente**: no hay `android.software.leanback` en ninguno de los dos manifiestos, así que se instalarían como apps de móvil/tablet en un launcher `leanback_only`. No son "navegadores de TV".

## 7. RECOMENDACIONES

### [SEGURO]
- **Instalar Firefox 158.0.2 32-bit** (Fennec F-Droid) — es la única opción que da **uBlock Origin + Privacy Badger** funcionando, y su ABI casa exactamente con el TV.
- Reducir las tres escalas de animación a **0.5** (no 0.0).
- Fijar `dom.ipc.processPrelaunch.enabled=false` y `browser.sessionstore.max_tabs_undo` ≤5 en `about:config` de Firefox.
- Activar Tracking Protection **Estándar** (no Estricto) en Firefox: el modo estricto añade consultas de DNS/canvas y penaliza el render en un Cortex de 4 núcleos.
- Instalar Brave 1.98.60 `BraveMonoarm.apk` **solo** si se prefiere su bloqueador nativo (Shields) al de uBO, asumiendo que **no** se podrán instalar extensiones.

### [RIESGO]
- Usar `gfx.webrender.software` o `layers.acceleration.disabled=true` sin medir antes: el TV tiene GPU real; es probable que **empeore** el scroll.
- Poner las escalas de animación a 0.0: se pierde el feedback de "cargando", confuso sin Extensions y con navegación por DPAD.
- Instalar un APK **arm64** en este TV: fallará con `INSTALL_FAILED_NO_MATCHING_ABIS`.
- Instalar por Play Store confianzos en que el dispositivo "recibe la variante correcta": Play hace split por ABI, y aquí solo existe `armeabi-v7a`; hay que verificar la variante servida.

### [NO HACER]
- **NO** instalar `Bravearm64Universal.apk` ni `BraveMonoarm64.apk` (contienen arm64): incompatible con este TV 32-bit.
- **NO** instalar `BraveMonox64.apk` / `BraveMonox86.apk` (x86): incompatibles.
- **NO** buscar uBlock Origin / Privacy Badger en Brave: no hay soporte de extensiones en Android estable. Perderá tiempo y quizá instale un APK no fiable.
- **NO** usar `adb root`, `remount`, `pm disable`, `settings put` masivo ni escrituras a `/data`: sesión no root y fuera del alcance autorizado.
- **NO** instalar el APK de F-Droid de Firefox x86_64 (1580210) ni arm64 (1580220) por error de nombre de archivo: los que sirven son `armeabi-v7a` = **1580200**.

## 8. Rutas de artefactos

Todo en `/tmp/tvdiag/ux/`:

| Archivo | Contenido |
|---|---|
| `build_props.txt` | ABI, fingerprint, characteristics, fecha, SDK |
| `libs_anim.txt` | `ls /system/lib64` (inexistente), `/system/lib` (640), escalas de animación |
| `vending_df_input.txt` | versionName de vending, `df -h /data`, `dumpsys input` (RC860U Mouse) |
| `pm_all.txt` | lista completa de paquetes (187 upgradeables) |
| `gms_webview.txt` | ABI de vending/GMS/WebView, `com.google.android.webview` 155.0.8059.43 |
| `mem_cpu.txt` | RAM 1.895.540K, 4 cores ARMv7 |
| `gpu_cpu.txt` | SurfaceFlinger → PowerVR BXE-4-32, GLES 3.2 |
| `features_final.txt` | `pm list features` (leanback_only), escalas, show_touches |
| `BraveMonoarm.apk` | APK oficial Brave 1.98.60 arm32 (231 MB, SHA-256 verificado) |
| `brave.sha256` / `brave_assets_full.txt` | checksum oficial + los 213 assets del release |
| `brave_manifest_full.txt` / `brave_manifest_tree.json` | manifiesto AXML de Brave parseado |
| `fennec_ents.json` / `fennec_cd.json` | directorio central ZIP del APK de Firefox 158.0.2 (ABIs) |
| `fennec_manifest.bin` | AndroidManifest.xml de Firefox (parseado: minSdk 26, GLES 2.0, 536 nodos) |
| `fennec_omni_names.json` | 5129 entradas de `assets/omni.ja` (incluye todo el stack WebExtensions) |
| `fennec_armeabi-v7a_greprefs.js` | prefs por defecto de Firefox 158 arm32 |
| `fennec_defaults_pref_armeabi-v7a_geckoview-prefs.js` | prefs GeckoView (render/memoria) |
| `amo_ublock-origin.json` / `amo_privacy-badger17.json` | AMO API v5 (compatibility, promoted) |
| `amo_full_*.json` | AMO v5 detalle de versión |
| `brave_fdroid_v1.json` | índice F-Droid oficial de Brave (485 APKs, 97 armeabi-v7a) |
| `fd_page.html` | página F-Droid de Fennec (versiones + nativecode + minSdk) |
| `axml.py` | parser AXML reutilizable (extracto de manifest binario) |
| `VERDICTOS.md` | este documento |
