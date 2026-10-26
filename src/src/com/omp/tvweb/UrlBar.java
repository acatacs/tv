package com.omp.tvweb;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
 * Barra de búsqueda y direcciones: una sola píldora compacta arriba, centrada.
 * [Inicio] [ campo: busca o escribe una dirección ] [Ir]. Se abre con el
 * campo seleccionado, así escribir reemplaza el texto actual.
 */
public class UrlBar extends LinearLayout {

    public interface Submit { void onUrl(String url); }

    private final EditText field;
    private final Button go;
    private final Button home;
    private Submit submit;
    private String homeUrl;

    public UrlBar(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int ph = (int) (14 * d), pv = (int) (10 * d);
        setPadding(ph, pv, ph, pv);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2121A22);
        bg.setCornerRadius(32f * d);
        bg.setStroke((int) (1 * d), 0x33FFFFFF);
        setBackground(bg);

        home = pill(c, d, "Inicio");
        home.setVisibility(GONE);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.rightMargin = (int) (10 * d);
        addView(home, hlp);
        home.setOnClickListener(new HomeListener(this));

        field = new EditText(c);
        field.setSingleLine(true);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0xFF7A8794);
        field.setHint("Buscar película, serie o dirección");
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setPadding((int) (12 * d), (int) (8 * d), (int) (12 * d), (int) (8 * d));
        addView(field, new LinearLayout.LayoutParams(0, -2, 1f));

        go = pill(c, d, "Ir");
        go.setTextColor(0xFF06222A);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF22D3EE);
        gb.setCornerRadius(22f * d);
        go.setBackground(gb);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.leftMargin = (int) (10 * d);
        addView(go, glp);

        field.setOnEditorActionListener(new SubmitListener(this));
        go.setOnClickListener(new GoListener(this));
        field.setOnFocusChangeListener(new SelectOnFocus());
    }

    // Button styled as a chip, with a visible focus ring for the remote.
    private static Button pill(Context c, float d, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTextColor(0xFFE8EAED);
        GradientDrawable idle = new GradientDrawable();
        idle.setColor(0xFF1B232D);
        idle.setCornerRadius(22f * d);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xFF26323E);
        focused.setCornerRadius(22f * d);
        focused.setStroke((int) (3 * d), 0xFF22D3EE);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] { android.R.attr.state_focused }, focused);
        bg.addState(new int[] {}, idle);
        b.setBackground(bg);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding((int) (18 * d), (int) (8 * d), (int) (18 * d), (int) (8 * d));
        return b;
    }

    public void setSubmit(Submit s) { this.submit = s; }

    // The only shortcut is home (lamovie.org). Shown when the page is elsewhere.
    public void setHome(String url) {
        homeUrl = url;
    }

    public void showHome(boolean show) {
        home.setVisibility(show ? VISIBLE : GONE);
    }

    // Open on the field with its text selected: typing replaces what is there.
    public void focusDefault() {
        field.requestFocus();
        field.selectAll();
    }

    public void focusField() { field.requestFocus(); }

    void fire() { if (submit != null) submit.onUrl(field.getText().toString().trim()); }

    public void setUrl(String u) { if (u != null && !u.equals(field.getText().toString())) field.setText(u); }

    static class HomeListener implements View.OnClickListener {
        final UrlBar b;
        HomeListener(UrlBar bar) { b = bar; }
        public void onClick(View v) { if (b.submit != null && b.homeUrl != null) b.submit.onUrl(b.homeUrl); }
    }

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

    static class SelectOnFocus implements View.OnFocusChangeListener {
        public void onFocusChange(View v, boolean has) {
            if (has) { ((EditText) v).selectAll(); }
        }
    }
}
