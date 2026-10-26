# TV Kalley R10G "MOKA" — Verificación y cuantificación post-cambios

Fecha: 2026-10-26 · Host: 192.168.1.74:5555 · Sesión ADB **no root** (uid 2000)
Baseline: `/tmp/tvdiag/baseline/baseline.json`

---

## 1) TABLA COMPARATIVA ANTES / DESPUÉS

| Métrica | ANTES | DESPUÉS | Delta | Veredicto |
|---|---|---|---|---|
| loadavg (1m) | 17.58 | 22.19 | +4.61 | **EMPEORÓ** |
| loadavg (5m) | 18.23 | 22.90 | +4.67 | **EMPEORÓ** |
| loadavg (15m) | 18.88 | 21.54 | +2.66 | **EMPEORÓ** |
| `free -m` total (MB) | 1851 | 1851 | 0 | igual (hardware) |
| `free -m` used (MB) | 1590 | 1583 | −7 | mejor (marginal) |
| `free -m` free (MB) | 260 | 267 | +7 | mejor (marginal) |
| Swap usado (MB) | 439 | 499 | +60 | **EMPEORÓ** |
| Total RAM (dumpsys) | 1,895,540K | 1,895,540K | 0 | igual |
| Free RAM (dumpsys) | 563,998K | 651,014K | +87,016K | **MEJORÓ** |
| Used RAM (dumpsys) | 1,418,499K | 1,592,072K | +173,573K | **EMPEORÓ** |
| Lost RAM | 254,175K | 78,588K | −175,587K | **MEJORÓ** |
| ZRAM physical usado | 49,532K | 69,848K | +20,316K | **EMPEORÓ** |
| ZRAM swap in use | 442,112K | 497,572K | +55,460K | **EMPEORÓ** |
| CPU TOTAL (dumpsys cpuinfo) | 12% (1% user, 8.5% kernel) | 60% (40% user, 20% kernel) | +48 pp | **EMPEORÓ** (durante ventana demeasurement con apps launching) |
| Procesos | 326 | 346 | +20 | **EMPEORÓ** |
| Hilos | 2143 | 2140 | −3 | igual |
| Paquetes sistema | 177 | 177 | 0 | igual |
| Paquetes third-party (`-3`) | 8 | 3 | −5 | MEJORÓ (8 desinstalados − 3 nuevas) |
| Paquetes disabled | 0 | 5 | +5 | aplicado |
| `window_animation_scale` | 1.0 | 0.5 | −0.5 | aplicado |
| `transition_animation_scale` | 1.0 | 0.5 | −0.5 | aplicado |
| `animator_duration_scale` | null | 0.5 | — | aplicado |
| Janky frames launcher | 313 (5.67%) de 5518 | 603 (3.46%) de 17442 | −2.21 pp | **MEJORÓ** (legacy: 45.61%) |
| uptime (s) | 4750.77 | 7950.69 | +3200 | esperado (sin reinicio aún) |
| TalkBack bound service | activo | ninguno | — | aplicado |
| `touchExplorationEnabled` | true | **false** | — | aplicado |
| `enabled_accessibility_services` | talkback | `null` | — | aplicado |
| `private_dns_mode` | (no documentado) | hostname | — | aplicado |
| `private_dns_specifier` | (no documentado) | dns.adguard-dns.com | — | aplicado |

**Lectura honesta:** los cambios de configuración están aplicados y verificados, y el jank del launcher mejoró
(5.67% → 3.46%). Pero **el loadavg subió ~26% y el CPU instantáneo está mucho más alto**. La causa más
probable no son los cambios: es la **carga de measurement** (arranque de Netflix, YouTube TV, Prime Video, Play
Store, Firefox, `com.omp.tvweb`) y el propio `com.omp.tvweb` (146 MB RSS) corriendo en primer plano durante
las pruebas, más el `system_server` con 257 MB RSS. El baseline se tomó con el dispositivo en reposo; la
comparación de CPU no es apples-to-apples. **Falta una medición de loadavg en reposo comparable al baseline
para poder afirmar mejora real de carga**; el orquestador debe repetir `cat /proc/loadavg` tras 10 min sin
interacción tras el reinicio previsto.

### Top 10 procesos por RSS (DESPUÉS)
KB: system_server 257.716 · ocessService0:0 172.128 · com.omp.tvweb 146.540 · ps.tv.launcherx 144.344 ·
android.vending 125.512 · gle.android.gms 115.084 · ding:background 98.540 · com.realtek.tv 91.980 ·
niss:interactor 76.608 · ndroid.systemui 75.528
(baseline.json **no contiene** desglose por proceso; comparación por proceso NO es posible).

