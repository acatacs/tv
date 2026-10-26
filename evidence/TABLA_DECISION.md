# Inventario de paquetes — Kalley R10G / MOKA (Android 14, SDK 34, armeabi-v7a)

Fecha: 2026-10-26 · ADB: 192.168.1.74:5555 (sesión no-root, uid 2000)

Leyenda de tamaño: total_MB = apk + data + cache, según `dumpsys diskstats` (Package Names / App Sizes / App Data Sizes / Cache Sizes). Los valores de `du -sh /data/app/*` fallaron con "Permission denied" (uid 2000); por eso el tamaño se obtuvo de `dumpsys diskstats` + `stat -c %s` de los APK y `pm path`.

**Launcher activo (NO TOCAR): `com.google.android.apps.tv.launcherx` (Google TV / TVLauncherX), `com.google.android.apps.tv.launcherx/.home.HomeActivity`, versionName 1.0.994451607.**
Comando: `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` → `priority=2 isDefault=true`.
`settings get secure home_component` → `null` (Android 14 no persiste ahí; el HOME por defecto lo resuelve el sistema por prioridad de filtro).
El launcher de proveedor `com.realtek.leanbacklauncher.partnercustomizer` (5.6.250922, /system_ext/app/CustomerLaucher) NO expone actividad HOME → no es un launcher activo, solo un configurador del CustomerLaucher.

---

## Tabla principal

