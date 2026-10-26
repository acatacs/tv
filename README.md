# Kalley R10G — TV tune-up, ad-free browser and HDMI3 diagnosis

Everything needed to reproduce, rebuild and verify the work done on a **Kalley R10G
("MOKA") Google TV** — a Realtek RTD6748 set-top running Android TV 14.

The repository holds three things:

1. **A native TV browser** (`com.omp.tvweb`) written from scratch, because neither
   Firefox for Android nor Chrome is usable with a D-pad: the arrow keys only
   scroll the page, they never move focus, so nothing is clickable.
2. **A one-tap launcher** (`com.omp.tvbrowser`) that opens Firefox from the Google TV
   home screen, which otherwise has no entry for it.
3. **A written record** of the system tuning and the HDMI3 dropout diagnosis.

---

## 1. Hardware

| Item | Value |
|---|---|
| Model | Kalley R10G (`KALLEY_FFM`) |
| SoC | Realtek RTD6748, 4 cores @ 1.2 GHz (2× A55 + 2× A53) |
| GPU | PowerVR B-Series BXE-4-32, OpenGL ES 3.2 |
| RAM | 1.9 GB (`1,895,540K`) |
| Android | 14 / SDK 34, Google TV |
| Build | `KALLEY/KALLEY_FFM/R10G:14/UKR1.260612.001/26062418:user/release-keys` |
| ABI | **`armeabi-v7a` only** — `/system/lib64` does not exist |
| Display | 3840×2160 panel, 1920×1080 override, density 320 |
| ADB | `192.168.1.74:5555` over the network |

ADB over USB is **not possible**: the TV's USB-A ports are host-only, not gadget.

---

## 2. Connecting

```bash
adb connect 192.168.1.74:5555
adb devices -l
```

The first `connect` returns `failed to authenticate`; the TV then shows an
authorization prompt and the key in `~/.android/adbkey` is accepted. Reconnecting
afterwards yields `device`.

---

## 3. System changes (all reversible)

### 3.1 Animation scales

```bash
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

Measured effect: launcher jank **5.67% → 3.46%** (`dumpsys gfxinfo`).

Revert:

```bash
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings delete global animator_duration_scale
```

### 3.2 TalkBack disabled

TalkBack was **on**, which drew the green focus box around every element and turned
on touch exploration. That was the main cause of the "everything feels slow" and
"the UI looks wrong" complaints.

```bash
adb shell settings put secure enabled_accessibility_services null
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure touch_exploration_enabled 0
```

Revert:

```bash
adb shell settings put secure enabled_accessibility_services \
  com.google.android.marvin.talkback/.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

### 3.3 Blocking DNS

```bash
adb shell settings put global private_dns_mode hostname
adb shell settings put global private_dns_specifier dns.adguard-dns.com
```

Verified: `doubleclick.net` and `googleadservices.com` now resolve to `127.0.0.1`,
while `google.com`, `play.googleapis.com` and `lamovie.org` resolve normally.

Revert:

```bash
adb shell settings put global private_dns_mode off
```

### 3.4 Removed packages (user 0 only)

```
com.wbd.stream                            com.hulu.livingroomplus
com.disney.disneyplus                     com.apple.atve.androidtv.appletv
com.google.android.youtube.tvunplugged    com.peacocktv.peacockandroid
com.tubitv                                com.instagram.airwave
```

Revert:

```bash
adb shell cmd package install-existing --user 0 <package>
```

### 3.5 Disabled packages

```
com.google.android.syncadapters.calendar   com.apps.wifihotspot
com.realtek.karaoke.userguide              com.apps.mk.gameui
com.seraphic.media.tv.interactive.app
```

Revert:

```bash
adb shell pm enable --user 0 <package>
```

The Google TV launcher (`com.google.android.apps.tv.launcherx`) was **not touched**
and is still the default home.

---

## 4. Ad blocking

Two independent layers, because either alone leaves gaps:

| Layer | Scope | What it catches |
|---|---|---|
| Private DNS (AdGuard) | whole device | ad/tracker domains by DNS name |
| `shouldInterceptRequest` in `com.omp.tvweb` | in-app | ad domains by host, plus all popup windows |
| uBlock Origin + Privacy Badger | inside Firefox | cosmetic filtering, first-party trackers |

The in-app list lives in `Main.AD_HOSTS`. Adding a domain is a one-line change and a
rebuild.

---

## 5. The native TV browser (`com.omp.tvweb`)

### 5.1 Why it exists

Firefox and Chrome for Android are phone/tablet apps. On this TV they receive D-pad
key events and use them to **scroll**, never to move DOM focus. The result is that
no link can be activated: there is no visible focus, no pointer and no way to reach
a "Play" button.

