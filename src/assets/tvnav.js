(function () {
  if (window.__ompP) return;
  window.__ompP = 1;

  try { omp.ready(); } catch (e) {}

  function area(r) { return r.width * r.height; }

  // Nearest ancestor that is genuinely clickable, or null.
  function clickable(el) {
    var n = el, i = 0, t;
    while (n && n !== document.body && i < 12) {
      t = n.tagName ? n.tagName.toLowerCase() : '';
      if (t === 'a' || t === 'button' || t === 'input' ||
          t === 'select' || t === 'textarea') return n;
      if (n.getAttribute && (n.getAttribute('role') === 'button' ||
                             n.hasAttribute('onclick'))) return n;
      try { if (getComputedStyle(n).cursor === 'pointer') return n; } catch (e) {}
      n = n.parentElement;
      i++;
    }
    return null;
  }

  // Largest card-like ancestor that still reads as one object rather than a
  // whole page section.
  function card(el) {
    var best = null, n = el, i = 0, r, a, t, interactive, looksCard, st;
    var VW = window.innerWidth, VH = window.innerHeight;
    while (n && n !== document.body && i < 10) {
      r = n.getBoundingClientRect();
      a = area(r);
      t = n.tagName ? n.tagName.toLowerCase() : '';
      interactive = (t === 'a' || t === 'button') ||
        (n.hasAttribute && (n.hasAttribute('onclick') ||
                            n.hasAttribute('tabindex') ||
                            n.getAttribute('role') === 'button'));
      looksCard = false;
      try {
        st = getComputedStyle(n);
        looksCard = st.cursor === 'pointer' ||
                    parseFloat(st.borderRadius || '0') > 3 ||
                    (n.children.length > 0 && a > 20000);
      } catch (e) {}
      if (a > 4000 && a < VW * VH * 0.30 && (interactive || looksCard)) best = n;
      else if (a >= VW * VH * 0.30) break;
      n = n.parentElement;
      i++;
    }
    return best;
  }

  function pickTarget(el) {
    if (!el) return null;
    var c = clickable(el);
    return c ? c : card(el);
  }

  // Ask the host to outline whatever sits under (x, y).
  window.__ompAt = function (x, y) {
    var el = null;
    try { el = document.elementFromPoint(x, y); } catch (e) {}
    var t = pickTarget(el);
    if (!t) { try { omp.hover(0, 0, 0, 0, 0, 0); } catch (e) {} return; }
    var r = t.getBoundingClientRect();
    var over = r.top < 0 ? Math.round(-r.top) : 0;
    try {
      omp.hover(Math.round(r.left), Math.round(r.top),
                Math.round(r.width), Math.round(r.height), 1, over);
    } catch (e) {}
  };

  // Three escalating click strategies: semantic, synthetic events, then the
  // host's real MotionEvent (which players need).
  window.__ompClickAt = function (x, y) {
    var el = null;
    try { el = document.elementFromPoint(x, y); } catch (e) {}
    var c = clickable(el);
    if (c && c.click) { try { c.click(); return true; } catch (e) {} }
    if (!el) return false;
    var o = {
      bubbles: true, cancelable: true,
      clientX: x, clientY: y, screenX: x, screenY: y,
      view: window, button: 0, detail: 1
    };
    try { el.dispatchEvent(new MouseEvent('mousedown', o)); } catch (e) {}
    try { el.dispatchEvent(new MouseEvent('mouseup', o)); } catch (e) {}
    try { el.dispatchEvent(new MouseEvent('click', o)); } catch (e) {}
    return true;
  };

  // Every element worth snapping to. Micro-links are dropped so the pointer
  // does not stop on a word inside a paragraph.
  function candidates() {
    var out = [], i, n, r, st, a, t, big, intr;
    var all = document.querySelectorAll(
      'a[href],button,[role=button],input,select,textarea,video,[onclick]');
    for (i = 0; i < all.length; i++) {
      n = all[i];
      r = n.getBoundingClientRect();
      a = area(r);
      if (r.width < 40 || r.height < 22 || a < 1600) continue;
      if (r.bottom < 10 || r.top > window.innerHeight - 10) continue;
      if (r.right < 10 || r.left > window.innerWidth - 10) continue;
      try { st = getComputedStyle(n); } catch (e) { continue; }
      if (st.visibility === 'hidden' || st.display === 'none') continue;
      if (parseFloat(st.opacity || '1') < 0.1) continue;
      t = n.tagName ? n.tagName.toLowerCase() : '';
      intr = (t === 'button' || t === 'input' || t === 'video' ||
              n.hasAttribute('onclick') || n.getAttribute('role') === 'button');
      big = a > 24000;
      out.push({
        x: r.left + r.width / 2,
        y: r.top + r.height / 2,
        a: a,
        b: big ? 1 : 0,
        i: intr ? 1 : 0
      });
    }
    return out;
  }

  // Magnetic snap: half-plane filter along the pressed direction, then a score
  // that strongly prefers big, interactive elements over body text.
  window.__ompNext = function (cx, cy, dx, dy) {
    var cs = candidates(), best = null, bs = 1e12, i, c, vx, vy, d, px, py;
    for (i = 0; i < cs.length; i++) {
      c = cs[i];
      vx = c.x - cx;
      vy = c.y - cy;
      if (Math.abs(vx) < 26 && Math.abs(vy) < 22) continue;   // already there
      if (dx !== 0) {
        if (dx > 0 ? vx <= 4 : vx >= -4) continue;
        px = Math.abs(vx); py = Math.abs(vy);
      } else {
        if (dy > 0 ? vy <= 4 : vy >= -4) continue;
        px = Math.abs(vy); py = Math.abs(vx);
      }
      d = px + py * 2.0;
      if (c.b) d -= 170;
      if (c.i) d -= 110;
      if (c.a > 90000) d -= 90;
      if (d < bs) { bs = d; best = c; }
    }
    if (!best) return null;
    return Math.round(best.x) + ',' + Math.round(best.y);
  };

  window.__ompScroll = function (dy) { window.scrollBy(0, dy); };

  // Fallback fullscreen for players that never ask for it themselves.
  window.__ompBigVideo = function () {
    var vs = document.querySelectorAll('video'), i, v, w, p;
    for (i = 0; i < vs.length; i++) {
      v = vs[i];
      if (v.offsetWidth < 120 || v.offsetHeight < 90) continue;
      if (v.requestFullscreen) {
        try { v.requestFullscreen(); return true; } catch (e) {}
      }
      w = v.webkitEnterFullscreen || v.webkitEnterFullScreen;
      if (w) { try { w.call(v); return true; } catch (e) {} }
      p = v.parentElement;
      if (p && p.requestFullscreen) {
        try { p.requestFullscreen(); return true; } catch (e) {}
      }
    }
    return false;
  };
})();