| # | Paquete | sistema/usuario | Tamaño (apk/data/cache MB → total) | Función | Decisión | Razón |
|---|---|---|---|---|---|---|
| 1 | com.google.android.apps.tv.launcherx | SISTEMA (UPDATED_SYSTEM_APP, /data/app) | 46.1 / 13.2 / 9.1 → **68.5 MB** | Google TV launcher (HOME) | **NO TOCAR** | Es el HOME activo y por defecto; 144 MB RSS + 85 MB del proceso `coreservices` = el mayor consumidor de RAM del dispositivo. Deshabilitarlo deja la TV sin home y hace caer a `com.realtek.systemservice/.fallbackhome.FallbackHome` o `com.android.tv.settings/.system.FallbackHome`. |
| 2 | com.google.android.tts | SISTEMA (UPDATED_SYSTEM_APP, /data/app) | 86.3 / **154.1** / 1.3 → **241.7 MB** | Google Text-to-Speech (voz, pronunciación de búsqueda) | **MANTENER** | Es el paquete #1 en espacio (154 MB de "data" = modelos de voz descargados). Es functionality de la barra de búsqueda de Google TV, no bloat: se pierde la búsqueda por voz si se deshabilita. Impacto: 241 MB pero necesaria. |
| 3 | com.google.android.gms | SISTEMA (UPDATED_SYSTEM_APP) | 158.4 / 67.0 / 6.8 → **232.2 MB** | Play Services (Fused Location, Cast, Fitness, ads) | **MANTENER** | Proceso activo (137 MB RSS). Requerido por Katniss/Google TV y por Cast. Riesgo de romper: ALTO. |
| 4 | com.google.android.gsf | SISTEMA (prebuilt) | 0.0 / 0.3 / 0.0 → **0.4 MB** | Google Services Framework (registro de cuenta) | **MANTENER** | Hay una cuenta Google activa (`mc102579@gmail.com`, tipo `com.google`). Deshabilitarlo rompe sincronización y Play Store. Tamaño irrelevante. |
| 5 | com.android.vending | SISTEMA (UPDATED_SYSTEM_APP, prebuilt Tubesky) | 47.9 / 8.0 / 1.5 → **57.4 MB** | Google Play Store (Tubesky TV) | **MANTENER** | versionName 53.4.17-34 [8] [PR] 988500596. Habilitado (enabled=1) y con 2 procesos vivos (129 MB + 110 MB RSS). Si se quiere aligerar sin romper nada: deshabilitar Play Games/YouTube Music es más seguro que el Store. |
| 6 | com.google.android.katniss | SISTEMA (UPDATED_SYSTEM_APP) | 46.2 / 2.7 / 0.0 → **48.9 MB** | Asistente de búsqueda por voz de Google TV | **MANTENER** | 98 MB RSS del proceso `interactor`. Es la búsqueda por voz del launcher. Deshabilitarlo afecta directamente al HOME. |
| 7 | com.google.android.webview | SISTEMA (prebuilt) | 94.4 / 0.1 / 0.0 → **94.5 MB** | WebView de Google (motor de las WebView de las apps) | **MANTENER** | Requerido por las apps de streaming y por los OAuth de Play Store. Riesgo: ALTO. |
| 8 | com.google.android.inputmethod.latin | SISTEMA (UPDATED_SYSTEM_APP) | 30.0 / 37.4 / **29.0 cache** → **96.4 MB** | Teclado Gboard para TV | **MANTENER** | Sin teclado no se puede escribir en la búsqueda. Los 29 MB de caché son recuperables (clear cache, no disable). |
| 9 | com.google.android.apps.tv.dreamx | SISTEMA (UPDATED_SYSTEM_APP) | 31.1 / 10.8 / 0.1 → **42.1 MB** | Screensaver de Google TV (DreamX) | **DESACTIVAR-SEGURO** | No es el HOME; es únicamente el fondo de inactividad. 73 MB RSS constante. Afecta solo al screensaver. Impacto: MEDIO. |
| 10 | com.google.android.tungsten.setupwraith | SISTEMA (UPDATED_SYSTEM_APP) | 28.3 / 2.1 / 1.5 → **31.9 MB** | Asistente de configuración de Google TV (SetupWraith) | **MANTENER** | Es el que se abre en el primer arranque y configura la cuenta. Sin él, la TV no completa el setup. 68 MB RSS. NO tocar pese al tamaño. |
| 11 | com.google.android.tv.remote.service | SISTEMA (UPDATED_SYSTEM_APP) | 7.1 / 0.2 / 0.2 → **7.5 MB** | Mando a distancia (AtvRemoteService) | **MANTENER** | 64 MB RSS, es el servicio del mando. Crítico. |
| 12 | com.google.android.tv.dfuservice | SISTEMA (UPDATED_SYSTEM_APP) | 3.9 / 0.5 / 0.2 → **4.6 MB** | Actualizaciones de firmware por OTA (DFU) | **MANTENER** | Riesgo de dejar la TV sin mecanismo de actualización. Tamaño mínimo. |
| 13 | com.google.android.apps.mediashell | SISTEMA (prebuilt) | 1.6 / 0.3 / 0.2 → **2.1 MB** | MediaShell (shell de medios de Google TV) | **MANTENER** | 70 MB RSS. Integrante del stack de Google TV. |
| 14 | com.google.android.partnersetup | SISTEMA (prebuilt) | 0.0 / 0.2 / 0.1 → **0.2 MB** | Partner Setup de Google | **MANTENER** | Tamaño irrelevante, riesgo de romper setup. |
| 15 | com.google.android.tv.googletvremotepairer | SISTEMA (prebuilt) | 0.0 / 0.1 / 0.1 → **0.3 MB** | Emparejamiento del mando | **MANTENER** | Crítico para el mando. |
| 16 | com.google.android.syncadapters.calendar | SISTEMA (prebuilt) | 4.8 / 1.2 / 0.4 → **6.4 MB** | Sincronizador de calendario Google | **DESACTIVAR-SEGURO** | No afecta al launcher, la búsqueda ni el streaming. Impacto: BAJO. |
| 17 | com.google.android.youtube.tv | SISTEMA (prebuilt) | 82.7 / 18.6 / 9.8 → **111.1 MB** | YouTube (versión TV, live TV) | **MANTENER** | Es la app de streaming principal y la que la usa el launcher para recomendaciones. Impacto de deshabilitar: ALTO para el usuario. |
| 18 | com.google.android.youtube.tvmusic | SISTEMA (UPDATED_SYSTEM_APP) | 4.6 / 0.0 / 0.0 → **4.6 MB** | YouTube Music para TV | **DESACTIVAR-SEGURO** | No interviene en el HOME ni en la búsqueda. 4.6 MB + un proceso. Impacto: BAJO. |
| 19 | com.google.android.play.games | SISTEMA (UPDATED_SYSTEM_APP) | 24.7 / 1.5 / 1.1 → **27.2 MB** | Google Play Games (servicio de juegos) | **DESACTIVAR-SEGURO** | Solo juegos. La TV no tiene mando de juegos configurado (solo aparece `com.apps.mk.gameui` como launcher de juegos). Impacto: BAJO. |
| 20 | com.netflix.ninja | SISTEMA (prebuilt /system/app/Netflix) | 87.2 / 12.9 / 0.0 → **100.0 MB** | Netflix | **MANTENER** | Streaming en uso (76 MB RSS activo). Preinstalado en la imagen: no se puede desinstalar, solo deshabilitar. Impacto para el usuario: ALTO. |
| 21 | com.amazon.amazonvideo.livingroom | SISTEMA (prebuilt /system/app/PrimeVideo) | 25.0 / 14.4 / 0.0 → **39.5 MB** | Prime Video | **MANTENER** | Proceso vivo con 84 MB RSS. Preinstalado. Impacto usuario: MEDIO-ALTO. |
| 22 | com.mk.tv.gallery | SISTEMA (UPDATED_SYSTEM_APP) | 119.1 / 0.3 / 0.0 → **119.5 MB** | Galería de fotos/mode Gallery (AIST) del proveedor | **DESACTIVAR-SEGURO** | 119.5 MB y 107 MB de APK para un visor de fotos que en una TV de salón casi no se usa. No toca el HOME. Impacto: MEDIO. |
| 23 | com.mk.tv.caption | SISTEMA (UPDATED_SYSTEM_APP) | 76.1 / 0.0 / 0.0 → **76.1 MB** | Subtítulos / AIST caption | **DESACTIVAR-SEGURO** | Solo subtítulos; no interviene en el launcher. Si el usuario no usa subtítulos, libera 76.1 MB. Impacto: MEDIO. |
| 24 | com.apps.mk.gameui | SISTEMA (prebuilt /system_ext/app/GameUI) | 1.3 / 0.0 / 0.0 → **1.3 MB** | UI de juegos del proveedor | **DESACTIVAR-SEGURO** | Es el launcher de juegos del fabricante; 78 MB RSS, el 4º consumidor de RAM de apps. No interviene en el HOME. Impacto: BAJO. |
| 25 | com.apps.mk.sk | SISTEMA (SmartKey2) | 0.0 / 0.6 / 0.5 → **1.1 MB** | SmartKey (teclas rápidas) | **MANTENER** | 1 MB, riesgo de perder atajos del mando. No worthwhile. |
| 26 | com.creative.fastscreen.tv | SISTEMA (FastCastTV) | 10.2 / 0.3 / 0.3 → **10.8 MB** | FastCast (replicación de pantalla) | **DESACTIVAR-SEGURO** | 10.8 MB y 2 actividades LAUNCHER (incluye `.activity.ad.Advertisement`). No afecta al HOME. Impacto: BAJO. |
| 27 | com.apps.tvmanager | SISTEMA (prebuilt) | 0.0 / 0.1 / 0.0 → **0.1 MB** | Gestor de TV del fabricante | **MANTENER** | Tamaño nulo, riesgo de romper gestión de canales. |
| 28 | com.apps.factory.ui | SISTEMA (prebuilt) | 0.0 / 0.1 / 0.1 → **0.2 MB** | UI de fábrica (modo factory) | **MANTENER** | 0.2 MB. No tocar. |
| 29 | com.apps.biz.mt | SISTEMA (TVMeetingMode) | 6.8 / 0.0 / 0.0 → **6.8 MB** | Modo reunión / videollamada | **DESACTIVAR-SEGURO** | 6.8 MB, launcher LAUNCHER, no es el HOME. Impacto: BAJO. |
| 30 | com.realtek.airplay2 | SISTEMA (RtkAirPlay) | 0.0 / 15.0 / 0.1 → **15.1 MB** | AirPlay 2 | **MANTENER** | 15 MB de datos acumulados por el servicio. Es una función de la TV, no bloat. |
| 31 | com.realtek.karaoke.userguide | SISTEMA (prebuilt) | 2.3 / 0.0 / 0.0 → **2.3 MB** | Guía de karaoke | **DESACTIVAR-SEGURO** | 2.3 MB, irrelevante en TV sinEntrada de micrófono configurada. Impacto: BAJO. |
| 32 | com.usc.scmanager.moka | SISTEMA (SCManager, /system/app) | 0.0 / 0.0 / 0.0 → **~0.1 MB** | Moka Security Manager (gestor de seguridad del fabricante) | **NO TOCAR** | Ya está `hidden=true` y `enabled=3` (DISABLED_UNTIL_USED). Gestiona la seguridad/autorización del sistema MOKA. Con permisos de CALL_PHONE, RECORD_AUDIO, READ_CONTACTS, BACKGROUND_LOCATION, etc. Deshabilitarlo puede romper el framework de seguridad. Tamaño irrelevante. |
| 33 | com.apps.secserviceoverlay | SISTEMA (overlay) | 0.0 / 0.0 / 0.0 → **~0.1 MB** | Overlay de seguridad Moka | **NO TOCAR** | 0.1 MB, parte del mismo framework. |
| 34 | com.realtek.leanbacklauncher.partnercustomizer | SISTEMA (/system_ext/app/CustomerLaucher) | 3.3 / 0.0 / 0.0 → **3.5 MB** | Configurador del CustomerLaucher del proveedor | **NO TOCAR** | Aunque el nombre "launcher" sugiere que es un launcher alternativo, **no expone actividad HOME** (resolver devuelve launcherx). Es el mecanismo por el que el proveedor mete su logo/columnas en el launcher de Google. Deshabilitarlo rompe la personalización del HOME. |
| 35 | com.wbd.stream | USUARIO (instalado) | 135.7 / 5.4 / 0.0 → **141.1 MB** | Max (Warner Bros. Discovery) | **DESACTIVAR-SEGURO** (o desinstalar desde Play Store) | App de usuario, la mayor de las no-sistema. No toca el HOME. Impacto si el usuario no la usa: ALTO en espacio (141 MB), MEDIO en RAM. |
| 36 | com.disney.disneyplus | USUARIO (instalado) | 91.1 / 0.0 / 0.0 → **91.1 MB** | Disney+ | **DESACTIVAR-SEGURO** | App de usuario, stopped=true/notLaunched=true → no se ha usado desde la instalación. 91.1 MB. |
| 37 | com.google.android.youtube.tvunplugged | USUARIO (instalado) | 74.7 / 2.8 / 1.5 → **79.0 MB** | YouTube TV (versión completa) | **DESACTIVAR-SEGURO** | Duplica `com.google.android.youtube.tv` (111 MB) que ya está preinstalada. Nota: ambas funcionan; no confundir con `com.google.android.youtube.tv` (SISTEMA, MANTENER). Impacto: MEDIO. |
| 38 | com.hulu.livingroomplus | USUARIO (instalado) | 46.0 / 0.0 / 0.0 → **46.0 MB** | Hulu | **DESACTIVAR-SEGURO** | stopped=true/notLaunched=true, nunca abierta. 46 MB. |
| 39 | com.apple.atve.androidtv.appletv | USUARIO (instalado) | 37.4 / 0.8 / 0.2 → **38.5 MB** | Apple TV app | **DESACTIVAR-SEGURO** | 38.5 MB, solo útil con Apple TV. |
| 40 | com.peacocktv.peacockandroid | USUARIO (instalado) | 22.1 / 0.6 / 0.0 → **22.7 MB** | Peacock | **DESACTIVAR-SEGURO** | 22.7 MB. |
| 41 | com.tubitv | USUARIO (instalado) | 0.2 / 0.0 / 0.0 → **0.2 MB** | Tubi (stub, app no descargada) | **MANTENER** | Solo 0.2 MB: es un stub de launcher que delega en la web. No compensa tocarlo. |
| 42 | com.instagram.airwave | USUARIO (instalado) | 0.3 / 0.0 / 0.0 → **0.3 MB** | Instagram (stub, app no descargada) | **MANTENER** | Solo 0.3 MB, stub. No compensa. |
| 43 | com.netflix.tokenmanager | SISTEMA (RtkNetflixTokenManager) | ~0.0 | Token manager de Netflix | **NO TOCAR** | Dependencia de autenticación de Netflix. 0 MB. |
| 44 | com.google.android.healthconnect.controller | SISTEMA (APEX) | 0.1 / 0.0 / 0.0 → **0.1 MB** | Health Connect | **DESACTIVAR-SEGURO** | Sin wearables conectados. 0.1 MB, irrelevante pero inocuo. Impacto: BAJO. |
| 45 | com.google.android.adservices.api | SISTEMA (APEX) | 0.2 MB | Privacy Sandbox / Adservices | **DESACTIVAR-SEGURO** | 0.2 MB, sin apps que lo usen. Impacto: BAJO. |
| 46 | com.google.android.ondevicepersonalization.services | SISTEMA (APEX) | 0.1 MB | Personalización on-device | **DESACTIVAR-SEGURO** | 0.1 MB. Impacto: BAJO. |
| 47 | com.android.dynsystem, com.android.shell, com.android.dynsystem (overlay GPS), com.android.tuning, etc. | SISTEMA | 0.1–0.3 MB c/u | Componentes AOSP dinámicos | **NO TOCAR** | <0.5 MB cada uno. Desactivar módulos dinámicos de AOSP puede dejar el sistema inestable sin ganancia. |
| 48 | com.android.systemui, com.android.providers.*, com.android.bluetooth, com.android.networkstack, com.android.devicelockcontroller, com.android.location.fused, com.android.performance, etc. | SISTEMA (APEX/priv-app) | 0.4–2.1 MB | Framework AOSP (SystemUI, providers, BT, red, seguro) | **NO TOCAR** | Volcadas de framework con permisos de plataforma. Sin bypass, deshabilitar = inestabilidad. |
| 49 | com.realtek.tv, com.realtek.systemservice, com.realtek.tv.atsc/isdb/dtv, com.realtek.dtv, com.realtek.globalkeyhandler, com.realtek.tv.*, com.realtek.* overlay | SISTEMA (Rtk*) | 0.1–0.4 MB c/u (~50 paquetes) | Hardware Realtek: tuner DTV/ATSC/ISDB, IR, overlays, energia, etc. | **NO TOCAR** | Son el soporte del tuner y de las teclas; pequeños y de riesgo alto. `com.realtek.systemservice/.fallbackhome.FallbackHome` es uno de los fallbacks del HOME. |
| 50 | com.apps.* (wifihotspot, otа, esticker3, scast, puresound, fivekey, virtualkey, usbmediaplayer, plugins, sys, authota, factory.ui, mk.sk, mk.gameui, biz.mt) | SISTEMA (MOKA/apps) | 0.1–3.0 MB c/u | Utilidades del fabricante (hotspot, USB media, e-sticker, sonido, cinco teclas) | **NO TOCAR** salvo los marcados arriba | Pequeños; salvo gameui/biz.mt ya marcados, tocarlos no aporta espacio y sí riesgo funcional. |
| 51 | com.android.tv.frameworkpackagestubs | SISTEMA | 0.1 MB | Stubs de framework TV | **NO TOCAR** | Es el que resuelve el intent BROWSABLE (`com.android.tv.frameworkpackagestubs/.Stubs$BrowserStub`). Es el "navegador" por defecto de Android TV. |
| 52 | com.android.htmlviewer | SISTEMA | 0.1 MB | Visor HTML | **NO TOCAR** | 0.1 MB. |
| 53 | com.google.android.feedback, com.google.android.safetycenter.resources, com.google.android.ext.services, com.google.android.ext.shared, com.google.android.modulemetadata, overlays Google, com.android.wifi.dialog, com.android.federatedcompute.services, com.android.hotspot2.osulogin, com.android.sdksandbox, com.android.location.fused | SISTEMA | 0.05–1.1 MB c/u | Servicios/overlays de Google y AOSP | **NO TOCAR** | Todos < 1.2 MB salvo ext.services (0.1 MB). No hay ganancia. |
| 54 | com.viso.mdm | SISTEMA (/system/app/mdm) | <0.1 MB | MDM corporativo (VISO) | **NO TOCAR** | MDM del fabricante; tamaño nulo. |
| 55 | **Sin navegador de terceros instalado** | — | — | — | — | Búsqueda de `brave|chrome|browser|phlox|tvbro|firefox|opera|edge|kiwi|duckduck` en `pm list packages -f`: **solo** `com.google.android.webview` y `com.google.android.overlay.googlewebview`. El intent BROWSABLE resuelve a `com.android.tv.frameworkpackagestubs/.Stubs$BrowserStub` (stub, no navegador real). No hay Chrome, Brave ni TvBro. Cualquier web la abre el stub de Android TV. |

