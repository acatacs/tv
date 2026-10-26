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
  var CARD_MIN = 60;      // a pointer container smaller than this is a glyph, not a card
  var MIN_SIZE = 24;      // smaller visible boxes are decoration, not targets
  var TEXT_ENTRY = /^(INPUT|TEXTAREA|SELECT)$/;
  var focused = null;
  var focusedBox = null;  // visible box of the focused control, CSS px
  var ring = null;
  var frame = 0;
  var settleTimer = 0;
  var cache = null;       // [{el, card}] in document order
  var dirty = true;
  var built = 0;
  var REBUILD_MS = 400;  // busy pages mutate every frame; rebuild at most this often

  // Rebuild the candidate list only when nodes are added or removed.
  var refocusTimer = 0;
  var observer = new MutationObserver(function () {
    dirty = true;
    // the focused control was removed (a popup closed): hide the ring and
    // land on the first visible control again instead of leaving it on air
    if (focused && !live(focused)) {
      focused = null;
      focusedBox = null;
      hide();
      clearTimeout(refocusTimer);
      refocusTimer = setTimeout(focusFirst, 120);
    }
  });

  function mount() {
    if (document.getElementById(STYLE_ID)) { return; }
    var s = document.createElement('style');
    s.id = STYLE_ID;
    s.textContent =
      '#' + RING_ID + '{position:fixed;left:0;top:0;width:0;height:0;margin:0;' +
      'box-sizing:border-box;border:3px solid #22D3EE;pointer-events:none;opacity:0;' +
      'z-index:2147483646;' +
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
    observer.observe(document.documentElement, { childList: true, subtree: true });
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
    if (el.__ompC) {
      if (r.width < CARD_MIN || r.height < CARD_MIN ||
          r.width > window.innerWidth || r.height > window.innerHeight) { return null; }
    } else if (r.width < MIN_SIZE || r.height < MIN_SIZE) { return null; }
    var st = window.getComputedStyle(el);
    if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity) < 0.05) {
      return null;
    }
    var b = { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
    // A fixed element is not cut by the overflow of its ancestors (a popup
    // inside a collapsed wrapper), and a fixed ancestor cuts off what is above it.
    var n = st.position === 'fixed' ? null : el.parentElement;
    while (n && n !== document.body && n !== document.documentElement) {
      var ps = window.getComputedStyle(n);
      if (ps.overflowX === 'hidden' || ps.overflowX === 'clip' ||
          ps.overflowY === 'hidden' || ps.overflowY === 'clip') {
        var c = n.getBoundingClientRect();
        b.left = Math.max(b.left, c.left);
        b.top = Math.max(b.top, c.top);
        b.right = Math.min(b.right, c.right);
        b.bottom = Math.min(b.bottom, c.bottom);
        if (b.right - b.left < 16 || b.bottom - b.top < 16) { return null; }
      }
      if (ps.position === 'fixed') { break; }
      n = n.parentElement;
    }
    return b;
  }

  // Candidates: a control is anything in SELECTOR. A card is the outermost
  // cursor:pointer element (the whole tile of a poster grid). A card must have
  // content: empty pointer boxes are loading placeholders, not targets.
  function elements() {
    if (cache && (!dirty || Date.now() - built < REBUILD_MS)) { return cache; }
    built = Date.now();
    var pointer = new WeakMap(), out = [], seen = new WeakSet(), all, i, el, parent, card;
    all = document.body ? document.body.getElementsByTagName('*') : [];
    for (i = 0; i < all.length; i++) {
      el = all[i];
      pointer.set(el, window.getComputedStyle(el).cursor === 'pointer');
      parent = el.parentElement;
      // a control is never a card: a small pointer button is still a button
      card = pointer.get(el) && !(parent && pointer.get(parent)) && !el.matches(SELECTOR) &&
             (el.textContent.trim().length > 0 || !!el.querySelector('img,svg,picture'));
      if (card || el.matches(SELECTOR)) {
        el.__ompC = card ? 1 : 0;
        out.push({ el: el, card: !!card });
        if (!card) { promote(el, out, seen); }
      }
    }
    cache = out;
    dirty = false;
    return cache;
  }

  // An open dialog owns the remote until it closes: only its controls count.
  // If it has none, fall back to the whole page rather than strand the focus.
  function modalRoot() {
    var m = document.querySelectorAll('[role=dialog],[aria-modal=true],dialog[open]'), i, root = null;
    for (i = 0; i < m.length; i++) {
      if (visibleBox(m[i])) { root = m[i]; }
    }
    return root;
  }

  // A target under another element (a popup, a banner) is not reachable.
  function covered(el, b) {
    var x = (b.left + b.right) / 2, y = (b.top + b.bottom) / 2;
    if (x < 0 || y < 0 || x >= window.innerWidth || y >= window.innerHeight) { return false; }
    var hit = document.elementFromPoint(x, y);
    return !!hit && hit !== el && !el.contains(hit) && !hit.contains(el);
  }

  // A link too small to aim at (a title under a poster) belongs to the tile
  // around it. The ring goes around the tile; OK still activates the link.
  function promote(link, out, seen) {
    var r = link.getBoundingClientRect(), host = link.parentElement, k = 0, hr;
    if (r.width >= MIN_SIZE && r.height >= MIN_SIZE) { return; }
    while (host && k < 4) {
      hr = host.getBoundingClientRect();
      if (hr.width >= CARD_MIN && hr.height >= CARD_MIN &&
          hr.width <= window.innerWidth * 0.9 && hr.height <= window.innerHeight * 0.95) {
        host.__ompH = link;
        host.__ompC = 1;
        if (!seen.has(host)) { seen.add(host); out.push({ el: host, card: true }); }
        return;
      }
      host = host.parentElement;
      k++;
    }
  }

  // Visible targets, off-screen ones included so that moving down past the
  // fold reaches the next row. Inside a card or a link is part of that target.
  // A control that wraps other controls is dropped: the innermost one wins.
  function targets() {
    var list = elements(), out = [], keep = [], i, e, b, t, p, depth, r;
    var top = -window.innerHeight * 2, bottom = window.innerHeight * 3;
    for (i = 0; i < list.length; i++) {
      e = list[i];
      r = e.el.getBoundingClientRect();
      if (r.bottom < top || r.top > bottom) { continue; }   // far outside the working band
      b = visibleBox(e.el);
      if (b) {
        e.el.__ompT = e.card ? 2 : 1;
        e.el.__ompW = 0;
        out.push({ el: e.el, card: e.card, b: b,
                   cx: (b.left + b.right) / 2, cy: (b.top + b.bottom) / 2 });
      }
    }
    for (i = 0; i < out.length; i++) {
      p = out[i].el.parentElement; depth = 0;
      while (p && depth < 12) {
        if (p.__ompT === 1 && p.tagName !== 'A') { p.__ompW = 1; }
        p = p.parentElement; depth++;
      }
    }
    for (i = 0; i < out.length; i++) {
      t = out[i];
      if (t.card || (!t.el.__ompW && !inside(t.el))) { keep.push(t); }
    }
    for (i = 0; i < out.length; i++) { out[i].el.__ompT = 0; out[i].el.__ompW = 0; }
    var modal = modalRoot(), inModal = [];
    if (modal) {
      for (i = 0; i < keep.length; i++) {
        if (modal.contains(keep[i].el)) { inModal.push(keep[i]); }
      }
      if (inModal.length) { return inModal; }
    }
    return keep;
  }

  // True when a card or a link is an ancestor: the element belongs to it.
  function inside(el) {
    var p = el.parentElement, depth = 0;
    while (p && depth < 12) {
      if (p.__ompT === 2 || (p.__ompT === 1 && p.tagName === 'A')) { return true; }
      p = p.parentElement; depth++;
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
      if (!onScreen(t.b) || covered(t.el, t.b)) { continue; }
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
  function nearest(dx, dy, skip) {
    var fx = (focusedBox.left + focusedBox.right) / 2;
    var fy = (focusedBox.top + focusedBox.bottom) / 2;
    var list = targets(), bestIn = null, scoreIn = Infinity, bestOut = null, scoreOut = Infinity;
    var i, t, ox, oy, main, cross, s, inColumn;
    for (i = 0; i < list.length; i++) {
      t = list[i];
      if (t.el === focused || skip.indexOf(t.el) >= 0) { continue; }
      ox = t.cx - fx;
      oy = t.cy - fy;
      if (dx !== 0) {
        if (dx * ox <= 0) { continue; }
        main = Math.abs(ox); cross = Math.abs(oy);
        inColumn = t.b.bottom > focusedBox.top && t.b.top < focusedBox.bottom;   // same row
      } else {
        if (dy * oy <= 0) { continue; }
        main = Math.abs(oy); cross = Math.abs(ox);
        inColumn = t.b.right > focusedBox.left && t.b.left < focusedBox.right;   // same column
      }
      s = main + cross * 2;
      if (inColumn) {
        if (s < scoreIn) { scoreIn = s; bestIn = t.el; }
      } else if (s < scoreOut) {
        scoreOut = s; bestOut = t.el;
      }
    }
    // a control that lines up with the focus always wins; the sidebar or a
    // header only takes over when nothing lines up in that direction
    return bestIn || bestOut;
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
    var skip = [], next, k;
    for (k = 0; k < 6; k++) {
      next = nearest(dx, dy, skip);
      if (!next) { break; }
      if (!covered(next, visibleBox(next) || next.getBoundingClientRect())) { paint(next); return true; }
      skip.push(next);
    }
    scrollPage(dx, dy);
    return false;
  }

  // Centre of the visible part of the focused control in CSS pixels, plus 1
  // when it is media. Java sends a real touch there: a trusted gesture, so the
  // site's own click handlers and fullscreen requests behave as for a finger.
  function tap() {
    if (!live(focused) || !focusedBox) { return null; }
    var media = focused.tagName === 'VIDEO' || focused.tagName === 'IFRAME';
    var hit = focused.__ompH || focused;   // a tile is activated through its link
    var hr = hit.getBoundingClientRect();
    return [Math.round((hr.left + hr.right) / 2), Math.round((hr.top + hr.bottom) / 2),
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
