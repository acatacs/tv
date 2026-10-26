# Kalley R10G — TV tune-up, ad-free browser and HDMI3 diagnosis

Everything needed to reproduce, rebuild and verify the work done on a **Kalley R10G
("MOKA") Google TV** — a Realtek RTD6748 set-top running Android TV 14.

The repository holds two things:

1. **A native TV browser** (`com.omp.tvweb`) written from scratch, because mobile
   browsers are unusable with a D-pad: the arrow keys only scroll the page, they
   never move focus, so nothing is clickable.
2. **A written record** of the system tuning and the HDMI3 dropout diagnosis.

Ad blocking is handled entirely outside the browser by system-wide Private DNS
(AdGuard), so it applies to every app, not just web pages.

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

Ad blocking is **system-wide**, via Private DNS, so it covers every app on the TV
rather than only web pages:

| Layer | Scope | What it catches |
|---|---|---|
| Private DNS (AdGuard) | whole device | ad and tracker domains, by DNS name |
| `shouldInterceptRequest` in `com.omp.tvweb` | in-app | ad hosts plus every popup window |

Verified on the device — these resolve to `127.0.0.1`, i.e. they are blackholed:

```
doubleclick.net        -> 127.0.0.1
googleadservices.com   -> 127.0.0.1
adservice.google.com   -> 127.0.0.1
```

while ordinary sites are untouched:

```
lamovie.org            -> 188.114.97.5
www.google.com         -> 142.251.150.119
play.googleapis.com    -> 172.217.116.4
```

The in-app list lives in `Main.AD_HOSTS`. Adding a domain is a one-line change and a
rebuild.

---

## 5. The native TV browser (`com.omp.tvweb`)

### 5.1 Why it exists

Mobile browsers are phone/tablet apps. On this TV they receive D-pad key events and
use them to **scroll**, never to move DOM focus. The result is that no link can be
activated: there is no visible focus and no way to reach a "Play" button.

`com.omp.tvweb` is a WebView host that adds the missing layer: a focus ring that
steps from one real control to the next, the way Android TV and tvOS move focus.
There is **no pointer** — a remote never aims at pixels.

### 5.2 Interaction model

| Control | Action |
|---|---|
| D-pad | move the focus ring to the next control in that direction |
| OK (centre / Enter) | activate the focused control with a real touch |
| BACK | history back; in fullscreen, leave fullscreen; at the root, exit the app |
| RED | show the URL bar |

D-pad auto-repeat is what produces continuous movement: holding the key walks the
ring control by control, so no easing or acceleration code is needed.

### 5.3 How focus is resolved (`assets/tvnav.js`)

The ring is a single overlay `div` injected once per page. `window.__ompNav`
exposes `move(dx, dy)`, `focusFirst()`, `tap()` and `fullscreen()`.

- **Targets** come from `a[href], button, input, select, textarea, video, iframe,
  [role=button], [role=link], [onclick], [tabindex]`.
- **`visibleBox()`** rejects anything under 24 px, `display:none`, `visibility:hidden`,
  opacity below 0.05, and clips the box against every `overflow:hidden`/`clip`
  ancestor, so a control buried inside a collapsed panel never receives focus.
- A control that **contains** another control is dropped, so the innermost control
  takes focus instead of the card wrapping it.
- **`nearest()`** scores candidates by distance along the pressed direction plus
  twice the off-axis distance, which is what makes a row win over a diagonal
  neighbour.
- Off-screen controls stay in the list: pressing DOWN past the fold scrolls to the
  next row rather than stopping.
- **`reveal()`** scrolls the least amount needed, keeping 140 px of margin, and uses
  `scrollIntoView` inside scroll containers so their own scrolling is respected.
- After scrolling settles (140 ms) `settle()` re-checks the focus; if it ended up off
  screen, focus moves to the nearest control still visible.
- The ring **follows live**: scroll and resize listeners reposition it with
  `requestAnimationFrame`, so it stays glued to its control while the page moves.
- `place()` copies the control's `border-radius` so the outline hugs rounded cards.

### 5.4 Activation

`tap()` returns the centre of the focused control in CSS pixels plus a flag for
media. Java then sends a real `MotionEvent` — a **trusted** gesture — so links
follow, buttons act, and fullscreen requests are permitted. A synthetic JS click
would not carry that trust.

### 5.5 Fullscreen video