`com.omp.tvweb` is a WebView host that adds exactly the missing layer: a real
pointer, plus magnetic snapping onto the page's actual interactive elements.

### 5.2 Interaction model

| Control | Action |
|---|---|
| D-pad (tap) | snap the pointer to the next real target in that direction |
| D-pad (hold) | fine, accelerating movement for pixel-precise placement |
| OK | click at the pointer position |
| BACK | back in history; in fullscreen, exit fullscreen |
| RED / INFO / MENU | open the URL bar |

The pointer is a `translationX/Y`-animated `ImageView` (composited, no relayout),
eased towards the target every 16 ms. The highlight is a separate `View` drawn
above the WebView, so it never fights the site's own CSS.

Snapping is resolved in JavaScript by `window.__ompNext`, which scores candidates
by distance along the pressed direction, then applies bonuses for large area and
for genuine interactivity (`a`, `button`, `input`, `video`, `[onclick]`,
`role=button`). Scoring is what stops the pointer landing on a stray word in a
paragraph instead of the "Watch now" button next to it.

### 5.3 Clicking

`__ompClickAt` first tries a semantic `element.click()` on the nearest clickable
ancestor, then falls back to dispatching real `mousedown` / `mouseup` / `click`
events at the coordinates, and finally a synthetic `MotionEvent` through
`WebView.dispatchTouchEvent`. Video players usually need one of the last two.

### 5.4 Fullscreen video

`WebChromeClient.onShowCustomView` moves the player's view into a full-screen
`FrameLayout` overlay and hides the system UI. For players that never request
fullscreen, `__ompBigVideo` calls `requestFullscreen()` on the largest `<video>`
(or its parent) as a fallback.

### 5.5 Source layout

```
src/
  AndroidManifest.xml                  leanback + VIEW intents, SDK 21..34
  ks.jks                               throwaway signing key (pass omp1234, alias k)
  assets/tvnav.js                      injected navigation script
  res/drawable-xhdpi/{icon,banner}.png generated by Pillow
  src/com/omp/tvweb/
    Main.java                          activity, key handling, WebView setup
    NativeBridge.java                  @JavascriptInterface: hover(), ready()
    ArrowCursor.java                   pointer Drawable (Path + halo)
    HighlightView.java                 focus outline Drawable
    UrlBar.java                        TV-style URL entry
```

912 lines of Java plus a 169-line JavaScript asset; no third-party dependencies,
no AndroidX, no Gradle. The navigation script lives in `assets/` rather than a
Java string literal so it can be read, diffed and linted on its own. `build.sh`
adds `assets/` to the APK explicitly, because `aapt2 link` does not copy a plain
asset tree the way Gradle does.

### 5.6 Building

Requirements: JDK 17+ and Android SDK build-tools **35.0.0**.

```bash
cd ~/Documents/tv
./scripts/build.sh            # produces apk/tvweb.apk
./scripts/build.sh install    # builds, then installs over ADB
```

The script runs:

```bash
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/base.apk -I $JAR --manifest AndroidManifest.xml \
    -R build/res.zip --min-sdk-version 21 --target-sdk-version 34
javac -source 8 -target 8 -classpath $JAR -d build/classes src/com/omp/tvweb/*.java
d8 --lib $JAR --min-api 21 --output build/dex build/classes/com/omp/tvweb/*.class
zip -q build/unsigned.apk classes.dex
zip -q -r build/unsigned.apk assets
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks ks.jks --ks-pass pass:omp1234 --key-pass pass:omp1234 \
    --out apk/tvweb.apk build/aligned.apk
```

Override the SDK or the TV address with `ANDROID_SDK=` and `TV_ADDR=`.

#### Known toolchain trap

`d8` in build-tools **34.0.0** crashes with an internal `NullPointerException` on any
class carrying an `EnclosingMethod` attribute that implements an interface from
`android.jar` — anonymous `Runnable`s and `ValueCallback<String>` are enough to
trigger it. Use **build-tools 35.0.0**, and prefer named static inner classes over
anonymous ones. This is why `Main` has classes like `HoverApply`, `Mover` and
`ProbeDone` instead of lambdas.

### 5.7 Reviewing changes on the TV

```bash
TV="adb -s 192.168.1.74:5555"

# launch
$TV shell am force-stop com.omp.tvweb
$TV shell am start -n com.omp.tvweb/.Main
sleep 18

# drive it
$TV shell input keyevent KEYCODE_DPAD_DOWN
$TV shell input keyevent KEYCODE_DPAD_CENTER

# see the result
$TV exec-out screencap -p > /tmp/shot.png

# watch the bridge while you move
$TV shell logcat -c
$TV shell logcat -d -s TVWEB
```