---

## Resumen cuantitativo

- Paquetes totales: **187** (177 `pm list packages -s` + 8 `-3` + 2 sólo `-u`: `com.usc.scmanager.moka`, y duplicado de launcherx). `pm list packages -d` → **0 paquetes deshabilitados**.
- Espacio total de apps (apk+data+cache): **1895.5 MB** (`dumpsys diskstats`: App Size 1.51 GB + App Data 404 MB + App Cache 72.7 MB).
- `/data`: 9.1 GB totales, 2.4 GB usados, **6.5 GB libres (71 % free)**. → **El espacio NO es el problema; la RAM sí.**
- RAM: 1895 MB totales, 1660 MB usados, 225 MB libres, swap 1 GB con 426 MB usado. `system_server` = 252 MB RES (13.6 %), `top` con load 18.6 y 189 % user CPU.
- RSS de apps (u0_*): **1660.8 MB en 22 procesos**. Top: launcherx 144 MB + launcherx:coreservices 85 MB, gms 137 MB, vending 129 + vending:background 110 MB, katniss:interactor 98 MB, Prime Video 84 MB, gameui 78 MB, Netflix 76 MB, youtube.tvunplugged 74 MB, dreamx 73 MB, puresound 71 MB, mediashell 71 MB, latin IME 70 MB, setupwraith 69 MB, tv.remote.service 64 MB.