`WebChromeClient.onShowCustomView` moves the player into a full-screen overlay.
If the player never asks for fullscreen, `__ompNav.fullscreen()` calls
`requestFullscreen()` on the focused `<video>`/`<iframe>` 450 ms after the tap,
while the gesture is still valid. `onCustomViewHidden()` is called when BACK leaves
fullscreen, otherwise the WebView refuses the next request for the rest of the
session.

### 5.6 Source layout

```
src/
  AndroidManifest.xml                  leanback + VIEW intents, SDK 21..34
  ks.jks                               throwaway signing key (pass omp1234, alias k)
  assets/tvnav.js                      focus-ring navigation, injected per page
  res/drawable-xhdpi/{icon,banner}.png generated with Pillow
  src/com/omp/tvweb/
    Main.java                          activity, key handling, WebView, fullscreen
    UrlBar.java                        TV-style URL entry
```

About 610 lines of Java, a 313-line JavaScript asset, a 49-line manifest and a
65-line build script: roughly 1,030 lines of source in total, no third-party
dependencies, no AndroidX, no Gradle. The navigation script lives in `assets/`
rather than a Java string literal so it can be read, diffed and linted on its own;
`build.sh` adds `assets/` to the APK explicitly because `aapt2 link` does not copy a
plain asset tree the way Gradle does.

### 5.7 Building

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
anonymous ones.

### 5.8 Reviewing changes on the TV

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

# watch the JavaScript bridge while you move
$TV shell logcat -c
$TV shell logcat -d -s TVWEB
```

Screenshots are 1920×1080 while the app window reports 3840×2160 logical pixels, so
divide coordinates by 2 when translating a tap point from a screenshot into a
`MotionEvent` position.

To check that a rebuild produced what is actually installed:

```bash
APK=$($TV shell pm path com.omp.tvweb | sed 's/package://' | tr -d '\r')
$TV pull "$APK" /tmp/installed.apk
unzip -p /tmp/installed.apk assets/tvnav.js | md5sum
md5sum src/assets/tvnav.js
```

---

## 6. HDMI3 dropouts — diagnosis (closed)

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

## 7. Ad blocking

Two layers, both independent of any browser extension:

| Layer | Scope | What it catches |
|---|---|---|
| Private DNS (AdGuard) | whole device | ad and tracker domains, by DNS name |
| `shouldInterceptRequest` in `com.omp.tvweb` | in-app | ad hosts plus every popup window |

DNS blocking covers every app, not only web pages: YouTube, Play, Netflix and the
launcher all resolve through the same resolver.

Verified on the device — these resolve to `127.0.0.1`, i.e. they are blackholed:

```
doubleclick.net        -> 127.0.0.1
googleadservices.com   -> 127.0.0.1
adservice.google.com   -> 127.0.0.1
```

while ordinary sites are untouched:

```
lamovie.org            -> 188.114.97.5
www.google.com         -> 142.251.150.119
play.googleapis.com    -> 172.217.116.4
```

The in-app list lives in `Main.AD_HOSTS`. Adding a domain is a one-line change and a
rebuild.

## 8. Known limitations

- ADB is unprivileged: no `dmesg`, no `/data/data`, no `/sys/class/thermal`.
- The TV's `loadavg` sits at ~19–22 because ~17 vendor kernel threads park in
  `D` state for the video pipeline. Measured over a quiet 10 s window the CPU is
  **98% idle** (`/proc/stat`: user 0.33%, system 0.96%, softirq 0.58%), so do not
  read `loadavg` as saturation on this device.
- After the cleanup the device reports exactly one user-installed package,
  `com.omp.tvweb`. No third-party browser and no Firefox remain.
- `com.omp.tvweb` is the only browser on the device; no third-party browser is
  installed.
- The WebView host inherits the platform WebView (`com.google.android.webview`
  155.x), so the browser is only as modern as that component.

---

## 9. Repository layout

```
README.md                 this file
.gitignore
scripts/build.sh          build + optional install
src/                      browser sources, manifest, resources, keystore
apk/tvweb.apk             signed browser APK
evidence/                 diagnosis reports and measurement baselines
```

`com.omp.tvweb` targets `armeabi-v7a`, the only ABI this panel has; an arm64 or x86
build fails with `INSTALL_FAILED_NO_MATCHING_ABIS`.