`com.apps.mk.gameui` → **no aparece** en `ps -A` (deshabilitado correctamente, confirmado).

---

## 2) VERIFICACIÓN DE INTEGRIDAD (evidencia literal)

### 2.1 Launcher — OK
```
$ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
com.google.android.apps.tv.launcherx/.home.HomeActivity

$ input keyevent KEYCODE_HOME; dumpsys activity activities | grep topResumedActivity
topResumedActivity=ActivityRecord{8b0fbb8 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t455}
  mFocusedApp=ActivityRecord{8b0fbb8 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t455}
```
`isDefault=true`, `priority=2` → el launcher sigue siendo el HOME por defecto y está en primer plano tras HOME.

### 2.2 Crashes — ninguno detectado
`logcat -d` (últimas 3000 líneas, `logcat_tail.txt`; y 1500 líneas post-lanzamientos, `logcat_post_launch.txt`):
```
grep "FATAL EXCEPTION|AndroidRuntime: |ANR in |has died|Force finishing|ClassNotFoundException|Unable to start activity"
→ No matches found
```
Único patrón residual (esperado, no es crash):
```
W ActivityManager: Unable to start service Intent { cmp=com.google.android.youtube.tvunplugged/...SystemJobService } U=0: not found
E JobScheduler.Concurrency: Error executing JobStatus{... com.google.android.youtube.tvunplugged ...}
E ActivityThread: Failed to find provider info for com.google.android.apps.youtube.tvunplugged.client.provider
```
→ Residuo de WorkManager/JobScheduler del APK desinstalado `com.google.android.youtube.tvunplugged` (las 3 app
`com.google.android.apps.youtube.tvunplugged*` son el mismo APK, 3 Activity aliases). Es ruido de un job
programado pendiente, **no un crash**. Se limpia solo al expirar el job (`TIME=-50m…`) o con:
`adb shell cmd jobscheduler run -f com.google.android.youtube.tvunplugged 0` (sin efecto; job huérfano) — para
purga real: `adb shell cmd jobscheduler cancel <jobId>` no es accesible sin root. **Riesgo: bajo.**

### 2.3 Play Store + GMS — OK
```
$ ps -A -o PID,NAME | grep -Ei "vending|gms"
1278 com.google.android.gms
7680 com.android.vending

$ am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.android.vending
Starting: Intent { act=android.intent.action.MAIN cat=[LAUNCHER] pkg=com.android.vending }
topResumedActivity=ActivityRecord{6aca923 u0 com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity t512}
```
(`am start -n com.android.vending/...finsky.activities.MainActivity` falla con SecurityException "not exported"
→ comportamiento normal, no un crash. Usar el intent con categoría LAUNCHER.)

### 2.4 DNS / conectividad Play — OK
```
$ ping -c1 -W3 play.googleapis.com
64 bytes from 172.217.116.4: icmp_seq=1 ttl=117 time=19.1 ms   RC1=0
$ ping -c1 -W3 android.clients.google.com
64 bytes from 142.250.115.139 (rq-in-f139.1e100.net): icmp_seq=1 ttl=105 time=107 ms   RC2=0
```
El DNS privado (AdGuard) **no** rompe Play: los hosts de Google resuelven a IPs públicas reales, no a 127.0.0.1.

### 2.5 Apps de streaming que quedan — las 3 abren sin crash

| App | Comando | topResumedActivity tras 5–6 s | Crash |
|---|---|---|---|
| Netflix | `am start -n com.netflix.ninja/.MainActivity` | `com.netflix.ninja/.MainActivity t505` | no |
| YouTube TV | `am start -n com.google.android.youtube.tv/...ShellActivity` | `com.google.android.youtube.tv/...MainActivity t508` | no |
| Prime Video | `am start -n com.amazon.amazonvideo.livingroom/com.amazon.ignition.IgnitionActivity` | `com.amazon.amazonvideo.livingroom/com.amazon.ignition.IgnitionActivity t510` | no |
| Play Store | `am start -a MAIN -c LAUNCHER com.android.vending` | `com.android.vending/...TvMainActivity t512` | no |

Todas cerradas con `am force-stop` tras la prueba. Nota: `am start -n com.amazon.amazonvideo.livingroom`
(sin activity) lanza `IllegalArgumentException: Bad component name` — es sintaxis inválida del comando, no un
fallo de la app; Prime Video expone `com.amazon.ignition.IgnitionActivity` (filtro LEANBACK_LAUNCHER).