### Ahorro estimado por escenario

| Escenario | Paquetes | Espacio recuperado | RAM liberada (estimada por RSS) | Riesgo |
|---|---|---|---|---|
| A — Solo apps de usuario no usadas | wbd.stream, disneyplus, youtube.tvunplugged, hulu, appletv, peacock | **≈ 419 MB** | bajo | NINGUNO (no tocan el HOME) |
| B — A + bloat de sistema de bajo impacto | + dreamx, mk.tv.gallery, mk.tv.caption, play.games, youtube.tvmusic, syncadapters.calendar, fastscreen.tv, biz.mt, karaoke.userguide, gameui | **≈ 707 MB** | ~250 MB (dreamx 73 + gameui 78 + puresound/medias…) | BAJO-MEDIO |
| C — B + Netflix / Prime Video / YouTube TV | + netflix.ninja, amazon.amazonvideo.livingroom, google.android.youtube.tv | **≈ 940 MB** | ~240 MB adicionales | MEDIO (pierde streaming) |

**Recomendación principal: escenario A o B.** No tocar `com.google.android.apps.tv.launcherx` en ningún escenario; es el HOME por defecto y su eliminación/deshabilitado rompe el acceso a la TV.

### Nota sobre `com.google.android.tts` (241.7 MB)
El APK de `/data/app` son 26.8 MB (base) y el prebuilt de `/product/priv-app/GoogleTTS` 60.4 MB; los 154 MB de "data" son **modelos de voz descargados** (idiomas). Es la función de voz de la barra de búsqueda de Google TV. Si el usuario no usa búsqueda por voz, es el mayor candidato a liberar (≈ 154 MB de datos) — pero NO se puede "desactivar" parcialmente: se puede hacer "clear data" desde Ajustes, lo que obliga a re-descargar el modelo. Se deja como **MANTENER** con nota.

