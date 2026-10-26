package com.omp.tvweb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

/**
 * Resaltado de objetivo: dibuja un contorno con brillo y un velo interior
 * muy tenue. Se pinta como View propia encima del WebView, así que no
 * depende del CSS del sitio y no se pierde al repintar.
 */
public class HighlightView extends View {

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float radius;

    public HighlightView(Context c, int accent, float density) {
        super(c);
        setWillNotDraw(false);

        radius = 8f * density;

        glow.setStyle(Paint.Style.STROKE);
        glow.setColor((accent & 0x00FFFFFF) | 0x33000000);
        glow.setStrokeWidth(9f * density);

        border.setStyle(Paint.Style.STROKE);
        border.setColor(accent);
        border.setStrokeWidth(3f * density);

        veil.setStyle(Paint.Style.FILL);
        veil.setColor((accent & 0x00FFFFFF) | 0x14FFFFFF);
    }

    public float radiusPx() {
        return radius;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float inset = border.getStrokeWidth() + 2f;
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float r = Math.min(radius, Math.min(box.width(), box.height()) / 2f);
        if (box.width() <= 0 || box.height() <= 0) return;

        c.drawRoundRect(box, r, r, veil);
        c.drawRoundRect(box, r, r, glow);
        c.drawRoundRect(box, r, r, border);
    }
}
