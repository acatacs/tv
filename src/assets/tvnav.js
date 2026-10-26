// TV navigation for the embedded WebView. One focus ring moves with the
// D-pad, the way Android TV and tvOS move focus. There is no pointer: the
// remote never aims at pixels, it steps from one control to the next.
// The ring is a single overlay that glides between controls, follows the
// page while it scrolls, and only lands on controls that are really visible.
// Injected once per page load by Main.java.
(function () {
  'use strict';

  if (window.__ompNav) { return; }

  var RING_ID = 'omp-ring';
  var STYLE_ID = 'omp-nav-style';
  var GAP = 4;            // px between the control and the ring
  var MARGIN = 140;       // px kept free above and below the focus while scrolling
  var SETTLE_MS = 140;    // after scrolling stops, re-check the focus once
  var SELECTOR = 'a[href],button,input:not([type=hidden]),select,textarea,video,iframe,' +
                 '[role=button],[role=link],[onclick],[tabindex]:not([tabindex="-1"])';
  var MIN_SIZE = 24;      // smaller visible boxes are decoration, not targets
  var TEXT_ENTRY = /^(INPUT|TEXTAREA|SELECT)$/;
  var focused = null;
  var focusedBox = null;  // visible box of the focused control, CSS px
  var ring = null;
  var frame = 0;
  var settleTimer = 0;

  function mount() {
    if (document.getElementById(STYLE_ID)) { return; }
    var s = document.createElement('style');
    s.id = STYLE_ID;
    s.textContent =
      '#' + RING_ID + '{position:fixed;left:0;top:0;width:0;height:0;margin:0;' +
      'box-sizing:border-box;border:3px solid #22D3EE;pointer-events:none;opacity:0;' +
      'z-index:2147483646;will-change:left,top,width,height;' +
      'transition:left .18s cubic-bezier(.2,.8,.2,1),top .18s cubic-bezier(.2,.8,.2,1),' +
      'width .18s cubic-bezier(.2,.8,.2,1),height .18s cubic-bezier(.2,.8,.2,1),' +
      'border-radius .18s ease,opacity .12s ease}' +
      '#' + RING_ID + '.omp-on{opacity:1}' +
      '@media (prefers-reduced-motion:reduce){#' + RING_ID + '{transition:none}}';
    (document.head || document.documentElement).appendChild(s);

    ring = document.createElement('div');
    ring.id = RING_ID;
    (document.body || document.documentElement).appendChild(ring);

    window.addEventListener('scroll', onScroll, { capture: true, passive: true });
    window.addEventListener('resize', onScroll, { passive: true });
  }

  function onScroll() {
    if (frame) { return; }
    frame = requestAnimationFrame(function () {
      frame = 0;
      follow();
    });
    clearTimeout(settleTimer);
    settleTimer = setTimeout(settle, SETTLE_MS);
  }

  function live(el) {
    return !!el && el.isConnected !== false && el.getClientRects().length > 0;
  }

  function onScreen(b) {
    return b.right > 0 && b.bottom > 0 &&
           b.left < window.innerWidth && b.top < window.innerHeight;
  }

  // The part of an element's box that is not cut off by an overflow:hidden or
  // clip ancestor. Scroll containers do not cut: their hidden items are
  // reachable, and reveal() scrolls them into view.
  function visibleBox(el) {
    if (el.disabled || el.getAttribute('aria-hidden') === 'true') { return null; }
    var r = el.getBoundingClientRect();
    if (r.width < MIN_SIZE || r.height < MIN_SIZE) { return null; }
    var st = window.getComputedStyle(el);
    if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity) < 0.05) {
      return null;
    }
    var b = { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
    var n = el.parentElement;
    while (n && n !== document.documentElement) {
      var ps = window.getComputedStyle(n);
      if (ps.overflowX === 'hidden' || ps.overflowX === 'clip' ||
          ps.overflowY === 'hidden' || ps.overflowY === 'clip') {
        var c = n.getBoundingClientRect();
        b.left = Math.max(b.left, c.left);
        b.top = Math.max(b.top, c.top);
        b.right = Math.min(b.right, c.right);
        b.bottom = Math.min(b.bottom, c.bottom);
        if (b.right - b.left < MIN_SIZE || b.bottom - b.top < MIN_SIZE) { return null; }
      }
      n = n.parentElement;
    }
    return b;
  }

  // Every visible control, off-screen ones included so that moving down past
  // the fold reaches the next row. A control that contains another control is
  // dropped: the innermost one takes focus.
  function targets() {
    var all = document.querySelectorAll(SELECTOR), out = [], i, el, b;
    for (i = 0; i < all.length; i++) {
      el = all[i];
      b = visibleBox(el);
      if (b) {
        el.__ompT = 1;
        out.push({ el: el, b: b, cx: (b.left + b.right) / 2, cy: (b.top + b.bottom) / 2 });
      }
    }
    for (i = 0; i < out.length; i++) {
      var p = out[i].el.parentElement, depth = 0;
      while (p && depth < 12) {
        if (p.__ompT) { p.__ompW = 1; }
        p = p.parentElement;
        depth++;
      }
    }
    // A link is the target of its whole card, so the small buttons inside it
    // (a menu dot, a play badge) are not separate stops.
    var keep = [];
    for (i = 0; i < out.length; i++) {
      if (!out[i].el.__ompW && !insideLink(out[i].el)) { keep.push(out[i]); }
    }
    for (i = 0; i < out.length; i++) {
      out[i].el.__ompT = 0;
      out[i].el.__ompW = 0;
    }
    return keep;
  }

  function insideLink(el) {
    var p = el.parentElement, depth = 0;
    while (p && depth < 12) {
      if (p.__ompT && p.tagName === 'A') { return true; }
      p = p.parentElement;
      depth++;
    }
    return false;
  }

  function place(b) {
    ring.style.left = (b.left - GAP) + 'px';
    ring.style.top = (b.top - GAP) + 'px';
    ring.style.width = (b.right - b.left + GAP * 2) + 'px';
    ring.style.height = (b.bottom - b.top + GAP * 2) + 'px';
    var radius = parseFloat(window.getComputedStyle(focused).borderTopLeftRadius) || 0;
    radius = Math.min(radius, (b.right - b.left) / 2, (b.bottom - b.top) / 2);
    ring.style.borderRadius = (radius + GAP) + 'px';
    ring.className = 'omp-on';
  }

  function follow() {
    if (!live(focused)) { hide(); return; }
    focusedBox = visibleBox(focused);
    if (focusedBox) { place(focusedBox); } else { hide(); }
  }

  function hide() {
    if (ring) { ring.className = ''; }
  }

  // Scrolling stopped. If the focus is now off screen, move it to the control
  // nearest the old spot that is on screen, so the next press starts from
  // something visible.
  function settle() {
    if (!live(focused)) { return; }
    var b = visibleBox(focused);
    if (b && onScreen(b)) { return; }
    var list = targets(), pick = null, best = Infinity, i, t, d;
    var cx = focusedBox ? (focusedBox.left + focusedBox.right) / 2 : window.innerWidth / 2;
    var cy = focusedBox ? (focusedBox.top + focusedBox.bottom) / 2 : window.innerHeight / 2;
    for (i = 0; i < list.length; i++) {
      t = list[i];
      if (!onScreen(t.b)) { continue; }
      d = Math.abs(t.cx - cx) + Math.abs(t.cy - cy) * 2;
      if (d < best) { best = d; pick = t; }
    }
    if (pick) { paint(pick.el); }
  }

  // Scroll the least needed to show the control, with a comfortable margin.
  // Inside a scroll container the browser does the minimum itself.
  function reveal(el, b) {
    var inBox = false, n = el.parentElement;
    while (n && n !== document.body && n !== document.documentElement) {
      var st = window.getComputedStyle(n);
      if (/(auto|scroll)/.test(st.overflowY) && n.scrollHeight > n.clientHeight + 4) {
        inBox = true;
        break;
      }
      n = n.parentElement;
    }
    if (inBox) {
      el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
      return;
    }
    var top = 0, H = window.innerHeight;
    if (b.bottom > H - MARGIN) { top = b.bottom - (H - MARGIN); }
    else if (b.top < MARGIN) { top = b.top - MARGIN; }
    if (top) { window.scrollBy({ top: top, behavior: 'smooth' }); }
  }

  function paint(el) {
    focused = el;
    // Focusing a text field opens the keyboard; leave that to the OK press.
    if (!TEXT_ENTRY.test(el.tagName)) {
      try { el.focus({ preventScroll: true }); } catch (e) {}
    }
    var b = visibleBox(el) || el.getBoundingClientRect();
    focusedBox = b;
    reveal(el, b);
    place(b);
  }

  // Top-most, then left-most control on screen. Rows are 30 px tolerant.
  function focusFirst() {
    var list = targets(), top = null, i, t;
    for (i = 0; i < list.length; i++) {
      t = list[i];
      if (!onScreen(t.b)) { continue; }
      if (!top || t.cy < top.cy - 30 || (Math.abs(t.cy - top.cy) <= 30 && t.cx < top.cx)) {
        top = t;
      }
    }
    if (top) { paint(top.el); }
    return !!top;
  }

  // The control that is nearest in the pressed direction. Along the press axis
  // the distance counts once, across it twice: a control straight below beats
  // a closer one that sits off to the side.
  function nearest(dx, dy) {
    var fx = (focusedBox.left + focusedBox.right) / 2;
    var fy = (focusedBox.top + focusedBox.bottom) / 2;
    var list = targets(), found = null, bestScore = Infinity, i, t, ox, oy, main, cross, s;
    for (i = 0; i < list.length; i++) {
      t = list[i];
      if (t.el === focused) { continue; }
      ox = t.cx - fx;
      oy = t.cy - fy;
      if (dx !== 0) {
        if (dx * ox <= 0) { continue; }
        main = Math.abs(ox); cross = Math.abs(oy);
      } else {
        if (dy * oy <= 0) { continue; }
        main = Math.abs(oy); cross = Math.abs(ox);
      }
      s = main + cross * 2;
      if (s < bestScore) { bestScore = s; found = t.el; }
    }
    return found;
  }

  // Nothing left in that direction: scroll the nearest scrollable box, or the
  // page, by most of a screen.
  function scrollPage(dx, dy) {
    var n = focused;
    while (n && n !== document.body && n !== document.documentElement) {
      if (n.scrollHeight > n.clientHeight + 4 &&
          /(auto|scroll)/.test(window.getComputedStyle(n).overflowY)) {
        n.scrollBy({ left: dx * n.clientWidth * 0.5, top: dy * n.clientHeight * 0.75, behavior: 'smooth' });
        return;
      }
      n = n.parentElement;
    }
    window.scrollBy({ left: dx * window.innerWidth * 0.5, top: dy * window.innerHeight * 0.75, behavior: 'smooth' });
  }

  function move(dx, dy) {
    mount();
    if (!live(focused) || !focusedBox) { return focusFirst(); }
    var next = nearest(dx, dy);
    if (next) { paint(next); return true; }
    scrollPage(dx, dy);
    return false;
  }

  // Centre of the visible part of the focused control in CSS pixels, plus 1
  // when it is media. Java sends a real touch there: a trusted gesture, so the
  // site's own click handlers and fullscreen requests behave as for a finger.
  function tap() {
    if (!live(focused) || !focusedBox) { return null; }
    var media = focused.tagName === 'VIDEO' || focused.tagName === 'IFRAME';
    return [Math.round((focusedBox.left + focusedBox.right) / 2),
            Math.round((focusedBox.top + focusedBox.bottom) / 2),
            media ? 1 : 0].join(',');
  }

  // Fallback for players that only enter fullscreen on their own button.
  // Called by Java shortly after the tap, while the gesture still counts.
  function fullscreen() {
    if (!live(focused) || document.fullscreenElement) { return false; }
    if (focused.tagName !== 'VIDEO' && focused.tagName !== 'IFRAME') { return false; }
    try {
      var p = focused.requestFullscreen && focused.requestFullscreen();
      if (p && p.catch) { p.catch(function () {}); }
      return true;
    } catch (e) {
      return false;
    }
  }

  // Short description of the focused control, for the log.
  function state() {
    if (!live(focused) || !focusedBox) { return 'none'; }
    return focused.tagName + '|' + String(focused.className).slice(0, 40) + '|' +
           Math.round(focusedBox.left) + ',' + Math.round(focusedBox.top) + ',' +
           Math.round(focusedBox.right - focusedBox.left) + 'x' +
           Math.round(focusedBox.bottom - focusedBox.top);
  }

  window.__ompNav = {
    version: 4,
    move: function (dx, dy) { var r = move(dx, dy); return (r ? 'moved ' : 'edge ') + state(); },
    focusFirst: function () { mount(); return focusFirst(); },
    tap: tap,
    fullscreen: fullscreen,
    state: state,
    targets: function () { return targets().length; }
  };

  mount();
  focusFirst();
})();