### 2.6 Paquetes — estado exacto
```
$ pm list packages --user 0 -s   | wc -l   → 177   (= baseline pkgs_s 177)
$ pm list packages --user 0 -3   | wc -l   → 3
$ pm list packages --user 0 -d              → com.apps.mk.gameui
                                               com.google.android.syncadapters.calendar
                                               com.realtek.karaoke.userguide
                                               com.seraphic.media.tv.interactive.app
                                               com.apps.wifihotspot
$ pm list packages --user 0 -3
package:org.mozilla.fennec_fdroid
package:com.omp.tvbrowser
package:com.omp.tvweb
```
Los 8 desinstalados **no** aparecen para user 0:
```
com.wbd.stream:0  com.disney.disneyplus:0  com.google.android.youtube.tvunplugged:0
com.hulu.livingroomplus:0  com.apple.atve.androidtv.appletv:0  com.peacocktv.peacockandroid:0
com.tubitv:0  com.instagram.airwave:0
```
Disabled verificados (`dumpsys package` → `enabled=3` = DISABLED_USER en los 5):
```
com.apps.mk.gameui: enabled=3      com.google.android.syncadapters.calendar: enabled=3
com.realtek.karaoke.userguide: enabled=3   com.seraphic.media.tv.interactive.app: enabled=3
com.apps.wifihotspot: enabled=3
```

### 2.7 TalkBack / accesibilidad — OK
```
$ dumpsys accessibility | head -60
User state[ attributes:{id=0, touchExplorationEnabled=false, ... alwaysOnMagnificationEnabled=true}]
  shortcut key:{com.google.android.marvin.talkback/...TalkBackService}
  Bound services:{}          ← ningún servicio bound
  Enabled services:{}        ← ninguno habilitado
  Binding services:{}
  Crashed services:{}

$ settings get global enabled_accessibility_services → null
$ settings get global accessibility_enabled         → null
$ settings get global touch_exploration_enabled     → null
```
El paquete `com.google.android.marvin.talkback` **sigue instalado** (solo se desactivó el servicio) → reversión
trivial. `alwaysOnMagnificationEnabled=true` es un ajuste del fabricante, preexistente y no relacionado.

---

## 3) COMANDOS DE REVERSIÓN EXACTOS

```bash
ADB="adb -s 192.168.1.74:5555"

# (A) Escalas de animación → valores originales
$ADB shell settings put global window_animation_scale 1.0
$ADB shell settings put global transition_animation_scale 1.0
$ADB shell settings put global animator_duration_scale null

# (B) TalkBack → restaurar servicio (equivalente a `pm enable`; requiere que el APK siga presente)
$ADB shell settings put global enabled_accessibility_services \
    com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
$ADB shell settings put global accessibility_enabled 1
$ADB shell settings put global touch_exploration_enabled 1

# (C) DNS privado → OFF
$ADB shell settings put global private_dns_mode off
$ADB shell settings put global private_dns_specifier ""

# (D) Re-habilitar los 5 paquetes deshabilitados
$ADB shell pm enable --user 0 com.apps.mk.gameui
$ADB shell pm enable --user 0 com.google.android.syncadapters.calendar
$ADB shell pm enable --user 0 com.realtek.karaoke.userguide
$ADB shell pm enable --user 0 com.seraphic.media.tv.interactive.app
$ADB shell pm enable --user 0 com.apps.wifihotspot

# (E) Re-instalar los 8 desinstalados para user 0 (los APK siguen en /data/app, comprobado vía `pm list packages -u`)
$ADB shell cmd package install-existing --user 0 com.wbd.stream
$ADB shell cmd package install-existing --user 0 com.disney.disneyplus
$ADB shell cmd package install-existing --user 0 com.google.android.youtube.tvunplugged
$ADB shell cmd package install-existing --user 0 com.hulu.livingroomplus
$ADB shell cmd package install-existing --user 0 com.apple.atve.androidtv.appletv
$ADB shell cmd package install-existing --user 0 com.peacocktv.peacockandroid
$ADB shell cmd package install-existing --user 0 com.tubitv
$ADB shell cmd package install-existing --user 0 com.instagram.airwave

# (F) Desinstalar las 3 apps nuevas (opcional, parte del desarrollo propio)
$ADB shell pm uninstall --user 0 org.mozilla.fennec_fdroid
$ADB shell pm uninstall --user 0 com.omp.tvbrowser
$ADB shell pm uninstall --user 0 com.omp.tvweb
```
Nota (E): los 8 aparecen en `pm list packages -u` (global) → los APKs **no** se borraron del sistema, solo se
desinstalaron para user 0. `install-existing --user 0` es la vía correcta.