---

## LIMITACIONES

1. `du -sh /data/app/*` → `du: /data/app/*: No such file or directory` y `ls /data/app` → `Permission denied`. Sesión ADB no root (uid 2000). Los tamaños provienen de `dumpsys diskstats` (valores del StorageManager, no del filesystem) y de `stat -c %s` sobre los APK; el "app size" incluye el APK en /data, no el prebuilt de /product o /system.
2. `settings get secure home_component` → `null`: en Android 14 el HOME por defecto se resuelve por `isDefault=true` y prioridad del filtro, no por `home_component`. La identidad del launcher se establece con `cmd package resolve-activity` (evidencia directa).
3. No se pudo confirmar qué paquete está "detrás" de `com.usc.scmanager.moka` más allá de `codePath=/system/app/SCManager` y `versionName=12.11.1.252`; sí se confirmó que ya está `hidden=true, enabled=3`.
4. `top` con `-o %MEM` devolvió solo la columna de porcentaje sin nombres (bug del toybox en este build); se resolvió con `top -b -n 1 -m 20` y `ps -A -o USER,PID,RSS,NAME`.
5. No se midió el consumo de CPU por app individual (solo global: 189 % user, load 18.6) ni el impacto en temperatura; fuera del alcance de esta tarea.
6. `split_config.*.apk` no se pudo listar en bloque por glob (`stat /data/app/*/*/split*.apk` → vacío, sin permisos de lectura del directorio); solo se enumeraron vía `pm path` para TTS, Netflix, Hulu y tubitv.
7. Los tamaños de "data" de apps de usuario (WBD 5.4 MB, etc.) son los reportados por el framework, sin verificación independiente por permisos de directorio.

