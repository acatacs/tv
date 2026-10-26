package com.omp.tvweb;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Barra de URL tipo TV: aparece arriba centrada, con píldora oscura,
 * campo grande y botón "Ir" enfocable con D-pad.
 */
public class UrlBar extends LinearLayout {

    public interface Submit { void onUrl(String url); }

    private final EditText field;
    private final Button go;
    private final LinearLayout row;
    private Submit submit;

    public UrlBar(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (28 * d);
        setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00B0F14);
        bg.setCornerRadius(18f * d);
        bg.setStroke((int) (1.5f * d), 0x4D22D3EE);
        setBackground(bg);

        row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        field = new EditText(c);
        field.setSingleLine(true);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0xFF7A8794);
        field.setHint("Escribe una dirección o busca");
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setPadding((int) (8 * d), (int) (10 * d), (int) (8 * d), (int) (10 * d));
        row.addView(field, new LinearLayout.LayoutParams(0, -2, 1f));

        go = new Button(c);
        go.setText("Ir");
        go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        go.setTextColor(0xFF06222A);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF22D3EE);
        gb.setCornerRadius(12f * d);
        go.setBackground(gb);
        go.setStateListAnimator(null);
        go.setPadding((int) (22 * d), (int) (8 * d), (int) (22 * d), (int) (8 * d));
        row.addView(go);

        addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = new TextView(c);
        tip.setText("OK confirma   ·   ATRÁS cierra");
        tip.setTextColor(0xFFA3AEB9);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tip.setPadding((int) (8 * d), (int) (8 * d), 0, 0);
        addView(tip);

        field.setOnEditorActionListener(new SubmitListener(this));
        go.setOnClickListener(new GoListener(this));
    }

    public void setSubmit(Submit s) { this.submit = s; }
    void fire() { if (submit != null) submit.onUrl(field.getText().toString().trim()); }
    public EditText field() { return field; }
    public Button button() { return go; }
    public void focusField() { field.requestFocus(); }
    public void setUrl(String u) { if (u != null && !u.equals(field.getText().toString())) field.setText(u); }

    static class GoListener implements View.OnClickListener {
        final UrlBar b;
        GoListener(UrlBar bar) { this.b = bar; }
        public void onClick(View v) { b.fire(); }
    }

    static class SubmitListener implements TextView.OnEditorActionListener {
        final UrlBar b;
        SubmitListener(UrlBar bar) { this.b = bar; }
        public boolean onEditorAction(TextView v, int action, KeyEvent ev) {
            if (action == EditorInfo.IME_ACTION_GO) { b.fire(); return true; }
            return false;
        }
    }
}