---

## 4) LIMITACIONES

1. **Sesión ADB sin root (uid 2000).** `ls /data/app` → Permission denied; no se puede verificar en disco qué
   APKs quedaron. La reversión (E) se apoya en `pm list packages -u`, que sí los lista.
2. **No se puede leer `/data/system/users/0/package-restrictions.xml`** → el estado de "restricted"/"hidden" no es
   inspeccionable; se infiere del comportamiento observable.
3. **Comparación de CPU/loadavg no es apples-to-apples.** El baseline se tomó en reposo; la medición DESPUÉS
   coincidió con el arranque secuencial de 4 apps heavy + `com.omp.tvweb` en primer plano. No se puede atribuir
   el +26% de loadavg a los cambios. **Se requiere re-medición en reposo** (10 min sin interacción, tras el
   reinicio que hará el orquestador) para emitir veredicto de carga.
4. **baseline.json no contiene desglose de RSS por proceso** → no hay "top 10 antes" con el que comparar.
5. `dumpsys package` se corta con `Failed to write while dumping service package: Broken pipe` (buffer del shell);
   el `enabled=3` se leyó OK para los 5 paquetes.
6. No se ejecutó `settings put`/`setprop`/`pm enable|disable` (prohibido en esta tarea): toda la verificación fue
   de solo lectura más `am force-stop` e `input keyevent`.
7. El jank del launcher ahora tiene dos métricas Android 14: la moderna (3.46%) y la legacy (45.61%). El
   baseline solo registró la moderna → comparación parcial, no totalmente homogénea.
8. `com.google.android.apps.youtube.tvunplugged.client.provider` y el job WorkManager huérfano de
   `com.google.android.youtube.tvunplugged` seguirán logged hasta que expiren; no se pueden purgar sin root.

---

## 5) ARTEFACTOS

```
/tmp/tvdiag/verify/REPORT.md               este informe
/tmp/tvdiag/verify/state_raw.txt           loadavg, free -m, nº procs, escalas de animación
/tmp/tvdiag/verify/mem_cpu.txt             dumpsys meminfo + cpuinfo TOTAL + nº hilos
/tmp/tvdiag/verify/final_state.txt         estado final en reposo tras HOME
/tmp/tvdiag/verify/final_mem_cpu.txt       meminfo/cpuinfo finales (2ª lectura, más estable)
/tmp/tvdiag/verify/pkgs.txt                pm list packages (-s, -3, -d, -u)
/tmp/tvdiag/verify/pkgs_user0.txt          conteos por user 0 + check de los 8 desinstalados
/tmp/tvdiag/verify/launcher.txt            resolve-activity HOME + topResumedActivity
/tmp/tvdiag/verify/accessibility.txt       dumpsys accessibility completo
/tmp/tvdiag/verify/settings_state.txt      private_dns_* + settings de accesibilidad
/tmp/tvdiag/verify/logcat_tail.txt         3000 líneas de logcat (baseline de crashes)
/tmp/tvdiag/verify/logcat_post_launch.txt  1500 líneas post-lanzamiento de apps
/tmp/tvdiag/verify/dns.txt                 ping play.googleapis.com / android.clients.google.com
/tmp/tvdiag/verify/stream_pkgs.txt         inventario de apps de streaming
/tmp/tvdiag/verify/stream_launch.txt       lanzamiento Netflix + YouTube TV
/tmp/tvdiag/verify/stream_launch2.txt      continuación (Prime Video con activity explícita)
/tmp/tvdiag/verify/yt_test.txt             re-test YouTube TV (confirmado MainActivity)
/tmp/tvdiag/verify/prime_resolve.txt       resolución de activity de Prime Video
/tmp/tvdiag/verify/prime_test.txt          lanzamiento de Prime Video
/tmp/tvdiag/verify/play_test.txt           intento con activity no exportada (SecurityException esperado)
/tmp/tvdiag/verify/play_test2.txt          Play Store vía LAUNCHER (OK)
/tmp/tvdiag/verify/proc_check.txt          ps de gameui (ausente) + vending/gms (presentes)
/tmp/tvdiag/verify/toprss.txt              top 15 procesos por RSS
/tmp/tvdiag/verify/gfx_now.txt             gfxinfo del launcher + uptime + loadavg
/tmp/tvdiag/verify/revert_feasibility.txt  estado enabled=3 de los 5 y presencia global de los 8
/tmp/tvdiag/verify/dataapp.txt             intento de listar /data/app (Permission denied)
```
