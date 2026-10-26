package com.omp.tvweb;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * Puente JS -> nativo. Cada método se ejecuta en el hilo de JS, así que
 * reenviar a la vista se hace con post() para no bloquear el render.
 */
public class NativeBridge {

    public interface Host {
        void onHover(int x, int y, int w, int h, int vis, int overflowTop);
        void onReady(boolean hasOmp);
    }

    private final Host host;

    public NativeBridge(Host h) {
        this.host = h;
    }

    @JavascriptInterface
    public void hover(int x, int y, int w, int h, int vis, int overflowTop) {
        host.onHover(x, y, w, h, vis, overflowTop);
    }

    @JavascriptInterface
    public void ready() {
        host.onReady(true);
    }
}
