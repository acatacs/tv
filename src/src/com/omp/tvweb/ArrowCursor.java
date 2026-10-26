package com.omp.tvweb;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * Puntero estilo escritorio: flecha con relleno blanco, contorno oscuro
 * suave, esquinas redondeadas y sombra difusa. Se dibuja con antialias y
 * un halo translúcido para que se lea sobre fondos claros y oscuros.
 */
public class ArrowCursor extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final Path haloPath = new Path();
    private final int iw, ih;

    public ArrowCursor(float sizePx) {
        iw = Math.round(sizePx * 1.05f);
        ih = Math.round(sizePx * 1.55f);

        float u = iw / 24f;   // unidad de diseño

        // flecha clásica, proporciones de escritorio, punta redondeada
        arrow.reset();
        arrow.moveTo(1.0f * u, 1.0f * u);
        arrow.lineTo(1.0f * u, 19.5f * u);
        arrow.lineTo(5.9f * u, 15.2f * u);
        arrow.lineTo(9.6f * u, 23.2f * u);
        arrow.lineTo(12.9f * u, 21.7f * u);
        arrow.lineTo(9.3f * u, 13.9f * u);
        arrow.lineTo(15.3f * u, 13.5f * u);
        arrow.close();

        haloPath.set(arrow);
        haloPath.offset(1.1f * u, 1.6f * u);

        halo.setStyle(Paint.Style.FILL);
        halo.setColor(0x55000000);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.WHITE);

        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0xE6000000);
        edge.setStrokeWidth(1.35f * u);
        edge.setStrokeJoin(Paint.Join.ROUND);
        edge.setStrokeCap(Paint.Cap.ROUND);

        core.setStyle(Paint.Style.STROKE);
        core.setColor(0x2EFFFFFF);
        core.setStrokeWidth(0.5f * u);
        core.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        c.drawPath(haloPath, halo);
        c.drawPath(arrow, fill);
        c.drawPath(arrow, edge);
        c.drawPath(arrow, core);
    }

    @Override
    public void setAlpha(int a) {
        fill.setAlpha(a);
        edge.setAlpha(a);
        halo.setAlpha(a);
        core.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        edge.setColorFilter(cf);
        halo.setColorFilter(cf);
    }

    @Override
    public int getIntrinsicWidth() {
        return iw;
    }

    @Override
    public int getIntrinsicHeight() {
        return ih;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
