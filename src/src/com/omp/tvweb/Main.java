package com.omp.tvweb;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

public class Main extends Activity implements NativeBridge.Host, UrlBar.Submit {

    static final String HOME = "https://lamovie.org";
    static final int ACCENT = 0xFF4FC3F7;
    static final int ACCENT_DIM = 0xCCB0BEC5;

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
        "tsyndicate.com", "trafficstars.com", "adsco.re"
    };

    // ---- vistas ----
    FrameLayout root;
    WebView web;
    UrlBar urlBar;
    HighlightView hilite;
    ImageView cursor;
    FrameLayout videoHolder;

    // ---- estado del puntero ----
    float cx, cy;              // posición real (destino)
    float sx, sy;              // posición suavizada (dibujada)
    int vw, vh;
    float dens;
    boolean pressed;
    final Set<Integer> held = new HashSet<Integer>();
    final Handler h = new Handler();
    boolean cursorVisible = true;

    boolean bridgeOk = false;
    boolean fullscreen = false;
    int hilW = -1, hilH = -1;

    final Set<String> adSet = new HashSet<String>();

    // ======================= bridge =======================
    public void onHover(final int x, final int y, final int w, final int hh,
                        final int vis, final int over) {
        android.util.Log.d("TVWEB", "hover " + x + "," + y + " " + w + "x" + hh + " vis=" + vis);
        runOnUiThread(new HoverApply(this, x, y, w, hh, vis, over));
    }

    public void onReady(boolean ok) {
        bridgeOk = ok;
        android.util.Log.d("TVWEB", "bridge ready=" + ok);
    }

    static class HoverApply implements Runnable {
        final Main a; final int x, y, w, hh, vis, over;
        HoverApply(Main m, int x, int y, int w, int hh, int vis, int over) {
            a = m; this.x = x; this.y = y; this.w = w; this.hh = hh;
            this.vis = vis; this.over = over;
        }
        public void run() {
            if (a.hilite == null) return;
            if (a.fullscreen || vis == 0 || w < 6 || hh < 6) {
                a.hilite.setVisibility(View.INVISIBLE);
                return;
            }
            int top = y + over;
            int height = hh - over;
            if (top < 0) { height += top; top = 0; }
            if (height < 6) { a.hilite.setVisibility(View.INVISIBLE); return; }

            int pad = (int) (4 * a.dens);
            int nw = w + pad * 2;
            int nh = height + pad * 2;
            int nx = x - pad;
            int ny = top - pad;

            if (nw == a.hilW && nh == a.hilH) {
                a.hilite.setTranslationX(nx);
                a.hilite.setTranslationY(ny);
            } else {
                a.hilW = nw;
                a.hilH = nh;
                FrameLayout.LayoutParams lp =
                        (FrameLayout.LayoutParams) a.hilite.getLayoutParams();
                lp.width = nw;
                lp.height = nh;
                a.hilite.setLayoutParams(lp);
                a.hilite.setTranslationX(nx);
                a.hilite.setTranslationY(ny);
            }
            if (a.hilite.getVisibility() != View.VISIBLE) {
                a.hilite.setVisibility(View.VISIBLE);
            }
        }
    }

    // ======================= web client =======================
    static class AdBlockClient extends WebViewClient {
        final Main a;
        AdBlockClient(Main m) { a = m; }

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
        public void onPageFinished(WebView v, String url) {
            if (a.urlBar != null) a.urlBar.setUrl(url);
            a.web.requestFocus();
            a.installProbe();
            a.probe();
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
        public void onShowCustomView(View view, CustomViewCallback cb) {
            a.enterFullscreen(view, cb);
        }

        @Override
        public void onHideCustomView() {
            a.exitFullscreen();
        }
    }

    // ======================= lifecycle =======================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        for (int i = 0; i < AD_HOSTS.length; i++) adSet.add(AD_HOSTS[i]);

        dens = getResources().getDisplayMetrics().density;
        vw = getResources().getDisplayMetrics().widthPixels;
        vh = getResources().getDisplayMetrics().heightPixels;
        cx = vw * 0.5f;
        cy = vh * 0.45f;
        sx = cx;
        sy = cy;

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

        web.addJavascriptInterface(new NativeBridge(this), "omp");
        web.setWebViewClient(new AdBlockClient(this));
        web.setWebChromeClient(new Chrome(this));
        web.loadUrl(HOME);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        // capa de video a pantalla completa
        videoHolder = new FrameLayout(this);
        videoHolder.setBackgroundColor(Color.BLACK);
        videoHolder.setVisibility(View.GONE);
        root.addView(videoHolder, new FrameLayout.LayoutParams(-1, -1));

        // resaltado
        hilite = new HighlightView(this, ACCENT, dens);
        hilite.setVisibility(View.INVISIBLE);
        root.addView(hilite, new FrameLayout.LayoutParams(0, 0));

        // puntero
        cursor = new ImageView(this);
        cursor.setImageDrawable(new ArrowCursor(19f * dens));
        root.addView(cursor, new FrameLayout.LayoutParams(
                (int) (19f * dens * 1.05f), (int) (19f * dens * 1.55f)));

        // barra de URL
        urlBar = new UrlBar(this);
        urlBar.setSubmit(this);
        urlBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                Math.min((int) (860 * dens), vw - (int) (96 * dens)), -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        blp.topMargin = (int) (24 * dens);
        root.addView(urlBar, blp);

        setContentView(root);
        web.requestFocus();

        h.post(new SmoothLoop(this));
    }

    // ======================= animación del puntero =======================
    static class SmoothLoop implements Runnable {
        final Main a;
        SmoothLoop(Main m) { a = m; }

        public void run() {
            float k = 0.34f;
            a.sx += (a.cx - a.sx) * k;
            a.sy += (a.cy - a.sy) * k;
            if (Math.abs(a.cx - a.sx) < 0.5f) a.sx = a.cx;
            if (Math.abs(a.cy - a.sy) < 0.5f) a.sy = a.cy;

            // translation: no provoca relayout, solo composición
            a.cursor.setTranslationX(a.sx);
            a.cursor.setTranslationY(a.sy);

            float sc = a.pressed ? 0.88f : 1.0f;
            a.cursor.setScaleX(sc);
            a.cursor.setScaleY(sc);
            a.cursor.setAlpha(a.cursorVisible ? 1f : 0f);

            a.h.postDelayed(this, 16);
        }
    }

    // ======================= fullscreen de video =======================
    void enterFullscreen(View v, WebChromeClient.CustomViewCallback cb) {
        if (cb == null) return;
        fullscreen = true;
        cursorVisible = false;
        hilite.setVisibility(View.INVISIBLE);
        urlBar.setVisibility(View.GONE);

        if (v.getParent() != null) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
        videoHolder.addView(v, new FrameLayout.LayoutParams(-1, -1));
        videoHolder.setVisibility(View.VISIBLE);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    void exitFullscreen() {
        fullscreen = false;
        cursorVisible = true;
        videoHolder.removeAllViews();
        videoHolder.setVisibility(View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        web.requestFocus();
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
        urlBar.setVisibility(View.VISIBLE);
        urlBar.focusField();
    }

    void hideUrlBar() {
        urlBar.setVisibility(View.GONE);
        web.requestFocus();
    }

    // ======================= JS =======================
    void installProbe() {
        web.evaluateJavascript(navScript(), null);
    }

    String navScript() {
        if (navSource != null) return navSource;
        StringBuilder sb = new StringBuilder();
        try {
            java.io.InputStream in = getAssets().open("tvnav.js");
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            r.close();
        } catch (Exception e) {
            android.util.Log.e("TVWEB", "asset tvnav.js missing", e);
        }
        navSource = sb.toString();
        return navSource;
    }

    String navSource;

    void probe() {
        if (web == null) return;
        if (probePending) { probeAgain = true; return; }
        probePending = true;

        int px = Math.round(cx);
        int py = Math.round(cy);
        if (px == lastProbeX && py == lastProbeY && !probeAgain) {
            probePending = false;
            return;
        }
        lastProbeX = px;
        lastProbeY = py;
        probeAgain = false;

        web.evaluateJavascript("window.__ompAt?window.__ompAt(" + px + "," + py + "):'NO_PROBE'",
                new ProbeDone(this));
    }

    boolean probePending = false;
    boolean probeAgain = false;
    int lastProbeX = -1, lastProbeY = -1;

    static class ProbeDone implements android.webkit.ValueCallback<String> {
        final Main a;
        ProbeDone(Main m) { a = m; }
        public void onReceiveValue(String v) {
            android.util.Log.d("TVWEB", "probe result=" + v);
            a.probePending = false;
            if (a.probeAgain) { a.probeAgain = false; a.probe(); }
        }
    }

    void click() {
        float px = cx;
        float py = cy;

        // toque real (funciona con reproductores y divs)
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, px, py, 0);
        web.dispatchTouchEvent(down);
        down.recycle();
        MotionEvent up = MotionEvent.obtain(t, t + 55, MotionEvent.ACTION_UP, px, py, 0);
        web.dispatchTouchEvent(up);
        up.recycle();

        // además click semántico, por si el sitio usa on('click') sobre el contenedor
        web.evaluateJavascript("window.__ompClickAt&&window.__ompClickAt("
                + Math.round(px) + "," + Math.round(py) + ")", null);

        // si el objetivo es un <video>, intentar pantalla completa
        web.evaluateJavascript("window.__ompBigVideo&&window.__ompBigVideo()", null);
    }

    // ======================= teclas =======================
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();

        if (fullscreen) {
            // en reproducción: ATRÁS sale, OK/PLAY gestiona, flechas al player
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_BACK) {
                exitFullscreen();
                return true;
            }
            return super.dispatchKeyEvent(ev);
        }

        if (urlBar != null && urlBar.getVisibility() == View.VISIBLE) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_BACK) {
                hideUrlBar();
                return true;
            }
            return super.dispatchKeyEvent(ev);
        }

        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (ev.getRepeatCount() == 0) {
                    held.add(code);
                    startMove();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                pressed = true;
                click();
                return true;
            }
            if (code == KeyEvent.KEYCODE_BACK) {
                if (web.canGoBack()) { web.goBack(); return true; }
                return true;
            }
        } else if (ev.getAction() == KeyEvent.ACTION_UP) {
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                held.remove(code);
                probe();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                pressed = false;
                probe();
                return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }

    void startMove() {
        if (moveScheduled) return;
        moveScheduled = true;
        h.postDelayed(new Mover(this), 0);
    }

    boolean moveScheduled = false;
    int holdTicks = 0;

    float heldStep() {
        // arranque fino (precision) y aceleracion suave al mantener
        int t = holdTicks;
        if (t < 4) return 9f * dens;
        if (t < 10) return 14f * dens;
        if (t < 18) return 20f * dens;
        return 27f * dens;
    }

    static class Mover implements Runnable {
        final Main a;
        Mover(Main m) { a = m; }

        public void run() {
            a.moveScheduled = false;
            if (a.held.isEmpty()) return;

            int dx = 0, dy = 0;
            if (a.held.contains(KeyEvent.KEYCODE_DPAD_LEFT)) dx = -1;
            if (a.held.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) dx = 1;
            if (a.held.contains(KeyEvent.KEYCODE_DPAD_UP)) dy = -1;
            if (a.held.contains(KeyEvent.KEYCODE_DPAD_DOWN)) dy = 1;

            a.holdTicks++;

            // primera pulsacion: salto magnetico al siguiente objetivo real
            if (a.holdTicks == 1) {
                a.snapTo(dx, dy);
                return;
            }

            // mantener pulsado: desplazamiento fino y progresivo
            float step = a.heldStep();
            float margin = 54f * a.dens;
            if (dy > 0 && a.cy > a.vh - margin) {
                a.web.evaluateJavascript("window.__ompScroll&&window.__ompScroll(200)", null);
                a.h.postDelayed(this, 110);
                return;
            }
            if (dy < 0 && a.cy < margin) {
                a.web.evaluateJavascript("window.__ompScroll&&window.__ompScroll(-200)", null);
                a.h.postDelayed(this, 110);
                return;
            }

            a.cx += dx * step;
            a.cy += dy * step;
            a.clampCursor();
            a.probe();
            a.h.postDelayed(this, 45);
        }
    }

    void clampCursor() {
        if (cx < 0) cx = 0;
        if (cy < 0) cy = 0;
        if (cx > vw - 1) cx = vw - 1;
        if (cy > vh - 1) cy = vh - 1;
    }

    int lastDx, lastDy;

    void snapTo(int dx, int dy) {
        lastDx = dx; lastDy = dy;
        String args = Math.round(cx) + "," + Math.round(cy) + "," + dx + "," + dy;
        web.evaluateJavascript(
                "window.__ompNext?window.__ompNext(" + args + "):null",
                new SnapDone(this));
    }

    static class SnapDone implements android.webkit.ValueCallback<String> {
        final Main a;
        SnapDone(Main m) { a = m; }
        public void onReceiveValue(String v) {
            if (v == null || v.equals("null")) {
                // sin objetivo en esa direccion: desplazamiento en bloque
                float step = 90f * a.dens;
                a.cx += a.lastDx * step;
                a.cy += a.lastDy * step;
                a.clampCursor();
                a.probe();
                return;
            }
            String clean = v.replace("\"", "");
            int comma = clean.indexOf(',');
            if (comma < 0) { a.probe(); return; }
            try {
                a.cx = Float.parseFloat(clean.substring(0, comma));
                a.cy = Float.parseFloat(clean.substring(comma + 1));
                a.clampCursor();
            } catch (Exception ignored) { }
            a.probe();
        }
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