## RECOMENDACIONES

**[SEGURO]**
- Desinstalar desde Play Store o `pm uninstall --user 0` las apps de usuario que no se usen: `com.wbd.stream`, `com.disney.disneyplus`, `com.google.android.youtube.tvunplugged`, `com.hulu.livingroomplus`, `com.apple.atve.androidtv.appletv`, `com.peacocktv.peacockandroid` → **≈ 419 MB** sin riesgo para el launcher.
- `pm disable-user --user 0` (reversible con `pm enable`) para bloat de sistema de bajo impacto: `com.google.android.apps.tv.dreamx` (screensaver, 42 MB + 73 MB RSS), `com.google.android.play.games` (27 MB), `com.google.android.youtube.tvmusic` (4.6 MB), `com.google.android.syncadapters.calendar` (6.4 MB), `com.creative.fastscreen.tv` (10.8 MB), `com.apps.biz.mt` (6.8 MB), `com.realtek.karaoke.userguide` (2.3 MB).
- Cambiar el fondo de inactividad del launcher a "Pantalla negra" en Ajustes de Google TV: apaga dreamx sin tocar el HOME.
- Ajustes → Aplicaciones → `com.google.android.inputmethod.latin` → "Liberar caché" (29 MB) y `com.google.android.tts` → "Liberar caché" (1.3 MB).
- Medir después: `top -b -n 1 -m 20` y `dumpsys diskstats` para confirmar la mejora.