`TVWEB` logs every `hover` and every probe result, which is the fastest way to tell
"the pointer never reached a target" from "the highlight is not drawing". A healthy
trace looks like `bridge ready=true`, then `hover 473,517 81x42 vis=1` as the
pointer moves; `hover 0,0 0x0 vis=0` means nothing interactive is under the cursor.

Screenshots are taken at 1920×1080 but the app window reports 3840×2160 logical
pixels, so divide coordinates by 2 when translating a tap point from a screenshot
into a `dispatchTouchEvent` position.

---

## 6. The launcher shim (`com.omp.tvbrowser`)

Firefox declares only `android.intent.category.LAUNCHER`, so it never appears in the
Google TV home screen, which queries `LEANBACK_LAUNCHER`. The shim is a 12 KB
`Theme.NoDisplay` activity that declares the leanback category and starts
`org.mozilla.fennec_fdroid/.App`. Source is `apk/shim.apk`; rebuild it by pointing
`aapt2` at the same manifest pattern with
`android:name=".Launch"` and a `LEANBACK_LAUNCHER` intent filter.

---

## 7. HDMI3 dropouts — diagnosis (closed)

**Symptom:** the picture on `hdmi_port=3` goes black for roughly 1–3 s, then returns.

**Finding:** the TV is not at fault. The source (the set-top decoder) emits an
invalid timing, the TV detects it and re-locks:

```
onms3 watchdog detect error!!watchdog err=200000
vfe_hdmi_drv_handle_on_line_measure_error port:3 ... is_reset_phy_flow=1
HDMI[p3] Switch to HDCP_OFF -> HDCP22 -> NO_HDCP
DETECT][EVENT][5] VIDEO FSM event: ONMS_FULLY_RESET, Action: RESET_TO_WAIT_SYNC
TMDS clock change!! tmds_phy_md_clk from 702 -> 0 -> 703
VIDEO FSM: WAIT_SYNC -> SETUP_VEDIO_PLL -> MEASURE -> DISPLAY_ON -> VIDEO_READY
```

Eight episodes captured and timed, median black 2.47 s. During all of them:

- `5V(0,0,0,1)` never dropped and `is_5V_HPD=1` in 100% of samples → **the cable
  never disconnected**.
- Zero HDMI hotplug events, zero display mode changes, zero Android process
  restarts.
- The source's pixel clock alternates 75816 ↔ 75924 (~0.14%), `v_total` alternates
  562 ↔ 563, and `filed_confuse` fires in 8/8 episodes.
- Active mode is 1080**i**59.94 YUV444 at ~74 MHz, which leaves the receiver no
  margin.

**Action:** set the decoder to **1080p60** (or force RGB / 4:2:2). That both proves
the source is the culprit and leaves the TV in a mode with headroom.

Full report: `evidence/INFORME_FINAL_HDMI3.md`.

---

## 8. Extensions

| Extension | State | Evidence |
|---|---|---|
| Privacy Badger 2026.9.15 | **installed and active** | Firefox menu shows "Privacy Badger 1" with a block counter |
| uBlock Origin 1.75.0 | **pending** | AMO serves it for Android ≥115; Firefox 158 qualifies |

To finish installing uBlock Origin on the TV: menu ⋮ → Extensions → Manage
extensions → the `+` next to uBlock Origin → Add.

---

## 9. Known limitations

- ADB is unprivileged: no `dmesg`, no `/data/data`, no `/sys/class/thermal`.
- The TV's `loadavg` sits at ~19–22 because 17 vendor kernel threads park in
  `D` state for the video pipeline — CPU is actually 72% idle. Do not read
  `loadavg` as saturation on this device.
- Firefox and Chrome are not TV browsers; they remain installed for the extension
  support the WebView cannot provide.
- The WebView host inherits the platform WebView (`com.google.android.webview`
  155.x), so the browser is only as modern as that component.

---

## 10. Repository layout

```
README.md                 this file
.gitignore
scripts/build.sh          build + optional install
src/                      browser sources, manifest, resources, keystore
apk/tvweb.apk             signed browser APK
apk/shim.apk              signed launcher shim
evidence/                 diagnosis reports and measurement baselines
```

Large upstream APKs (`fennec-158.0.2-armeabi-v7a.apk`, `BraveMonoarm.apk`) are
gitignored. Re-download them with:

```bash
curl -LO https://f-droid.org/repo/org.mozilla.fennec_fdroid_1580200.apk
curl -LO https://github.com/brave/brave-browser/releases/download/v1.100.23/BraveMonoarm.apk
```

Only `armeabi-v7a` builds of either will install; arm64 and x86 variants fail with
`INSTALL_FAILED_NO_MATCHING_ABIS`.
