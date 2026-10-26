package com.omp.tvweb;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class Main extends Activity implements UrlBar.Submit {

    static final String TAG = "TVWEB";
    static final String HOME = "https://lamovie.org";

    // Pill texts. Escapes keep the source ASCII whatever the build locale is.
    static final String FAILED = "No se pudo cargar · OK para reintentar";
    static final String HINT = "Botón rojo: escribir una dirección";

    static final long FULLSCREEN_FALLBACK_MS = 450;
    static final long HINT_MS = 4000;

    static final String[] AD_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "amazon-adsystem.com",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
        "popads.net", "popcash.net", "propellerads.com", "propellerpops.com",
        "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net",
        "trafficfactory.biz", "adsterra.com", "onclckds.com", "onclkds.com",
        "clickadu.com", "hilltopads.net", "hilltopads.com", "mgid.com",
        "zergnet.com", "revcontent.com", "scorecardresearch.com",
        "quantserve.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "1rx.io", "2mdn.net", "moatads.com", "casalemedia.com",
        "smartadserver.com", "bidswitch.net", "contextweb.com", "gumgum.com",
        "districtm.io", "33across.com", "sharethrough.com", "teads.tv",
        "spotxchange.com", "adfox.ru", "zedo.com", "adform.net",
        "advertising.com", "adsafeprotected.com", "serving-sys.com",
        "sizmek.com", "flashtalking.com", "innovid.com", "tremorhub.com",
        "telaria.com", "springserve.com", "beachfront.com", "freewheel.tv",
        "stickyadstv.com", "undertone.com", "sovrn.com", "lijit.com",
        "indexww.com", "emxdgt.com", "triplelift.com", "onetag-sys.com",
        "adpushup.com", "ezoic.net", "ezojs.com", "adroll.com",
        "clarity.ms", "hotjar.com", "histats.com", "statcounter.com",
        "liveinternet.ru", "onclickads.net", "adcash.com", "bidvertiser.com",
        "adf.ly", "shorte.st", "linkvertise.com", "adfoc.us", "ouo.io",
        "cloudflareinsights.com", "popmonetizer.com", "clksite.com",
        "tsyndicate.com", "trafficstars.com", "adsco.re", "effectivegatecpm.com"
    };

    FrameLayout root;
    WebView web;
    UrlBar urlBar;
    FrameLayout videoHolder;
    TextView status;
    View progress;

    final Handler h = new Handler();
    final Set<String> adSet = new HashSet<String>();
    float dens;
    boolean fullscreen;
    boolean loadFailed;
    boolean hintShown;
    boolean hintVisible;
    boolean playerMode;   // a player is focused: the remote drives it, not the page
    WebChromeClient.CustomViewCallback customCb;
    String navSource;

    // ======================= web client =======================
    static class PageClient extends WebViewClient {
        final Main a;
        PageClient(Main m) { a = m; }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
            String host = req.getUrl().getHost();
            if (host != null) {
                String lo = host.toLowerCase();
                for (String bad : a.adSet) {
                    if (lo.equals(bad) || lo.endsWith("." + bad)) {
                        return new WebResourceResponse("text/plain", "utf-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                }
            }
            return null;
        }

        // Page-initiated navigation of the main frame (ad redirects, popunders)
        // to another site is dropped; anything the person asked for passes.
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
            String url = req.getUrl().toString();
            if (req.isForMainFrame() && !req.hasGesture() && !sameSite(v.getUrl(), url)) {
                Log.d(TAG, "blocked redirect " + url);
                return true;
            }
            return shouldOverrideUrlLoading(v, url);
        }

        static boolean sameSite(String current, String next) {
            if (current == null) return true;
            return registrable(Uri.parse(current).getHost()).equals(registrable(Uri.parse(next).getHost()));
        }

        // last two labels: "www.flixlatam.com" and "flixlatam.com" are one site
        static String registrable(String host) {
            if (host == null) return "";
            String[] p = host.split("\\.");
            if (p.length <= 2) return host;
            return p[p.length - 2] + "." + p[p.length - 1];
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, String url) {
            if (url == null) return false;
            if (!url.startsWith("http")) {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) { }
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView v, String url, Bitmap icon) {
            a.loadFailed = false;
            a.startProgress();
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            if (a.urlBar != null) {
                a.urlBar.setUrl(url);
                a.urlBar.showHome(url == null || !url.startsWith(HOME));
            }
            // onReceivedError runs before this; keep its message on screen.
            if (a.loadFailed) return;
            a.hideStatus();
            a.injectNav();
            a.web.requestFocus();
            a.showHint();
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
            if (req.isForMainFrame()) {
                a.loadFailed = true;
                a.showStatus(FAILED);
            }
        }
    }

    static class Chrome extends WebChromeClient {
        final Main a;
        Chrome(Main m) { a = m; }

        @Override
        public boolean onCreateWindow(WebView v, boolean d, boolean u, Message r) {
            return false; // sin popups publicitarias
        }

        @Override
        public void onProgressChanged(WebView v, int progress) {
            a.moveProgress(progress);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback cb) {
            a.enterFullscreen(view, cb);
        }

        @Override
        public void onHideCustomView() {
            a.exitFullscreen(false);
        }
    }

    // ======================= lifecycle =======================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // only debuggable builds expose the page to DevTools
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        for (String host : AD_HOSTS) adSet.add(host);

        dens = getResources().getDisplayMetrics().density;
        int vw = getResources().getDisplayMetrics().widthPixels;

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportZoom(false);
        s.setLoadsImagesAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 14; SmartTV) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36");

        web.setWebViewClient(new PageClient(this));
        web.setWebChromeClient(new Chrome(this));
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        // capa de video a pantalla completa
        videoHolder = new FrameLayout(this);
        videoHolder.setBackgroundColor(Color.BLACK);
        videoHolder.setVisibility(View.GONE);
        root.addView(videoHolder, new FrameLayout.LayoutParams(-1, -1));

        // progreso de carga: una línea fina arriba, sin texto
        progress = new View(this);
        progress.setBackgroundColor(0xFF22D3EE);
        progress.setPivotX(0);
        progress.setVisibility(View.GONE);
        root.addView(progress, new FrameLayout.LayoutParams(-1, (int) (3 * dens), Gravity.TOP));

        // estado: error y pista. Abajo a la izquierda, dentro del area segura
        status = buildStatus();
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.START);
        slp.leftMargin = (int) (48 * dens);
        slp.bottomMargin = (int) (40 * dens);
        root.addView(status, slp);

        // barra de URL
        urlBar = new UrlBar(this);
        urlBar.setSubmit(this);
        urlBar.setHome(HOME);
        urlBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                Math.min((int) (860 * dens), vw - (int) (96 * dens)), -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        blp.topMargin = (int) (24 * dens);
        root.addView(urlBar, blp);

        setContentView(root);
        web.requestFocus();
        web.loadUrl(HOME);
    }

    TextView buildStatus() {
        TextView t = new TextView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xDD0B0F14);
        bg.setCornerRadius(20f * dens);
        bg.setStroke((int) (1 * dens), 0x33FFFFFF);
        t.setBackground(bg);
        t.setTextColor(0xFFE8EAED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        int ph = (int) (22 * dens), pv = (int) (12 * dens);
        t.setPadding(ph, pv, ph, pv);
        t.setVisibility(View.GONE);
        return t;
    }

    void startProgress() {
        progress.setVisibility(View.VISIBLE);
        progress.setAlpha(1f);
        progress.setScaleX(0f);
    }

    void moveProgress(int p) {
        progress.setScaleX(p / 100f);
        if (p >= 100) {
            progress.animate().alpha(0f).setDuration(300).withEndAction(new HideProgress(this));
        }
    }

    static class HideProgress implements Runnable {
        final Main a;
        HideProgress(Main m) { a = m; }
        public void run() { a.progress.setVisibility(View.GONE); }
    }

    void showStatus(String text) {
        hintVisible = false;
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    void hideStatus() {
        hintVisible = false;
        status.setVisibility(View.GONE);
    }

    // Shown once, after the first page, so people learn where the URL bar lives.
    void showHint() {
        if (hintShown || fullscreen) return;
        hintShown = true;
        hintVisible = true;
        status.setText(HINT);
        status.setVisibility(View.VISIBLE);
        h.postDelayed(new HintTimeout(this), HINT_MS);
    }

    static class HintTimeout implements Runnable {
        final Main a;
        HintTimeout(Main m) { a = m; }
        public void run() {
            if (a.hintVisible) a.hideStatus();
        }
    }

    // ======================= fullscreen de video =======================
    void enterFullscreen(View v, WebChromeClient.CustomViewCallback cb) {
        if (cb == null) return;
        if (fullscreen) { cb.onCustomViewHidden(); return; }
        fullscreen = true;
        customCb = cb;
        urlBar.setVisibility(View.GONE);
        hideStatus();

        if (v.getParent() != null) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
        videoHolder.addView(v, new FrameLayout.LayoutParams(-1, -1));
        videoHolder.setVisibility(View.VISIBLE);
        applyImmersive(true);
    }

    // byUser: the app is leaving fullscreen (BACK). The WebView must be told,
    // or it refuses the next fullscreen request for the rest of the session.
    void exitFullscreen(boolean byUser) {
        if (!fullscreen) return;
        fullscreen = false;
        WebChromeClient.CustomViewCallback cb = customCb;
        customCb = null;
        if (byUser && cb != null) cb.onCustomViewHidden();
        videoHolder.removeAllViews();
        videoHolder.setVisibility(View.GONE);
        applyImmersive(false);
        web.requestFocus();
    }

    void applyImmersive(boolean on) {
        getWindow().getDecorView().setSystemUiVisibility(on
                ? View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                  | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ======================= UrlBar.Submit =======================
    public void onUrl(String t) {
        if (TextUtils.isEmpty(t)) return;
        if (!t.startsWith("http")) {
            if (t.contains(" ") || t.indexOf('.') < 0) {
                t = "https://duckduckgo.com/?q=" + Uri.encode(t);
            } else {
                t = "https://" + t;
            }
        }
        web.loadUrl(t);
        hideUrlBar();
    }

    void showUrlBar() {
        hideStatus();
        urlBar.setVisibility(View.VISIBLE);
        urlBar.focusDefault();
    }

    void hideUrlBar() {
        urlBar.setVisibility(View.GONE);
        web.requestFocus();
    }

    // ======================= navegación por foco =======================
    void injectNav() {
        web.evaluateJavascript(navScript(), null);
    }

    String navScript() {
        if (navSource != null) return navSource;
        StringBuilder sb = new StringBuilder();
        try {
            InputStream in = getAssets().open("tvnav.js");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
        } catch (IOException e) {
            Log.e(TAG, "asset tvnav.js missing", e);
        }
        navSource = sb.toString();
        return navSource;
    }

    void nav(int dx, int dy) {
        web.evaluateJavascript("window.__ompNav&&window.__ompNav.move(" + dx + "," + dy + ")",
                new NavDone());
    }

    static class NavDone implements ValueCallback<String> {
        public void onReceiveValue(String v) { }
    }

    void activate() {
        if (playerMode) return;
        web.evaluateJavascript("window.__ompNav&&window.__ompNav.tap()", new TapDone(this));
    }

    // OK with nothing to stop on but a player on screen: take the remote into it.
    static class PlayerDone implements ValueCallback<String> {
        final Main a;
        PlayerDone(Main m) { a = m; }
        public void onReceiveValue(String v) {
            if (v == null || "null".equals(v)) return;
            String clean = v.replace("\"", "");
            if (clean.startsWith("TAP:")) {
                // a poster with a play button: touch it, then look for the player
                String[] p = clean.substring(4).split(",");
                try {
                    float s = a.web.getScale();
                    a.tapView(a.web, Integer.parseInt(p[0]) * s, Integer.parseInt(p[1]) * s);
                    Log.d(TAG, "poster tap " + p[0] + "," + p[1]);
                } catch (RuntimeException e) {
                    Log.e(TAG, "bad poster tap " + clean);
                    return;
                }
                a.h.postDelayed(new PlayerLater(a), 1200);
                return;
            }
            a.playerMode = true;
            Log.d(TAG, "player " + clean);
        }
    }

    static class PlayerLater implements Runnable {
        final Main a;
        PlayerLater(Main m) { a = m; }
        public void run() {
            a.web.evaluateJavascript("window.__ompNav&&window.__ompNav.playerFrame()", new PlayerDone(a));
        }
    }

    // Sends a real touch so the page sees a trusted gesture: links follow,
    // buttons act, and fullscreen requests are allowed.
    void tapView(View target, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        target.dispatchTouchEvent(down);
        down.recycle();
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0);
        target.dispatchTouchEvent(up);
        up.recycle();
    }

    static class TapDone implements ValueCallback<String> {
        final Main a;
        TapDone(Main m) { a = m; }
        public void onReceiveValue(String v) {
            if (v == null || "null".equals(v)) {
                // nothing to stop on: a player on screen takes the remote instead
                a.web.evaluateJavascript("window.__ompNav&&window.__ompNav.enterPlayer()", new PlayerDone(a));
                return;
            }
            String[] p = v.replace("\"", "").split(",");
            if (p.length < 3) return;
            try {
                float s = a.web.getScale();
                float x = Integer.parseInt(p[0]) * s;
                float y = Integer.parseInt(p[1]) * s;
                Log.d(TAG, "tap css=" + p[0] + "," + p[1] + " scale=" + s + " media=" + p[2]);
                a.tapView(a.web, x, y);
                if ("1".equals(p[2])) {
                    a.playerMode = true;
                    a.h.postDelayed(new FullscreenFallback(a), FULLSCREEN_FALLBACK_MS);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "bad tap reply " + v);
            }
        }
    }

    static class FullscreenFallback implements Runnable {
        final Main a;
        FullscreenFallback(Main m) { a = m; }
        public void run() {
            if (a.fullscreen) return;
            a.web.evaluateJavascript("window.__ompNav&&window.__ompNav.fullscreen()", null);
        }
    }

    // ======================= teclas =======================
    static boolean isDpad(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    // Players show their control bar on pointer activity. A hover move (mouse
    // source, so it does not count as a tap) over the player wakes it without
    // pausing anything.
    void showPlayerControls() {
        long t = SystemClock.uptimeMillis();
        float x = videoHolder.getWidth() > 0 ? videoHolder.getWidth() / 2f : web.getWidth() / 2f;
        float y = videoHolder.getHeight() > 0 ? videoHolder.getHeight() / 2f : web.getHeight() / 2f;
        MotionEvent.PointerProperties[] props = { new MotionEvent.PointerProperties() };
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] coords = { new MotionEvent.PointerCoords() };
        coords[0].x = x;
        coords[0].y = y;
        MotionEvent hover = MotionEvent.obtain(t, t, MotionEvent.ACTION_HOVER_MOVE, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        web.dispatchGenericMotionEvent(hover);
        hover.recycle();
    }

    // A key press delivered to the page: it reaches whichever frame has focus.
    void sendKey(int code, int meta) {
        long t = SystemClock.uptimeMillis();
        web.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta));
        web.dispatchKeyEvent(new KeyEvent(t, t + 1, KeyEvent.ACTION_UP, code, 0, meta));
    }

    static boolean isBarKey(int code) {
        return code == KeyEvent.KEYCODE_PROG_RED || code == KeyEvent.KEYCODE_SEARCH
                || code == KeyEvent.KEYCODE_MENU;
    }

    static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;

        // address bar: red key, or the Search / Menu keys most remotes have
        if (down && ev.getRepeatCount() == 0 && isBarKey(code) && urlBar.getVisibility() != View.VISIBLE) {
            showUrlBar();
            return true;
        }

        // la barra de URL usa el foco nativo de Android (campo, botón Ir)
        if (urlBar.getVisibility() == View.VISIBLE) {
            return super.dispatchKeyEvent(ev);
        }

        // en vídeo a pantalla completa: OK toca el centro (controles del reproductor)
        if (fullscreen) {
            if (down && isOk(code) && ev.getRepeatCount() == 0) {
                tapView(videoHolder, videoHolder.getWidth() / 2f, videoHolder.getHeight() / 2f);
                return true;
            }
            return super.dispatchKeyEvent(ev);
        }

        // reproductor: izquierda/derecha saltan en el video, OK pausa o reanuda
        // (Enter). Al mover, la barra de controles del reproductor aparece.
        if (playerMode) {
            if (isDpad(code) && down) showPlayerControls();
            if (isOk(code)) {
                if (down && ev.getRepeatCount() == 0) sendKey(KeyEvent.KEYCODE_ENTER, 0);
                return true;
            }
            return super.dispatchKeyEvent(ev);
        }

        // navegación de página: el auto-repeat del mando da el movimiento continuo
        if (isDpad(code)) {
            if (down) {
                int dx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1
                        : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
                int dy = code == KeyEvent.KEYCODE_DPAD_UP ? -1
                        : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
                nav(dx, dy);
            }
            return true;
        }

        if (isOk(code)) {
            // consumir también el "up" para que la página no reciba un click suelto
            if (down && ev.getRepeatCount() == 0) {
                if (loadFailed) web.reload();
                else activate();
            }
            return true;
        }

        return super.dispatchKeyEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) { exitFullscreen(true); return; }
        if (playerMode) { playerMode = false; return; }
        if (urlBar.getVisibility() == View.VISIBLE) { hideUrlBar(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed(); // raíz: sale de la app, como cualquier app de TV
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent ev) {
        if (code == KeyEvent.KEYCODE_PROG_RED) {
            showUrlBar();
            return true;
        }
        return super.onKeyUp(code, ev);
    }
}