**[RIESGO]**
- `pm disable-user` de `com.netflix.ninja` (100 MB), `com.amazon.amazonvideo.livingroom` (39.5 MB) o `com.google.android.youtube.tv` (111 MB): libera espacio pero corta el streaming que el usuario está usando (Netflix y Prime Video tienen proceso vivo). Solo si el usuario confirma que no los usa.
- Deshabilitar `com.mk.tv.gallery` (119.5 MB) o `com.mk.tv.caption` (76.1 MB): son modos del proveedor; no afectan al HOME, pero se pierde la galería de fotos y los subtítulos.
- `pm uninstall --user 0` de `com.google.android.tts`: libera ~154 MB de modelos de voz, pero se pierde la búsqueda por voz de Google TV (el launcher sigue funcionando). Requiere confirmación explícita del usuario.
- Cambiar el launcher por defecto (p. ej. al CustomerLaucher de Realtek) NO es viable: `com.realtek.leanbacklauncher.partnercustomizer` no expone actividad HOME.

**[NO HACER]**
- **NO tocar `com.google.android.apps.tv.launcherx`** (ni deshabilitarlo, ni desinstalarlo, ni cambiar `home_component`). Es el HOME activo (`priority=2 isDefault=true`), el mayor consumidor de RAM (144+85 MB) y su pérdida deja la TV en un fallback sin interfaz de usuario.
- NO tocar `com.google.android.katniss`, `com.google.android.tungsten.setupwraith`, `com.google.android.tv.remote.service`, `com.google.android.tv.googletvremotepairer`: son la búsqueda por voz, el setup inicial y el mando.
- NO tocar `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending`: hay una cuenta Google activa (`mc102579@gmail.com`); romper el framework deja la TV sin Play, sin sincronización y sin Cast.
- NO tocar `com.google.android.webview` (94.5 MB): motor de las WebView de todas las apps y de los OAuth de Play Store.
- NO tocar `com.usc.scmanager.moka` ni `com.apps.secserviceoverlay`: framework de seguridad del fabricante con permisos de alto nivel (CALL_PHONE, RECORD_AUDIO, BACKGROUND_LOCATION). Ya están ocultos/deshabilitados por el propio sistema.
- NO tocar `com.realtek.*` (tuner DTV/ATSC/ISDB, IR/globalkey, overlays, frameworkoverlay, sistema de respaldo del HOME) ni `com.apps.mk.*`, `com.apps.*` de utilidad: son soporte de hardware y de teclado/mando.
- NO usar `pm uninstall` sobre paquetes de sistema sin `--user 0`: con `pm uninstall` a secas se risked de dejar la imagen inconsistente; y NO usar `pm clear` sobre gms/vending/launcherx.
- NO tocar `com.android.systemui`, `com.android.providers.*`, APEX de framework (`com.android.devicelockcontroller`, `com.android.sdksandbox`, `com.android.location.fused`, `com.android.federatedcompute.services`): framework AOSP, deshabilitarlo produce inestabilidad.

---

## Artefactos crudos

`/tmp/tvdiag/pkg/`
- `INVENTARIO.tsv` — tabla completa de los 187 paquetes (tipo, total/apk/data/cache MB, enabled, hidden, versionName, ruta).
- `list3.txt`, `lists.txt`, `listd.txt`, `listall.txt` — salidas de `pm list packages -3/-s/-d` (0 deshabilitados).
- `launcher.txt`, `home_activities.txt`, `home_component.txt`, `launchers2.txt`, `vendor_launcher.txt` — resolución del HOME y del launcher de proveedor.
- `diskstats.txt` — `dumpsys diskstats` crudo (App Sizes / App Data Sizes / Cache Sizes por paquete).
- `sizes.tsv` — tamaños normalizados en MB por paquete.
- `dsys_pkgs.txt` — `dumpsys package packages` completo (flags, codePath, permisos, enabled).
- `versions.txt`, `flags.txt`, `paths_flags.txt`, `enabled_states.txt` — versionName / flags / rutas / estado por paquete clave.
- `apk_sizes.txt`, `sys_apk_sizes.txt`, `prebuilt_sizes.txt`, `splits.txt` — tamaños de APK (`stat -c %s`) por paquete.
- `browser_resolve.txt`, `browsers.txt` — resolución de intents BROWSABLE y búsqueda de navegadores.
- `gms_state.txt`, `accounts.txt` — estado de vending/gms/gsf y cuenta Google activa.
- `df.txt`, `dfall.txt` — espacio en disco.
- `rss.txt`, `procs.txt`, `running_apps.txt`, `top.txt`, `top2.txt`, `meminfo.txt` — consumo de RAM/CPU por proceso.
- `launcher_apps.txt` — 23 activities LAUNCHER (map de la pantalla de inicio).
- `table_final.tsv`, `table_final2.tsv`, `table_all.tsv` — versiones intermedias de la tabla.
- `perms.txt`, `du.txt`, `du_data.txt` — salidas fallidas por permisos (documentadas como limitación).
