// Пульк в браузере: the star of «Пульс» on the page and «Говорить» — for a phone where Пульк cannot be the system
// keyboard (iPhone). Geometry, recogniser and word matching are the Android app's (Pulse.kt, Words.kt), in base
// units around the thumb's corner; phrases and lessons come from the app's sources through data.js.
(function () {
  'use strict';
  const D = window.PULK, DEG = Math.PI / 180;
  const P = window.PulkCore, own = P.own, R = P.R, PAD = P.PAD, OUTER = P.OUTER, P_MIN = P.P_MIN, P_TURN = P.P_TURN, HOLD_MS = P.HOLD_MS, PH = P.PH, KINDS = P.KINDS, SPOT = P.SPOT, ALT = P.ALT, SPACE = P.SPACE, spot = P.spot, petalOf = P.petalOf, slot = P.slot, tap = P.tap, resolve = P.resolve, KEYS = P.KEYS, hit = P.hit, fold = P.fold, shape = P.shape, Words = P.Words, JAB_OUT = P.JAB_OUT, JAB_HOME = P.JAB_HOME, JAB_SHORT = P.JAB_SHORT, Jabs = P.Jabs, jab = P.jab, alpha = P.alpha, OneEuro = P.OneEuro, lean = P.lean;
  const $ = function (id) { return document.getElementById(id); };
  // localStorage, read once per key: screens ask for the same lists on every keystroke
  const memo = Object.create(null);
  const store = {
    get: function (k, d) {
      if (k in memo) return memo[k];
      let v = d; try { const s = localStorage.getItem('pulk.' + k); if (s != null) v = JSON.parse(s); } catch (e) {}
      return (memo[k] = v);
    },
    // false when the browser's storage is full (photos fill it first): the caller says so instead of losing it silently
    set: function (k, v) { memo[k] = v; try { localStorage.setItem('pulk.' + k, JSON.stringify(v)); return true; } catch (e) { return false; } },
  };

  let lang = 'ru', symbols = false, shiftOnce = false;
  function table() { return symbols ? D.SYM : (lang === 'ru' ? D.RU : D.EN); }

  // ---- drawing the keyboard ----
  const svg = $('kbd'), NS = 'http://www.w3.org/2000/svg';
  let left = store.get('hand', 'right') === 'left';
  let W = 0, H = 0, VH = 0, K = 1, CX = 0, CY = 0, C = {};
  function mx(x) { return left ? W - x : x; }
  function scr(bx, by) { return [mx(CX + bx * K), CY - by * K]; }
  function toBase(px, py) { return [(mx(px) - CX) / K, (CY - py) / K]; }
  function xy(p) { return p[0].toFixed(1) + ',' + p[1].toFixed(1); }
  function el(tag, attrs, parent) { const e = document.createElementNS(NS, tag); for (const a in attrs) e.setAttribute(a, attrs[a]); (parent || svg).appendChild(e); return e; }
  function arcPts(r0, r1, a0, a1) {
    const pts = [], steps = Math.max(2, Math.round((a1 - a0) / 3));
    for (let i = 0; i <= steps; i++) { const a = (a0 + (a1 - a0) * i / steps) * DEG; pts.push(scr(r1 * Math.cos(a), r1 * Math.sin(a))); }
    for (let j = steps; j >= 0; j--) { const b = (a0 + (a1 - a0) * j / steps) * DEG; pts.push(scr(r0 * Math.cos(b), r0 * Math.sin(b))); }
    return pts.map(xy).join(' ');
  }
  // colours are read once per layout (and when the phone switches light/dark), not on every frame
  function readColours() {
    const cs = getComputedStyle(document.documentElement);
    ['ink', 'muted', 'accent', 'on-accent', 'petal', 'press', 'zone', 'line', 'glass'].forEach(function (n) { C[n] = cs.getPropertyValue('--' + n).trim(); });
  }
  let petals = [], lifts = [], labels = [], keyEls = {}, drop0, dropAt = [0, 0], bubble, bubbleText, trail, lit = -2, labelsKey = '', relayout = false;
  const guide = { path: null, dot: null, pts: null, len: 0, raf: 0, t0: 0, target: -1 };
  function layout(force) {
    if (touch) { relayout = true; return; }   // never move the star under a finger mid-stroke
    // the page's own size, not the visible part: a zoomed-in Safari reports a smaller innerWidth, and the star
    // must not be redrawn for it
    const de = document.documentElement, nw = de.clientWidth || window.innerWidth, nh = de.clientHeight || window.innerHeight;
    if (!force && W === nw && VH === nh) return;
    W = nw; VH = nh; readColours(); document.body.classList.toggle('left-hand', left);
    // the big star takes more of the screen: more room for a thumb that does not land precisely
    const big = store.get('bigStar', false);
    K = Math.min(Math.min(W, big ? 520 : 440) / (OUTER * (big ? 0.98 : 1.047)), (W - 2) / OUTER, VH * (big ? 0.5 : 0.42) / (OUTER + 4));
    K = Math.max(K, 0.3);   // a page opened hidden reports no height: never draw the star inside out
    H = Math.ceil(OUTER * K + 4); CX = W + 4; CY = H + 4;
    svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H); svg.setAttribute('height', H);
    svg.innerHTML = ''; petals = []; lifts = []; labels = []; keyEls = {}; lit = -2; labelsKey = '';
    const fs = Math.max(K, 0.85);
    KEYS.forEach(function (k) {
      const poly = el('polygon', { points: arcPts(k.r0 + 3, k.r1 - 3, k.a0 + 0.8, k.a1 - 0.8), fill: C.glass, stroke: C.line, 'stroke-width': 1, 'stroke-linejoin': 'round' });
      const m = (k.r0 + k.r1) / 2, am = (k.a0 + k.a1) / 2 * DEG, p = scr(m * Math.cos(am), m * Math.sin(am));
      keyEls[k.fn] = { poly: poly, text: el('text', { x: p[0], y: p[1], 'text-anchor': 'middle', 'dominant-baseline': 'central', fill: C.ink, 'font-size': 16 * fs, 'font-weight': 600 }) };
    });
    for (let p = 0; p < 8; p++) {
      const g = 1.6 / R / DEG, gi = 1.6 / 18 / DEG, pts = [];
      for (let i = 0; i <= 8; i++) { const a = (p * 45 - 22.5 + g + (45 - 2 * g) * i / 8) * DEG; pts.push(scr(PH[0] + (R - 1) * Math.cos(a), PH[1] + (R - 1) * Math.sin(a))); }
      for (let j = 8; j >= 0; j--) { const b = (p * 45 - 22.5 + gi + (45 - 2 * gi) * j / 8) * DEG; pts.push(scr(PH[0] + 18 * Math.cos(b), PH[1] + 18 * Math.sin(b))); }
      petals.push(el('polygon', { class: 'petal', points: pts.map(xy).join(' '), fill: C.petal, stroke: C.line, 'stroke-width': 1, 'stroke-linejoin': 'round' }));
      // the pressed petal lifts a little outwards, towards its letters (like the app)
      const o = scr(PH[0], PH[1]), q = scr(PH[0] + 6 * Math.cos(p * 45 * DEG), PH[1] + 6 * Math.sin(p * 45 * DEG));
      lifts.push('translate(' + (q[0] - o[0]).toFixed(1) + 'px,' + (q[1] - o[1]).toFixed(1) + 'px)');
    }
    for (let pp = 0; pp < 8; pp++) for (let kk = 0; kk < 4; kk++) {
      const q = scr.apply(null, spot(pp, KINDS[kk]));
      labels.push({ p: pp, i: kk, el: el('text', { x: q[0], y: q[1], 'text-anchor': 'middle', 'dominant-baseline': 'central', fill: kk === 0 ? C.ink : C.muted,
        'font-size': (kk === 0 ? 24 : kk === 3 ? 12 : 15) * fs, 'font-weight': kk === 0 ? 700 : 400 }) });
    }
    const c = scr(PH[0], PH[1]);
    drop0 = el('circle', { class: 'drop', cx: c[0], cy: c[1], r: 13 * K, fill: C.accent }); dropAt = [0, 0];
    trail = el('polyline', { points: '', fill: 'none', stroke: C.accent, 'stroke-width': 5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', opacity: 0.6 });
    guide.path = el('polyline', { points: '', fill: 'none', stroke: C.accent, 'stroke-width': 3, 'stroke-dasharray': '6 7', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', opacity: 0.7, visibility: 'hidden' });
    guide.dot = el('circle', { cx: 0, cy: 0, r: 12 * fs, fill: C.accent, stroke: C['on-accent'], 'stroke-width': 3, visibility: 'hidden' });
    bubble = el('circle', { class: 'drop', cx: c[0], cy: c[1], r: 26, fill: C.accent, visibility: 'hidden' });
    bubbleText = el('text', { class: 'drop', x: c[0], y: c[1], 'text-anchor': 'middle', 'dominant-baseline': 'central', fill: C['on-accent'], 'font-size': 30, 'font-weight': 800, visibility: 'hidden' });
    paintLabels();
    showGuide(guided ? guided.word[guided.i] : null);
  }
  function caps() { return shiftOnce || !text || /[.!?…]\s$/.test(text) || /\n$/.test(text); }
  function paintLabels() {
    const up = caps() && !symbols, key = up + lang + symbols;
    if (key === labelsKey) return;
    labelsKey = key;
    const t = table();
    labels.forEach(function (l) { const s = t[l.p][l.i] || ''; l.el.textContent = up ? s.toUpperCase() : s; });
    const lab = { space: lang === 'ru' ? 'пробел' : 'space', enter: '⏎', bksp: '⌫', shift: '⇧', lang: lang.toUpperCase(), sym: symbols ? 'абв' : '?123', say: '🔊' };
    for (const f in keyEls) keyEls[f].text.textContent = lab[f];
  }
  // the lit petal changes a few times per stroke, not on every move: only then are the petals repainted
  function light(p, force) {
    if (p === lit && !force) return;
    lit = p;
    petals.forEach(function (e, i) {
      e.setAttribute('fill', i === p ? C.press : i === guide.target ? C.zone : C.petal);
      e.style.transform = i === p ? lifts[i] : '';
      e.setAttribute('stroke', i === guide.target ? C.accent : C.line); e.setAttribute('stroke-width', i === guide.target ? 2.5 : 1);
    });
  }

  // the drop (and the letter bubble over it) leans after the finger, in screen pixels; back when the finger lifts
  function leanTo(q) {
    const c = scr(PH[0], PH[1]), d = q ? lean(q[0] - c[0], q[1] - c[1]) : [0, 0];
    if (!drop0 || (Math.abs(d[0] - dropAt[0]) < 0.3 && Math.abs(d[1] - dropAt[1]) < 0.3)) return;
    dropAt = d; const tf = d[0] || d[1] ? 'translate(' + d[0].toFixed(1) + 'px,' + d[1].toFixed(1) + 'px)' : '';
    drop0.style.transform = bubble.style.transform = bubbleText.style.transform = tf;
  }

  // ---- the guide: a dot that runs the needed stroke on the star, again and again, until the finger repeats it ----
  function strokeFor(ch) { const t = table(); for (let p = 0; p < 8; p++) for (let i = 0; i < 4; i++) if (t[p][i] === ch) return { p: p, i: i, kind: KINDS[i] }; return null; }
  function guidePts(p, kind) {
    const a = p * 45 * DEG, dx = Math.cos(a), dy = Math.sin(a), out = [[0, 0]];
    if (kind === 'c') out.push([dx * 80, dy * 80]);
    else if (kind === 'b') out.push([dx * 62, dy * 62], [dx * 14, dy * 14]);
    else { const b = a + (kind === 'l' ? 1 : -1) * Math.PI / 2; out.push([dx * 55, dy * 55], [dx * 55 + Math.cos(b) * 36, dy * 55 + Math.sin(b) * 36]); }
    return out.map(function (q) { return scr(PH[0] + q[0], PH[1] + q[1]); });
  }
  function showGuide(ch) {
    cancelAnimationFrame(guide.raf); guide.raf = 0;
    labels.forEach(function (l) { l.el.setAttribute('fill', l.i === 0 ? C.ink : C.muted); });
    const f = ch && strokeFor(ch);
    if (!f || !guide.path) {
      guide.target = -1;
      if (guide.path) { guide.path.setAttribute('visibility', 'hidden'); guide.dot.setAttribute('visibility', 'hidden'); }
      light(-1, true); return;
    }
    guide.target = f.p; guide.pts = guidePts(f.p, f.kind); guide.len = 0;
    for (let i = 1; i < guide.pts.length; i++) guide.len += Math.hypot(guide.pts[i][0] - guide.pts[i - 1][0], guide.pts[i][1] - guide.pts[i - 1][1]);
    guide.path.setAttribute('points', guide.pts.map(xy).join(' '));
    guide.path.setAttribute('visibility', 'visible');
    labels.forEach(function (l) { if (l.p === f.p && l.i === f.i) l.el.setAttribute('fill', C.accent); });
    light(-1, true); guide.t0 = performance.now();
    (function tick(now) {
      const run = 1300, rest = 700, u = ((now - guide.t0) % (run + rest)) / run;
      if (touch || u > 1) guide.dot.setAttribute('visibility', 'hidden');
      else {
        let e = u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2, d = e * guide.len;
        for (let i = 1; i < guide.pts.length; i++) {
          const a = guide.pts[i - 1], b = guide.pts[i], sl = Math.hypot(b[0] - a[0], b[1] - a[1]);
          if (d <= sl || i === guide.pts.length - 1) { const k = sl ? Math.min(d / sl, 1) : 0; guide.dot.setAttribute('cx', a[0] + (b[0] - a[0]) * k); guide.dot.setAttribute('cy', a[1] + (b[1] - a[1]) * k); break; }
          d -= sl;
        }
        guide.dot.setAttribute('visibility', 'visible');
      }
      guide.raf = requestAnimationFrame(tick);
    })(guide.t0);
  }

  // ---- words: the dictionary, this person's own words and pairs (the matching itself is in core.js) ----
  // stop list (stopwords.js): rude and obscene words are never offered — children type here too
  const dicts = {}, next = Object.create(null), bad = P.Stop(window.PULK_STOP);
  const soft = (window.PULK_STOP && window.PULK_STOP.soft) || {};
  function loadDict(l) {
    if (dicts[l]) return;
    dicts[l] = 'loading';
    fetch('../../dict_' + l + '.txt').then(function (r) { return r.ok ? r.text() : ''; })
      .then(function (t) { dicts[l] = new Words(t.split(/\r?\n/), l === 'ru' ? D.RU : D.EN, bad); paintChips(); })
      .catch(function () { dicts[l] = null; });
  }
  loadDict('ru');
  fetch('next_ru.txt').then(function (r) { return r.ok ? r.text() : ''; }).then(function (t) {
    t.split(/\r?\n/).forEach(function (l) { const i = l.indexOf('\t'); if (i > 0) next[l.slice(0, i)] = l.slice(i + 1).split(' '); });
  }).catch(function () {});
  function words() { const w = dicts[lang]; return w && w !== 'loading' && !symbols ? w : null; }
  // what this person says: their words are known to suggestions (and never "corrected"), their word pairs come first
  function userWords() { return store.get('user', {}); }
  function learnText(t) {
    const ws = fold(t.toLowerCase()).split(/[^\p{L}'-]+/u).filter(Boolean); if (!ws.length) return;
    let u = userWords(), pr = store.get('pairs', {}), prev = '^', fresh = 0;
    ws.forEach(function (w) {
      if (w.length >= 2) { if (!own(u, w)) fresh++; u[w] = own(u, w) + 1; }
      const m = own(pr, prev) || (pr[prev] = {}); m[w] = own(m, w) + 1; prev = w;
    });
    const keys = Object.keys(u);
    if (keys.length > 3000) keys.sort(function (a, b) { return u[a] - u[b]; }).slice(0, keys.length - 3000).forEach(function (k) { delete u[k]; });
    store.set('user', u); store.set('pairs', pr); return fresh;
  }
  function nextWords(prev) {
    let p = fold(prev), out = [], mine = own(store.get('pairs', {}), p) || {};
    Object.keys(mine).filter(function (w) { return mine[w] >= 2; }).sort(function (a, b) { return mine[b] - mine[a]; }).forEach(function (w) { if (out.indexOf(w) < 0) out.push(w); });
    (next[p] || []).forEach(function (w) { if (out.indexOf(w) < 0) out.push(w); });
    out = out.filter(function (w) { return !bad(w); });
    if (!out.length) out = (next['^'] || []).filter(function (w) { return !bad(w); });
    return out.slice(0, 3);
  }
  function currentWord() { const m = /[\p{L}']+$/u.exec(text); return m ? m[0] : ''; }
  function wordChips() {
    const w = currentWord(), W = words();
    if (w.length) {
      if (!W) return [];
      const f = fold(w.toLowerCase());
      return W.suggest(w, 4, userWords()).filter(function (s) { return fold(s.toLowerCase()) !== f; }).slice(0, 3);
    }
    if (!text || /\s$/.test(text)) {
      if (lang !== 'ru' || symbols) return [];
      const prev = /([\p{L}']+)\s*$/u.exec(text), key = !text.trim() || /[.!?…]\s*$/.test(text) || !prev ? '^' : prev[1].toLowerCase();
      return nextWords(key).map(function (s) { return caps() ? s[0].toUpperCase() + s.slice(1) : s; });
    }
    return [];
  }

  // ---- typing ----
  let text = '', onEnter = null, lastFix = null;
  const FIX_GLOW_MS = 1500;
  function render() {
    // the star and the word chips only where they type something: not over the pond or the adult's forms
    document.body.classList.toggle('nostar', !$('text') && !game && !probe);
    const t = $('text'); if (!t) { paintLabels(); return; }   // no text on this screen, but the star's keys still change
    if (sysKbd) { sysField(t); paintLabels(); chipsSoon(); return; }
    const fx = lastFix && text === lastFix.at && performance.now() - lastFix.t < FIX_GLOW_MS ? text.length - 1 - lastFix.len : -1;
    if (fx >= 0) {   // the word Пульк just corrected glows for a moment, so the person (and whoever watches) sees it
      t.textContent = text.slice(0, fx); const g = document.createElement('span'); g.className = 'fixed'; g.textContent = text.slice(fx, text.length - 1);
      t.appendChild(g); t.appendChild(document.createTextNode(' '));
    } else t.textContent = text;
    const c = document.createElement('span'); c.className = 'caret'; t.appendChild(c);
    if (guided) { const rest = guided.word.slice(guided.i), r = document.createElement('span'); r.className = 'rest'; r.textContent = caps() ? rest.charAt(0).toUpperCase() + rest.slice(1) : rest; t.appendChild(r); }
    paintLabels(); chipsSoon();
  }
  // «⌨ Обычная»: the phone's own keyboard instead of the star, into the same phrase (for the adult, or a day the star is
  // not right); the star comes back with «★ Звезда» or on any other screen
  let sysKbd = false;
  function sysField(t) {
    let f = t.querySelector('input');
    if (!f) {
      t.textContent = ''; f = document.createElement('input'); f.type = 'text'; f.className = 'sys'; f.setAttribute('autocomplete', 'off');
      f.placeholder = t.getAttribute('data-hint') || '';
      f.oninput = function () { text = f.value; lastFix = null; chipsSoon(); };
      f.onkeydown = function (e) { if (e.key === 'Enter') { e.preventDefault(); if (onEnter) onEnter(); } };
      t.appendChild(f);
    }
    if (f.value !== text) f.value = text;
    return f;
  }
  function setSysKbd(on) {
    sysKbd = on; document.body.classList.toggle('syskbd', on);
    const t = $('text'); if (t && !on) t.textContent = '';
    render();
    if (on && t) sysField(t).focus();   // in the same touch: only then does iPhone open its keyboard
  }
  let chipsFrame = 0;
  function chipsSoon() { if (!chipsFrame) chipsFrame = requestAnimationFrame(function () { chipsFrame = 0; paintChips(); }); }
  function typeLetter(v) { tick(); lastFix = null; text += caps() && !symbols ? v.toUpperCase() : v; shiftOnce = false; render(); }
  // a finished word that is not a word, but is one stroke-slip away from a word, is corrected; ⌫ right after undoes it
  // which letters are hard for this hand: the game brings them more often
  function missed(ch) { ch = ch.toLowerCase(); const st = store.get('letters', {}), v = own(st, ch) || [0, 0]; v[1]++; st[ch] = v; store.set('letters', st); }
  function fixLastWord() {
    const w = currentWord(), W = words(); if (guided || w.length < 2) return null;
    const f = fold(w.toLowerCase()), kind = own(soft, f);
    let c;
    if (kind && own(userWords(), f) < 2) c = shape(kind, w);   // a rude word: its kinder twin (stopwords.js «soft»)
    else {
      if (!W) return null;
      c = W.correction(w, userWords());
      if (!c || c === w) return null;
      for (let i = 0; i < c.length; i++) if (fold(c[i].toLowerCase()) !== fold(w[i].toLowerCase())) missed(c[i]);
    }
    const before = text; text = text.slice(0, text.length - w.length) + c; return { before: before, word: w, len: c.length };
  }
  function space() {
    const fix = fixLastWord();
    text += ' '; lastFix = fix && { undo: fix.before + ' ', at: text, word: fix.word, len: fix.len, t: performance.now() };
    render(); sayWord();
    if (fix) setTimeout(render, FIX_GLOW_MS + 20);
  }
  function backspace() {
    if (lastFix && text === lastFix.at) { text = lastFix.undo; const u = userWords(), f = fold(lastFix.word.toLowerCase()); u[f] = Math.max(2, own(u, f) + 1); store.set('user', u); lastFix = null; render(); return; }
    lastFix = null; text = text.slice(0, -1); render();
  }
  function takeWord(s) { lastFix = null; const w = currentWord(); text = text.slice(0, text.length - w.length) + s + ' '; render(); sayWord(); }
  // a guided word: only the letter the guide shows is typed; a different one is not an error — the dot shows again,
  // and after three tries Пульк types it itself, so nobody gets stuck on the first steps
  let guided = null;
  function guidedStroke(t) {
    if (t.key) { guide.t0 = performance.now(); return; }   // a service key is not a try: just show the way again
    const want = guided.word[guided.i], s = t.slot || resolve(table(), t.pts);
    if (s.petal >= 0 && s.v === want) { guided.note(''); return nextGuided(want); }
    missed(want);
    if (++guided.miss >= 3) { guided.note('Я помог с буквой «' + want + '». Дальше — ты.'); return nextGuided(want); }
    guided.note(s.petal >= 0 ? 'Это «' + s.v + '». Нужна «' + want + '» — веди пальцем, как бежит точка.' : 'Веди пальцем по звезде, как бежит точка.');
    guide.t0 = performance.now();
  }
  function nextGuided(ch) {
    typeLetter(ch); guided.i++; guided.miss = 0;
    if (guided.i < guided.word.length) { showGuide(guided.word[guided.i]); render(); return; }
    const g = guided; guided = null; showGuide(null); render(); g.done();
  }

  // ---- «Толчки»: the cutting into jabs is in core.js ----
  function jabsOn() { return (game ? !!game.words : store.get('mode', 'pulse') === 'jabs') && !guided && !probe; }   // the hand check needs the whole stroke
  // a jab's letter goes to the text, or to the word bubble while that game is on
  function jabbed(v) { if (game && game.words) game.add(v); else typeLetter(v); }

  // ---- a steadier hand (as Pulse.Steady): tremor smoothed with the 1€ filter (Casiez, Roussel, Vogel, CHI 2012) —
  // strong smoothing while the finger moves slowly or rests, almost none in a quick stroke; and strokes measured
  // against this hand's own length: «long» means short jitter must not count, «short» means short strokes are enough ----
  function Steady(ox, oy) {
    const sm = store.get('smooth', 0), mc = sm === 2 ? 0.8 : 3;   // Hz: strong or light smoothing
    this.ox = ox; this.oy = oy; this.tol = store.get('tol', 1); this.on = sm > 0;
    this.fx = new OneEuro(mc, 0.02); this.fy = new OneEuro(mc, 0.02);
  }
  Steady.prototype.at = function (x, y, t) {
    const sx = this.on ? this.fx.f(x, t) : x, sy = this.on ? this.fy.f(y, t) : y;
    return [this.ox + (sx - this.ox) / this.tol, this.oy + (sy - this.oy) / this.tol];
  };
  // accidental touches: a brush shorter than this is not a letter, and a touch right after another is a bounce
  function guardMin() { return [0, 80, 160][store.get('guard', 0)] || 0; }
  function guardGap() { return [0, 120, 300][store.get('guard', 0)] || 0; }
  let lastEnd = 0;

  // ---- touch on the keyboard ----
  let touch = null;
  // the finger in the star's own units, whatever the page's zoom or the star's padding: through the drawing's matrix
  function point(e) {
    const m = svg.getScreenCTM && svg.getScreenCTM();
    if (m) { let q = svg.createSVGPoint(); q.x = e.clientX; q.y = e.clientY; q = q.matrixTransform(m.inverse()); return toBase(q.x, q.y); }
    const r = svg.getBoundingClientRect(); return toBase(e.clientX - r.left, e.clientY - r.top);
  }
  svg.addEventListener('pointerdown', function (e) {
    e.preventDefault(); try { svg.setPointerCapture(e.pointerId); } catch (x) {}
    if (touch) return;   // one thumb: a second finger is ignored
    const b = point(e), now = Date.now();
    if (!probe && now - lastEnd < guardGap()) { touch = { id: e.pointerId, ignored: true }; return; }   // a bounce
    touch = { id: e.pointerId, key: hit(b[0], b[1]), pts: [b[0], b[1]], trail: [xy(scr(b[0], b[1]))], still: now, start: now, ax: b[0], ay: b[1], shown: '' };
    if (!touch.key && !probe) touch.steady = new Steady(b[0], b[1]);
    if (!touch.key && jabsOn()) { touch.jabs = new Jabs(b[0], b[1]); if (game) game.start(); }
    if (touch.key && touch.key.fn === 'bksp' && !guided) {
      // with the guard on, ⌫ waits for the finger to be sure (on release); held, it repeats either way
      if (!guardMin()) backspace();
      touch.rep = setTimeout(function rep() { backspace(); if (touch) { touch.repeated = true; touch.rep = setTimeout(rep, 70); } }, 450);
    }
    keepAwake();
  });
  svg.addEventListener('pointermove', function (e) {
    if (!touch || touch.id !== e.pointerId || touch.key || touch.ignored) return;
    let b = point(e);
    if (touch.steady) b = touch.steady.at(b[0], b[1], e.timeStamp || Date.now());
    if (touch.jabs) {
      const done = touch.jabs.add(b[0], b[1]);
      if (done) { jabbed(jab(table(), done).v); light(-1); leanTo(null); trail.setAttribute('points', ''); bubble.setAttribute('visibility', 'hidden'); bubbleText.setAttribute('visibility', 'hidden'); touch.shown = ''; return; }
      const cur = touch.jabs.cur; if (!cur.length) return;
      const js = jab(table(), cur), pts = [];
      for (let i = 0; i < cur.length; i += 2) pts.push(xy(scr(cur[i], cur[i + 1])));
      light(js.petal); trail.setAttribute('points', pts.join(' ')); leanTo(scr(cur[cur.length - 2], cur[cur.length - 1]));
      const jshown = caps() && !symbols ? js.v.toUpperCase() : js.v;
      if (jshown !== touch.shown) { touch.shown = jshown; bubbleText.textContent = jshown; bubble.setAttribute('visibility', 'visible'); bubbleText.setAttribute('visibility', 'visible'); }
      return;
    }
    touch.pts.push(b[0], b[1]); touch.trail.push(xy(scr(b[0], b[1])));
    if (Math.hypot(b[0] - touch.ax, b[1] - touch.ay) > 4) { touch.ax = b[0]; touch.ay = b[1]; touch.still = Date.now(); }
    const s = resolve(table(), touch.pts);
    light(s.petal); leanTo(scr(b[0], b[1]));
    trail.setAttribute('points', touch.trail.join(' '));
    let shown = s.petal < 0 ? '·' : (Date.now() - touch.still > HOLD_MS && ALT[s.v] ? ALT[s.v] : s.v);
    if (caps() && !symbols) shown = shown.toUpperCase();
    if (shown !== touch.shown) { touch.shown = shown; bubbleText.textContent = shown; }
    if (touch.trail.length === 2) { bubble.setAttribute('visibility', 'visible'); bubbleText.setAttribute('visibility', 'visible'); }
  });
  function endTouch(e, cancelled) {
    if (!touch || touch.id !== e.pointerId) return;
    const t = touch; touch = null;
    if (t.ignored) return;
    const tooShort = !probe && Date.now() - t.start < guardMin(); lastEnd = Date.now();   // the hand check measures every touch
    clearTimeout(t.rep); light(-1); leanTo(null); trail.setAttribute('points', ''); bubble.setAttribute('visibility', 'hidden'); bubbleText.setAttribute('visibility', 'hidden');
    if (relayout) { relayout = false; layout(true); }
    if (cancelled || tooShort) return;
    if (t.key && t.key.fn === 'bksp') { if (guardMin() && !t.repeated && !guided) backspace(); return; }
    if (probe && !t.key) { probe(t); return; }
    if (guided) { guidedStroke(t); return; }
    if (game && !t.key) { gameStroke(t); return; }
    if (t.jabs) { const last = t.jabs.finish(); if (last) typeLetter(jab(table(), last).v); space(); return; }
    if (t.key) {
      switch (t.key.fn) {
        case 'space': space(); break; case 'enter': if (onEnter) onEnter(); break;
        case 'shift': shiftOnce = !shiftOnce; lastFix = null; paintLabels(); break;
        case 'lang': lang = lang === 'ru' ? 'en' : 'ru'; symbols = false; lastFix = null; loadDict(lang); render(); break;
        case 'sym': symbols = !symbols; lastFix = null; render(); break;
        case 'say': say(text.trim() || lastSaid); break;
      }
      return;
    }
    const s = resolve(table(), t.pts);
    if (s.petal < 0) { space(); return; }
    const n = t.pts.length, held = Date.now() - t.still > HOLD_MS && Math.hypot(t.pts[n - 2] - t.pts[0], t.pts[n - 1] - t.pts[1]) >= P_MIN;
    typeLetter(held && ALT[s.v] ? ALT[s.v] : s.v);
  }
  svg.addEventListener('pointerup', function (e) { endTouch(e, false); });
  svg.addEventListener('pointercancel', function (e) { endTouch(e, true); });
  // a touch the phone took away (a system swipe, a call, the app switcher) never ends by itself: without this the
  // star would wait for it forever and ignore every later touch
  svg.addEventListener('lostpointercapture', function (e) { endTouch(e, true); });
  document.addEventListener('visibilitychange', function () { if (document.visibilityState !== 'visible' && touch) endTouch({ pointerId: touch.id }, true); });
  // Safari on iPhone zooms with two fingers whatever the page asks: a zoomed page cuts the screens and the star
  document.addEventListener('gesturestart', function (e) { e.preventDefault(); });
  document.addEventListener('touchmove', function (e) { if (e.touches && e.touches.length > 1) e.preventDefault(); }, { passive: false });

  // ---- a joystick, a gamepad or a keyboard as the star — nothing to set up. A stick works as «Толчки»: a push out
  // and back is a letter, a short push the letter at the centre, a push that turns the letter with a turn. Keys:
  // the number pad (or arrows) gives directions; held together with 5 — the centre letter, with a neighbour — the turn ----
  function commitSlot(s) {
    keepAwake();
    if (guided) return guidedStroke({ slot: s });
    if (game) return gameStroke({ slot: s });
    if (s.petal < 0) space(); else typeLetter(s.v);
  }
  function showCand(s) {
    light(s ? s.petal : -1);
    if (!s) { bubble.setAttribute('visibility', 'hidden'); bubbleText.setAttribute('visibility', 'hidden'); return; }
    bubbleText.textContent = caps() && !symbols ? s.v.toUpperCase() : s.v;
    bubble.setAttribute('visibility', 'visible'); bubbleText.setAttribute('visibility', 'visible');
  }
  function keyAction(fn) {
    if (guided || document.body.classList.contains('welcome')) return;
    keepAwake();
    if (fn === 'space') space(); else if (fn === 'bksp') backspace(); else if (fn === 'enter') { if (onEnter) onEnter(); }
    else if (fn === 'shift') { shiftOnce = !shiftOnce; lastFix = null; paintLabels(); }
  }
  const KEYDIR = { Numpad6: 0, Numpad9: 1, Numpad8: 2, Numpad7: 3, Numpad4: 4, Numpad1: 5, Numpad2: 6, Numpad3: 7, ArrowRight: 0, ArrowUp: 2, ArrowLeft: 4, ArrowDown: 6 };
  let pend = null;   // the direction key held down: its letter goes when the key is let go
  function flushPend() { if (!pend) return; const s = slot(table(), pend.p, pend.kind); pend = null; showCand(null); commitSlot(s); }
  document.addEventListener('keydown', function (e) {
    const tg = e.target;
    if (tg && (tg.tagName === 'INPUT' || tg.tagName === 'TEXTAREA') || e.metaKey || e.ctrlKey || e.altKey) return;
    if (document.body.classList.contains('welcome') || $('big').style.display === 'flex') return;
    const d = KEYDIR[e.code];
    if (d != null) {
      e.preventDefault(); if (e.repeat) return;
      const p = left ? (4 - d + 8) % 8 : d;   // the left hand's star is mirrored
      if (pend && pend.kind === 'c') { const diff = (p - pend.p + 8) % 8; if (diff >= 1 && diff <= 3) { pend.kind = 'l'; } else if (diff >= 5) { pend.kind = 'r'; } if (pend.kind !== 'c') { showCand(slot(table(), pend.p, pend.kind)); return; } }
      flushPend();
      pend = { p: p, kind: 'c', code: e.code }; showCand(slot(table(), p, 'c')); return;
    }
    if (e.code === 'Numpad5' || e.code === 'Numpad0') {
      e.preventDefault(); if (e.repeat) return;
      if (pend && e.code === 'Numpad5') { pend.kind = 'b'; showCand(slot(table(), pend.p, 'b')); } else commitSlot(SPACE);
      return;
    }
    if (e.key === ' ' || e.key === 'Enter' || e.key === 'Backspace') {
      e.preventDefault(); flushPend();
      keyAction(e.key === ' ' ? 'space' : e.key === 'Enter' ? 'enter' : 'bksp'); return;
    }
    // a big-key keyboard or any keyboard: letters go straight in
    if (e.key && e.key.length === 1 && /[а-яёa-z.,!?'-]/i.test(e.key)) {
      e.preventDefault(); flushPend();
      const ch = e.key.toLowerCase(), at = strokeFor(ch);
      commitSlot({ petal: at ? at.p : 0, kind: at ? at.kind : 'c', v: ch });
    }
  });
  document.addEventListener('keyup', function (e) { if (pend && e.code === pend.code) flushPend(); });

  const pad = { raf: 0, j: null, btn: [] };
  function pollPad() {
    let gps = navigator.getGamepads ? navigator.getGamepads() : [], g = null;
    for (let i = 0; i < gps.length; i++) if (gps[i] && gps[i].connected) { g = gps[i]; break; }
    if (!g) { pad.raf = 0; return; }
    if (!document.body.classList.contains('welcome')) {
      let x = g.axes[0] || 0, y = g.axes[1] || 0, m = Math.hypot(x, y), bx = 0, by = 0;
      if (m > 0.22) { const k = (m - 0.22) / 0.78 * 90 / m; bx = x * k * (left ? -1 : 1); by = -y * k; }   // a dead zone, then up to 90 units
      if (!pad.j) pad.j = new Jabs(0, 0);
      const done = pad.j.add(bx, by);
      if (done) { showCand(null); commitSlot(jab(table(), done)); }
      else if (pad.j.cur.length) showCand(jab(table(), pad.j.cur));
      [['space', 0], ['bksp', 1], ['enter', 2], ['shift', 3]].forEach(function (b) {
        const on = !!(g.buttons[b[1]] && g.buttons[b[1]].pressed);
        if (on && !pad.btn[b[1]]) keyAction(b[0]);
        pad.btn[b[1]] = on;
      });
    }
    pad.raf = requestAnimationFrame(pollPad);
  }
  window.addEventListener('gamepadconnected', function () { if (!pad.raf) pollPad(); });

  // ---- phrases: own first, then history, the ready ones and the day's; a few first letters (or the first letters
  // of words: «ск ст») bring a whole phrase ----
  function norm(s) { return fold(s.toLowerCase()).trim(); }
  function completePhrase(typed) {
    const t = norm(typed); if (t.length < 2) return [];
    const all = [], seen = Object.create(null);
    [mine(), history(), D.situations.reduce(function (a, s) { return a.concat(s[1]); }, []), D.day.reduce(function (a, d) { return a.concat(d[2]); }, []), D.quick]
      .forEach(function (list) { list.forEach(function (p) { const k = norm(p); if (!seen[k]) { seen[k] = 1; all.push(p); } }); });
    const starts = all.filter(function (p) { const n = norm(p); return n.indexOf(t) === 0 && n !== t; });
    const pieces = t.split(' ').filter(Boolean);
    const byWords = pieces.length < 2 ? [] : all.filter(function (p) {
      if (starts.indexOf(p) >= 0) return false;
      let ws = norm(p).split(/[^\p{L}]+/u).filter(Boolean), w = 0;
      return pieces.every(function (pc) { while (w < ws.length && ws[w].indexOf(pc) !== 0) w++; if (w === ws.length) return false; w++; return true; });
    });
    return starts.concat(byWords).slice(0, 3);
  }

  // the row above the tabs: the screen's main action (under the thumb, never scrolled away), whole phrases, words
  let chipMode = 'speak', chipAction = null;
  function chip(label, cls, fn) {
    const b = document.createElement('button'); b.textContent = label; if (cls) b.className = cls; b.onclick = fn;
    b.onmousedown = function (e) { if (sysKbd) e.preventDefault(); };   // a word chip must not close the phone's keyboard
    return b;
  }
  function paintChips() {
    const box = $('chips'); box.innerHTML = ''; if (!$('text')) return;
    if (chipAction && (!chipAction.ifText || text.trim())) box.appendChild(chip(chipAction.label, 'main', chipAction.fn));
    if (guided) return;
    const shown = Object.create(null);
    if (chipMode === 'speak') completePhrase(text).forEach(function (p) { shown[norm(p)] = 1; box.appendChild(chip(p, '', function () { text = p; sayAll(); })); });
    wordChips().forEach(function (s) { if (!shown[norm(s)]) box.appendChild(chip(s, '', function () { takeWord(s); })); });
  }

  // «пульк» on every letter: a short soft drop, heard without looking (there is no vibration on iPhone)
  let audio = null, drop = null;
  function tick() {
    if (!store.get('tick', true) || store.get('calm', false)) return;
    try {
      const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return;
      if (!audio) {
        audio = new AC(); const sr = audio.sampleRate, n = Math.round(0.09 * sr); drop = audio.createBuffer(1, n, sr);
        let d = drop.getChannelData(0), ph = 0;
        for (let i = 0; i < n; i++) { const t = i / sr, f = 420 + 700 * (1 - Math.exp(-t / 0.02)); ph += 2 * Math.PI * f / sr; d[i] = Math.sin(ph) * (1 - Math.exp(-t / 0.004)) * Math.exp(-t / 0.03) * 0.25; }
      }
      if (audio.state === 'suspended') audio.resume();
      const src = audio.createBufferSource(); src.buffer = drop; src.connect(audio.destination); src.start();
    } catch (e) {}
  }

  // ---- the voice ----
  let lastSaid = '', utter = null;
  function rate() { return store.get('rate', 1); }
  // voices of the phone itself first: an «online» voice (Google, Microsoft in some browsers) sends the phrase to its
  // service, and «nothing leaves the phone» would not be true — such a voice is never picked by default and is marked
  function voices(ru) {
    if (!window.speechSynthesis) return [];
    const vs = speechSynthesis.getVoices().filter(function (x) { return x.lang && x.lang.toLowerCase().indexOf(ru ? 'ru' : 'en') === 0; });
    return vs.filter(onPhone).concat(vs.filter(function (x) { return !onPhone(x); }));
  }
  function onPhone(v) { return v.localService !== false; }
  function say(t, word) {
    t = (t || '').trim(); if (!t || !window.speechSynthesis) return;
    if (!word) lastSaid = t;
    const ss = speechSynthesis, ru = /[а-яё]/i.test(t) || !/[a-z]/i.test(t);
    const u = new SpeechSynthesisUtterance(t), vs = voices(ru), chosen = store.get('voice', '');
    u.lang = ru ? 'ru-RU' : 'en-US'; u.rate = store.get('calm', false) ? Math.min(rate(), 0.9) : rate(); u.pitch = store.get('pitch', 1);
    u.voice = vs.filter(function (x) { return x.name === chosen; })[0] || vs[0] || null;
    utter = u;   // Safari may drop an utterance nobody holds on to
    // Safari can swallow a phrase spoken right after cancel(): cancel only what is still going, and give it a moment
    // a browser that throws on speaking leaves the phrase silent, never the page broken
    try { if (ss.speaking || ss.pending) { ss.cancel(); setTimeout(function () { if (utter === u) try { ss.speak(u); } catch (e) {} }, 60); } else ss.speak(u); } catch (e) {}
  }
  // «Говорить по словам»: each finished word is said at once, so the person beside follows the phrase as it grows
  function sayWord() { if (tab !== 'speak' || guided || !store.get('words', false)) return; const m = /([\p{L}\p{N}-]+) $/u.exec(text); if (m) say(m[1], true); }
  // the screen stays on during a conversation: while one types an answer, the partner waits, and so must the phone
  let wake = null;
  function keepAwake() {
    if (tab !== 'speak' || wake || !navigator.wakeLock || document.visibilityState !== 'visible') return;
    wake = 'asking';
    navigator.wakeLock.request('screen').then(function (w) {
      if (tab !== 'speak') { w.release(); wake = null; return; }
      wake = w; w.addEventListener('release', function () { if (wake === w) wake = null; });
    }).catch(function () { wake = null; });
  }
  function letSleep() { if (wake && wake.release) wake.release(); wake = null; }
  document.addEventListener('visibilitychange', keepAwake);
  function history() { return store.get('phrases', []); }
  function remember(t) { t = t.trim(); store.set('phrases', [t].concat(history().filter(function (p) { return norm(p) !== norm(t); })).slice(0, 200)); logSaid(learnText(t) || 0); }
  // the conversation: what the child said and what the adult wrote, in turns, kept on this phone
  function talk(who, t) { t = (t || '').trim(); if (!t) return; const l = store.get('talk', []); l.push({ w: who, t: t, at: Date.now() }); store.set('talk', l.slice(-100)); }
  let talking = false;
  function sayAll() {
    const fix = fixLastWord(); if (fix) lastFix = null;   // a slip in the last word is fixed before it is said aloud
    const t = text.trim(); if (!t) return;
    say(t); remember(t); text = ''; lastFix = null;
    if (tab === 'speak') { talk('me', t); if (talking) talkScreen(); else speakScreen(true); } else render();
  }
  function showBig(t) {
    t = t || lastSaid || ''; const b = $('bigText'); b.textContent = t; b.style.fontSize = (t.length < 16 ? 60 : t.length < 50 ? 42 : 30) + 'px'; $('big').style.display = 'flex';
    if (!bigOpen) { bigOpen = true; try { window.history.pushState({ pulk: 'big' }, ''); } catch (x) {} }   // «назад» closes the big phrase
  }
  let afterBig = null;
  function closeBig() { bigOpen = false; $('big').style.display = 'none'; const f = afterBig; afterBig = null; if (f) f(); }
  $('flip').onclick = function () { $('big').classList.toggle('flip'); };
  $('back').onclick = function () { if (bigOpen) window.history.back(); else closeBig(); };

  // ---- the pond ----
  function drops() { return store.get('drops', 0); }
  function addDrop() { store.set('drops', drops() + 1); return drops(); }

  // ---- screens ----
  let tab = 'speak';
  function btn(label, cls, fn) { return chip(label, cls, fn); }
  function h(tag, txt, cls) { const e = document.createElement(tag); e.textContent = txt; if (cls) e.className = cls; return e; }
  // a delete that a shaking hand can touch by accident: the first touch asks, the second (within 4 s) removes
  function sureBtn(label, cls, fn) {
    let armed = 0;
    const b = btn(label, cls, function () {
      if (Date.now() - armed < 4000) { armed = 0; fn(); return; }
      armed = Date.now(); b.textContent = 'Убрать? Ещё раз'; b.classList.add('urgent');
      setTimeout(function () { if (armed && Date.now() - armed >= 4000) { armed = 0; b.textContent = label; b.classList.remove('urgent'); } }, 4100);
    });
    return b;
  }
  function row(cls) { const r = document.createElement('div'); r.className = cls || 'row'; return r; }
  function dots(n, at, label) { const d = h('div', '', 'dots'), b = document.createElement('b'); for (let i = 0; i < n; i++) b.textContent += i < at ? '●' : i === at ? '◉' : '○'; d.appendChild(b); d.appendChild(document.createTextNode(label)); return d; }
  function textBox(hint) { const t = document.createElement('div'); t.id = 'text'; t.setAttribute('data-hint', hint); return t; }
  function field(value, placeholder, onInput, multi) {
    const f = document.createElement(multi ? 'textarea' : 'input'); if (multi) f.rows = 3; else f.type = 'text';
    f.value = value; f.placeholder = placeholder; f.setAttribute('autocomplete', 'off');   // no «Автозаполнить контакт»
    f.oninput = function () { onInput(f.value); }; return f;
  }
  // «Назад»: the phone's back gesture (and the browser's button) goes to the previous screen of Пульк, not off the
  // page. Each screen marks itself; a screen already on the way back is returned to, not stacked again
  // (window.history: inside this file, history() is the list of said phrases)
  let visited = [], restoring = false, ignorePops = 0, waitingPushes = 0, bigOpen = false;
  function screenAt(name, show) {
    const e = { name: name, show: show, tab: tab, welcome: document.body.classList.contains('welcome') };
    if (restoring) { visited[visited.length - 1] = e; return; }
    let i; for (i = visited.length - 1; i >= 0 && visited[i].name !== name; i--) {}
    if (i >= 0) { const drop = visited.length - 1 - i; visited.length = i + 1; visited[i] = e; if (drop) { ignorePops++; window.history.go(-drop); } return; }
    visited.push(e);
    if (visited.length > 1) push();
  }
  // a step back the page asked for lands a moment later: a screen opened meanwhile is added only after it
  function push() { if (ignorePops) { waitingPushes++; return; } try { window.history.pushState({ pulk: visited.length }, ''); } catch (x) {} }
  window.addEventListener('popstate', function () {
    if (ignorePops) { if (!--ignorePops) for (; waitingPushes; waitingPushes--) push(); return; }
    if (bigOpen) { closeBig(); return; }
    if (visited.length < 2) return;
    visited.pop(); const e = visited[visited.length - 1];
    tab = e.tab; if (tab !== 'speak') letSleep();
    document.querySelectorAll('#tabs button').forEach(function (x) { x.setAttribute('aria-pressed', x.dataset.tab === tab); });
    document.body.classList.toggle('welcome', e.welcome);
    restoring = true; try { e.show(); } finally { restoring = false; }
  });
  function begin(mode) {   // every screen starts clean: no guide, no game, no leftover action or Enter
    talking = false; game = null; probe = null; document.body.classList.remove('talking');
    if (sysKbd && mode !== 'speak') { sysKbd = false; document.body.classList.remove('syskbd'); }   // lessons and games need the star
    const s = $('screen'); s.innerHTML = ''; chipMode = mode || 'none'; chipAction = null; onEnter = null; guided = null; showGuide(null); return s;
  }

  // «Говорить». Made for a conversation with the people close by: answers first, a choice the adult offers,
  // the phrase being typed, then phrases. Everything for the adult is on its own screen.
  function mine() { return store.get('mine', []); }
  function aboutMe() { return store.get('about', ''); }
  // «Сейчас»: phrases for this part of the day (as Phrases.now in the app)
  function dayNow() { let hr = new Date().getHours(), part = D.day[D.day.length - 1]; D.day.forEach(function (d) { if (hr >= d[1]) part = d; }); return ['Сейчас · ' + part[0], part[2]]; }
  // the family's own: words with photos (names, pets, favourite things) and the adult's own phrase groups
  function family() { return store.get('family', []); }
  function groups() { return store.get('groups', []); }
  function photoOf(word) { const n = norm(word), f = family().filter(function (x) { return norm(x.w) === n; })[0]; return f && f.img; }
  function situations() {
    const out = [];
    if (mine().length) out.push(['Мои', mine()]);
    if (family().length) out.push(['Семья', family()]);
    groups().forEach(function (g) { if (g.list.length) out.push([g.name, g.list, g]); });
    out.push(dayNow());
    return calm() ? out : out.concat(D.situations);   // calm: only one's own and the moment's phrases
  }
  function calm() { return store.get('calm', false); }
  function photoButton(label, img, cls, fn) {
    const b = btn('', cls, fn);
    if (img) { const i = document.createElement('img'); i.src = img; i.alt = ''; b.appendChild(i); b.classList.add('pic'); }
    b.appendChild(document.createTextNode(label)); return b;
  }
  let sitName = null;
  function speakScreen(top) {
    const s = begin('speak'); if (top === true) s.scrollTop = 0; pressTab('speak');
    screenAt('speak', function () { speakScreen(true); });
    // a choice the adult offered: big, first, said with one touch; a family photo shows on its option
    const ch = store.get('choice', null);
    if (ch && ch.opts && ch.opts.length) {
      const card = h('div', '', 'card choice');
      if (ch.q) card.appendChild(h('div', ch.q, 'cq'));
      const cg = row('choices');
      ch.opts.forEach(function (o, i) {
        cg.appendChild(photoButton((ch.picked === i ? '✓ ' : '') + o, photoOf(o), ch.picked === i ? 'main' : '', function () { say(o); remember(o); talk('me', o); ch.picked = i; store.set('choice', ch); speakScreen(); }));
      });
      card.appendChild(cg);
      card.appendChild(btn('Готово — убрать выбор', 'wide quiet', function () { store.set('choice', null); speakScreen(true); }));
      s.appendChild(card);
    }
    const g = row('answers');
    D.quick.forEach(function (p) { g.appendChild(btn(p.split(',')[0], '', function () { say(p); logSaid(0); talk('me', p); })); });
    s.appendChild(g);
    const cats = row('scroll'), list = document.createElement('div'), r0 = row();
    if (aboutMe()) { const am = btn('Про меня', 'main', function () { say(aboutMe()); showBig(aboutMe()); }); am.style.flex = '1'; r0.appendChild(am); }
    const ur = btn('Срочно', 'urgent', function () { sitName = 'Срочно'; paintSit(); list.scrollIntoView({ block: 'nearest' }); });
    ur.style.flex = '1'; r0.appendChild(ur);
    const tk = btn('💬 Разговор', '', talkScreen); tk.style.flex = '1'; r0.appendChild(tk);
    s.appendChild(r0);
    // the phrase being typed stays in sight above the chips while the answers and phrases scroll under it
    const cmp = h('div', '', 'compose'); s.appendChild(cmp);
    cmp.appendChild(textBox('Печатай звездой — я скажу'));
    const r = row('row acts');   // one line: a second line of buttons pushes the phrases under the chips
    r.appendChild(btn('Сказать', 'main', sayAll)); r.appendChild(btn('Показать', '', function () { showBig(text.trim()); }));
    r.appendChild(btn('★ В мои', '', function () {
      const t = text.trim() || lastSaid; if (!t) return;
      store.set('mine', [t].concat(mine().filter(function (p) { return norm(p) !== norm(t); })).slice(0, 60)); sitName = 'Мои'; text = ''; speakScreen(true);
    }));
    r.appendChild(btn('Стереть', '', function () { text = ''; lastFix = null; render(); }));
    const kb = btn(sysKbd ? '★' : '⌨', 'kb', function () { setSysKbd(!sysKbd); kb.textContent = sysKbd ? '★' : '⌨'; kb.setAttribute('aria-label', sysKbd ? 'Звезда' : 'Обычная клавиатура'); });
    kb.setAttribute('aria-label', sysKbd ? 'Звезда' : 'Обычная клавиатура'); r.appendChild(kb);
    cmp.appendChild(r);
    s.appendChild(h('h2', 'Фразы'));
    s.appendChild(cats); s.appendChild(list);
    function paintSit() {
      const all = situations(), names = all.map(function (x) { return x[0]; });
      if (names.indexOf(sitName) < 0) sitName = names.indexOf('Семья') >= 0 ? 'Семья' : dayNow()[0];   // the family first, else «Сейчас»
      if (sitName === 'Срочно' && names.indexOf('Срочно') < 0) sitName = names[0];
      const cur = all[names.indexOf(sitName)] || all.concat([D.situations.filter(function (x) { return x[0] === 'Срочно'; })[0]])[all.length];
      cats.innerHTML = ''; list.innerHTML = '';
      all.forEach(function (x) { cats.appendChild(btn((x[0] === sitName ? '✓ ' : '') + x[0], x[0] === sitName ? 'main' : '', function () { sitName = x[0]; paintSit(); })); });
      if (cur[0] === 'Семья') {
        const tiles = row('tiles');
        cur[1].forEach(function (f) { tiles.appendChild(photoButton(f.w, f.img, '', function () { say(f.w); remember(f.w); talk('me', f.w); })); });
        list.appendChild(tiles); return;
      }
      cur[1].forEach(function (p) {
        const line = row('line');
        line.appendChild(btn(p, 'wide', function () { say(p); remember(p); talk('me', p); }));
        if (cur[0] === 'Мои') line.appendChild(sureBtn('✕', 'x', function () { store.set('mine', mine().filter(function (q) { return q !== p; })); paintSit(); }));
        list.appendChild(line);
      });
    }
    paintSit();
    if (!calm()) {
      s.appendChild(h('h2', 'Сказано'));
      const hist = history().slice(0, 8);
      if (!hist.length) s.appendChild(h('p', 'Здесь появятся сказанные фразы — касание повторит.', 'muted'));
      hist.forEach(function (p) { s.appendChild(btn('↻ ' + p, 'wide', function () { say(p); talk('me', p); })); });
    }
    s.appendChild(h('h2', 'Для взрослого'));
    // «Толчки» for those who know «Пульс» (as the app): a newcomer learns one way, but it is found right here
    jabsToggle(s);
    if (store.get('doors', null) === 'quick' && !store.get('offered', false)) {
      const st0 = store.get('letters', {}), misses = Object.keys(st0).reduce(function (a, k) { return a + (st0[k][1] || 0); }, 0);
      if (misses >= 15) {
        const oc = h('div', '', 'card choice'); s.appendChild(oc);
        oc.appendChild(h('div', 'Звезда иногда промахивается. Подстроить под руку — 30 секунд?', 'title'));
        const orow = row(); oc.appendChild(orow);
        orow.appendChild(btn('Подстроить', 'main', function () { store.set('offered', true); handCheck(function () { speakScreen(true); }); }));
        orow.appendChild(btn('Не предлагать', '', function () { store.set('offered', true); speakScreen(); }));
      }
    }
    if (iosBrowser && hasOwnData()) s.appendChild(h('p', 'Пульк открыт в Safari: добавьте его на экран «Домой», чтобы слова и фото не пропали. Копия — в «Настройки и помощь».', 'warn'));
    const ar = row();
    ar.appendChild(btn('Предложить выбор', '', function () { adultScreen('choice'); }));
    ar.appendChild(btn('Настройки и помощь', '', function () { adultScreen(); }));
    s.appendChild(ar);
    if (!calm()) s.appendChild(h('p', 'Звезда: прямой смах — крупная буква, смах с поворотом — мелкая снаружи, «туда и обратно» — у центра, касание в центре — пробел. ⏎ — сказать, 🔊 — ещё раз. Промахи звезды Пульк поправит сам; ⌫ сразу после — вернуть как было. Работает и без интернета, если страница открывалась хотя бы раз.', 'muted'));
    onEnter = sayAll; chipAction = { label: 'Сказать', fn: sayAll, ifText: true }; render(); keepAwake();
  }

  // «Разговор»: the child's phrases and the adult's, in turns, big — the adult can write a question too: many
  // autistic children follow written words more easily than spoken ones, and the feed shows whose turn it is
  function talkScreen() {
    const s = begin('speak'); talking = true; document.body.classList.add('talking');
    screenAt('talk', talkScreen);
    const top = row();
    top.appendChild(btn('← Говорить', '', function () { speakScreen(true); }));
    top.appendChild(sureBtn('Новый разговор', 'quiet', function () { store.set('talk', []); talkScreen(); }));
    s.appendChild(top);
    let feed = h('div', '', 'feed'), list = store.get('talk', []).slice(-40), lastDay = '';
    if (!list.length) feed.appendChild(h('p', 'Здесь будет разговор по очереди: вопросы взрослого — слева, ответы — справа, крупно. Касание повторяет фразу вслух.', 'muted'));
    list.forEach(function (m) {
      const d = new Date(m.at), day = d.toDateString();
      if (day !== lastDay) { lastDay = day; feed.appendChild(h('div', d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' }), 'day')); }
      const b = btn(m.t, 'bubble ' + (m.w === 'me' ? 'me' : 'you'), function () { say(m.t, m.w !== 'me'); });
      feed.appendChild(b);
    });
    s.appendChild(feed);
    let ar = row('line'), ask = '';
    const af = field('', 'Взрослый: вопрос или ответ', function (v) { ask = v; });
    af.onkeydown = function (e) { if (e.key === 'Enter') { e.preventDefault(); send(); } };
    function send() { if (!ask.trim()) return; talk('you', ask); talkScreen(); }
    ar.appendChild(af); ar.appendChild(btn('→', 'main x', send));
    s.appendChild(ar);
    const g = row('scroll');
    D.quick.forEach(function (p) { g.appendChild(btn(p.split(',')[0], '', function () { say(p); logSaid(0); talk('me', p); talkScreen(); })); });
    s.appendChild(g);
    s.appendChild(textBox('Ответ — звездой'));
    onEnter = sayAll; chipAction = { label: 'Сказать', fn: sayAll, ifText: true }; render(); keepAwake();
    s.scrollTop = s.scrollHeight;
  }

  function pick(s, label, key, def, list, note) {
    s.appendChild(h('div', label, 'title'));
    if (note) s.appendChild(h('p', note, 'muted'));
    const r = row(); s.appendChild(r);
    (function paint() {
      r.innerHTML = '';
      list.forEach(function (x) { const on = store.get(key, def) === x[1]; r.appendChild(btn((on ? '✓ ' : '') + x[0], on ? 'main' : '', function () { store.set(key, x[1]); paint(); })); });
    })();
  }
  const JABS_NOTE = 'Толчки — целое слово одним касанием, не отрывая пальца: толчок к букве и назад; поднял палец — пробел. Быстрее, когда «Пульс» уже освоен.';
  function jabsToggle(s) {
    const b = btn('', 'wide', function () { store.set('mode', store.get('mode', 'pulse') === 'jabs' ? 'pulse' : 'jabs'); paint(); });
    function paint() { const on = store.get('mode', 'pulse') === 'jabs'; b.textContent = (on ? '✓ ' : '') + 'Толчки — для опытных'; b.className = on ? 'main wide' : 'wide'; }
    paint(); s.appendChild(b); s.appendChild(h('p', JABS_NOTE, 'muted'));
  }
  function toggle(label, key, def, after) {
    const b = btn('', 'wide', function () { store.set(key, !store.get(key, def)); paint(); if (after) after(); });
    function paint() { const on = store.get(key, def); b.textContent = (on ? '✓ ' : '') + label; b.className = on ? 'main wide' : 'wide'; }
    paint(); return b;
  }
  function voiceControls(s) {
    const vr = row(), st = row(), pt = row(); s.appendChild(vr); s.appendChild(st); s.appendChild(pt);
    function paintVoices() {
      vr.innerHTML = ''; const vs = voices(true), cur = store.get('voice', '') || (vs[0] && vs[0].name);
      if (!vs.length) vr.appendChild(h('p', 'Русский голос не найден. На iPhone: Настройки → Универсальный доступ → Устная речь → Голоса → Русский.', 'muted'));
      if (vs.length > 1) vs.forEach(function (v) { vr.appendChild(btn((v.name === cur ? '✓ ' : '') + v.name.replace(/\s*\(.*\)$/, '') + (onPhone(v) ? '' : ' · через интернет'), v.name === cur ? 'main' : '', function () { store.set('voice', v.name); paintVoices(); say('Привет! Это мой голос.', true); })); });
      const curV = vs.filter(function (v) { return v.name === cur; })[0];
      if (curV && !onPhone(curV)) vr.appendChild(h('p', 'Этот голос работает через интернет: каждая фраза уходит его сервису. Чтобы всё оставалось на устройстве, установите русский голос в настройках устройства и выберите его здесь.', 'warn'));
    }
    function choices(box, key, def, list, phrase) {
      box.innerHTML = '';
      list.forEach(function (x) { const on = store.get(key, def) === x[1]; box.appendChild(btn((on ? '✓ ' : '') + x[0], on ? 'main' : '', function () { store.set(key, x[1]); choices(box, key, def, list, phrase); say(phrase, true); })); });
    }
    paintVoices();
    choices(st, 'rate', 1, [['Медленно', 0.8], ['Обычно', 1], ['Быстро', 1.3]], 'Так я говорю.');
    // a higher pitch sounds younger: phones have no children's voices, this is the closest a page can get
    choices(pt, 'pitch', 1, [['Голос обычный', 1], ['Моложе', 1.3]], 'Привет! Это мой голос.');
    if (window.speechSynthesis) speechSynthesis.onvoiceschanged = function () { if (vr.isConnected) paintVoices(); };
  }
  function handButtons() {
    const r = row('pair');
    [['Правая', false], ['Левая', true]].forEach(function (x) {
      r.appendChild(btn((left === x[1] ? '✓ ' : '') + x[0], left === x[1] ? 'main' : '', function () {
        left = x[1]; store.set('hand', left ? 'left' : 'right'); layout(true);
        const n = r.children; for (let i = 0; i < n.length; i++) { const on = i === (left ? 1 : 0); n[i].className = on ? 'main' : ''; n[i].textContent = (on ? '✓ ' : '') + (i ? 'Левая' : 'Правая'); }
      }));
    });
    return r;
  }
  // a photo from the camera or the gallery, made small and square: it lives in this browser's storage
  function pickPhoto(done) {
    const f = document.createElement('input'); f.type = 'file'; f.accept = 'image/*';
    f.onchange = function () {
      const file = f.files && f.files[0]; if (!file) return;
      const img = new Image(), url = URL.createObjectURL(file);
      // a picture that will not open (or too big for this phone's memory) gives null: the button says so
      img.onload = function () {
        let d = null;
        try {
          const c = document.createElement('canvas'), n = 160, side = Math.min(img.width, img.height); c.width = c.height = n;
          c.getContext('2d').drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, n, n);
          d = side > 0 ? c.toDataURL('image/jpeg', 0.75) : null;
        } catch (e) {}
        URL.revokeObjectURL(url); done(d);
      };
      img.onerror = function () { URL.revokeObjectURL(url); done(null); };
      img.src = url;
    };
    f.click();
  }

  // ---- the first visit (as WizardActivity in the app): the hand and the voice, then a fork — «Начать сразу» or
  // «Подстроить под человека». Five doors, asked as difficulties, never as diagnoses; «Не знаю» is always safe. ----
  const DOORS = [
    { key: 'hands', title: 'Рука дрожит, задевает экран или быстро устаёт?', note: 'Подстроим, как звезда принимает касания.',
      answers: [['Нет', 0], ['Немного', 1], ['Да, смахивать трудно', 2], ['Не знаю', 'u']] },
    { key: 'eyes', title: 'Трудно видеть буквы на экране?', note: 'Сделаем крупнее.',
      answers: [['Нет', 0], ['Да', 1], ['Совсем не видно', 2], ['Не знаю', 'u']] },
    { key: 'speech', title: 'Говорить голосом трудно?', note: 'Тогда главное — «Говорить»: телефон произносит набранное.',
      answers: [['Нет', 0], ['Да', 1], ['Не знаю', 'u']] },
    { key: 'calm', title: 'Мешают лишние кнопки, звуки, движение?', note: 'Оставим только нужное, звук и голос — мягче.',
      answers: [['Нет', 0], ['Да', 1], ['Не знаю', 'u']] },
    { key: 'helper', title: 'Рядом будет взрослый, который помогает?', note: 'Покажем ему минутную памятку.',
      answers: [['Да', 1], ['Нет', 0]] },
  ];
  // what each answer turns on; «Не знаю» turns on only what never gets in the way
  function applyDoors(d) {
    if (d.hands === 1 || d.hands === 'u') { store.set('guard', 1); if (d.hands === 1) store.set('smooth', 1); }
    if (d.hands === 2) { store.set('guard', 2); store.set('smooth', 2); store.set('bigStar', true); }
    if (d.eyes === 1 || d.eyes === 2) { store.set('bigStar', true); store.set('bigText', true); }
    if (d.calm === 1) store.set('calm', true);
    store.set('doors', d); layout(true); applyLook();
  }
  function applyLook() { document.body.classList.toggle('big-text', !!store.get('bigText', false)); }
  // what is on now, in words — the summary after the doors and the head of «Удобство»
  function comfortSummary() {
    const on = [];
    if (left) on.push('левая рука');
    const g = store.get('guard', 0); if (g) on.push('защита от случайных касаний' + (g === 2 ? ' (сильная)' : ''));
    const sm = store.get('smooth', 0); if (sm) on.push('сглаживание дрожи' + (sm === 2 ? ' (сильное)' : ''));
    const tl = store.get('tol', 1); if (tl !== 1) on.push(tl < 1 ? 'короткий смах' : 'длинный смах');
    if (store.get('bigStar', false)) on.push('крупная звезда');
    if (store.get('bigText', false)) on.push('крупный текст');
    if (store.get('calm', false)) on.push('спокойный режим');
    if (store.get('mode', 'pulse') === 'jabs') on.push('«Толчки»');
    if (store.get('words', false)) on.push('говорить по словам');
    return on;
  }
  function welcomeScreen() {
    document.body.classList.add('welcome');
    const s = begin(); s.scrollTop = 0;
    screenAt('welcome', welcomeScreen);
    const top = h('div', '', 'hello'), img = document.createElement('img'); img.src = '../icon.png'; img.alt = '';
    top.appendChild(img); const tt = document.createElement('div'); tt.appendChild(h('h1', 'Пульк'));
    tt.appendChild(h('p', 'Печатай одним пальцем — телефон скажет вслух.', 'note')); top.appendChild(tt);
    s.appendChild(top);
    const c1 = h('div', '', 'card'); s.appendChild(c1);
    c1.appendChild(h('h2', 'Какой рукой?'));
    c1.appendChild(h('p', 'Клавиатура встанет под большой палец этой руки.', 'note'));
    c1.appendChild(handButtons());
    c1.appendChild(btn('🔊 Послушать голос', 'wide', function () { say('Привет! Это мой голос. Я скажу всё, что ты напечатаешь.', true); }));
    s.appendChild(btn('Начать сразу', 'main wide', function () { store.set('doors', 'quick'); summaryScreen({}); }));
    s.appendChild(h('p', 'Обычные настройки. Подстроить можно потом: «Настройки и помощь» → «Удобство».', 'muted'));
    s.appendChild(btn('Подстроить под человека — 2 минуты', 'wide', function () { doorScreen(0, {}); }));
    s.appendChild(h('p', 'Пять вопросов о том, что трудно и что мешает. Настраиваете для ребёнка или ученика — выберите это.', 'muted'));
    let tip = 'Набранное остаётся на телефоне.';
    if (iosBrowser) tip += ' Чтобы открывать как приложение: «Поделиться» → «На экран „Домой“».';
    s.appendChild(h('p', tip, 'muted'));
  }
  function doorScreen(i, d) {
    if (i >= DOORS.length) { applyDoors(d); summaryScreen(d); return; }
    const s = begin(); s.scrollTop = 0; const door = DOORS[i];
    screenAt('door' + i, function () { doorScreen(i, d); });
    s.appendChild(dots(DOORS.length, i, 'вопрос ' + (i + 1) + ' из ' + DOORS.length));
    s.appendChild(h('h1', door.title));
    s.appendChild(h('p', door.note, 'note'));
    door.answers.forEach(function (x) { const b = btn(x[0], x[1] === 1 || x[1] === 2 ? 'wide' : 'wide', function () { d[door.key] = x[1]; doorScreen(i + 1, d); }); b.classList.add('answer'); s.appendChild(b); });
    if (i > 0) s.appendChild(btn('← Назад', 'wide quiet', function () { doorScreen(i - 1, d); }));
  }
  function summaryScreen(d) {
    document.body.classList.add('welcome');   // still before the keyboard
    const s = begin(); s.scrollTop = 0;
    screenAt('summary', function () { summaryScreen(d); });
    const on = comfortSummary();
    s.appendChild(h('h1', d.hands === undefined ? 'Можно начинать' : 'Готово — подстроено'));
    s.appendChild(h('p', on.length ? 'Включено: ' + on.join(', ') + '.' : 'Обычные настройки — их можно поменять в «Удобстве».', 'note'));
    if (d.eyes === 2) s.appendChild(h('p', 'Для тех, кто не видит экран, лучше Android-версия Пулька: в ней есть звуковой компас и экранный диктор.', 'warn'));
    if (d.hands === 1 || d.hands === 2 || d.hands === 'u') {
      const hc = h('div', '', 'card choice'); s.appendChild(hc);
      hc.appendChild(h('div', 'Проверить руку — 30 секунд', 'title'));
      hc.appendChild(h('p', 'Подержать палец на звезде и сделать 4 смаха. Пульк сам подберёт сглаживание дрожи и длину смаха.', 'muted'));
      hc.appendChild(btn('Проверить руку', 'main wide', function () { document.body.classList.remove('welcome'); handCheck(function () { summaryScreen(d); }); }));
    }
    s.appendChild(h('h2', 'Как тебя зовут?'));
    s.appendChild(h('p', 'Можно пропустить. Из имени получится карточка «Про меня» — её можно показать незнакомым.', 'note'));
    let name = ''; s.appendChild(field('', 'Имя', function (v) { name = v.trim(); }));
    function start(where) {
      if (name && !aboutMe()) store.set('about', 'Меня зовут ' + name + '. Я не говорю, но всё понимаю. Я отвечу на телефоне — подождите, пожалуйста.');
      store.set('welcomed', true); document.body.classList.remove('welcome');
      const go = function () { document.querySelector('#tabs button[data-tab="' + where + '"]').click(); };
      if (d.helper === 1) { tab = where; parentsScreen(go); } else go();   // the adult beside reads a minute first
    }
    s.appendChild(btn(d.speech === 1 ? 'Учиться говорить — 3 минуты' : 'Начать с урока — 3 минуты', 'main wide', function () { start('lesson'); }));
    s.appendChild(btn('Сразу говорить', 'wide', function () { start('speak'); }));
    s.appendChild(btn('← Изменить ответы', 'wide quiet', function () { doorScreen(0, {}); }));
  }

  // «Проверить руку» (as CalibrationActivity, shorter): the finger held still shows the tremor; four strokes show how
  // long this hand's strokes are. The star listens without typing.
  let probe = null;
  function handCheck(back) {
    const s = begin(); s.scrollTop = 0;
    screenAt('hand', function () { handCheck(back); });
    let dirs = [[90, 'вверх'], [0, 'вправо'], [225, 'вниз-влево'], [180, 'влево']], lens = [], jit = null;
    s.appendChild(h('h1', 'Проверить руку'));
    const say1 = h('p', '', 'note'), ok = h('div', '', 'ok'); s.appendChild(say1); s.appendChild(ok);
    s.appendChild(btn('Пропустить', 'wide quiet', function () { probe = null; showGuide(null); back(); }));
    function ask() {
      if (jit === null) { say1.textContent = 'Положи палец на звезду и подержи неподвижно, пока не скажу «хорошо» — пару секунд.'; showGuide(null); return; }
      if (lens.length < dirs.length) { const d0 = dirs[lens.length]; say1.textContent = 'Смахни ' + d0[1] + ', как тебе удобно (' + (lens.length + 1) + ' из 4).'; showGuide(slot(D.RU, Math.round(d0[0] / 45) % 8, 'c').v); return; }
      probe = null; showGuide(null);
      const avg = lens.reduce(function (a, x) { return a + x; }, 0) / lens.length;
      store.set('smooth', jit > 3 ? 2 : jit > 1.5 ? 1 : store.get('smooth', 0));
      store.set('tol', avg < 45 ? 0.75 : avg > 95 ? 1.4 : 1);
      say1.textContent = 'Готово. Дрожь: ' + (jit > 3 ? 'заметная — сглаживание сильное' : jit > 1.5 ? 'небольшая — сглаживание лёгкое' : 'почти нет') + '. Смах: ' + (avg < 45 ? 'короткий — хватит маленького движения' : avg > 95 ? 'длинный — мелкие подёргивания не считаются' : 'обычный') + '.';
      s.appendChild(btn('Дальше', 'main wide', back));
    }
    probe = function (t) {
      const n = t.pts.length / 2, dur = Date.now() - t.start;
      if (jit === null) {
        if (dur < 1500) { ok.textContent = 'Чуть дольше — пару секунд.'; return; }
        let mx0 = 0, my0 = 0; for (let i = 0; i < n; i++) { mx0 += t.pts[2 * i]; my0 += t.pts[2 * i + 1]; } mx0 /= n; my0 /= n;
        let v = 0; for (let k = 0; k < n; k++) v += Math.pow(t.pts[2 * k] - mx0, 2) + Math.pow(t.pts[2 * k + 1] - my0, 2);
        jit = Math.sqrt(v / n); ok.textContent = 'Хорошо.'; ask(); return;
      }
      let far = 0; for (let q = 1; q < n; q++) far = Math.max(far, Math.hypot(t.pts[2 * q] - t.pts[0], t.pts[2 * q + 1] - t.pts[1]));
      if (far < 8) { ok.textContent = 'Это касание — нужен смах.'; return; }
      lens.push(far); ok.textContent = ''; ask();
    };
    ask(); render();
  }

  // ---- the child's words, photos and phrases live in this browser only. Safari on iPhone may clear a site's data
  // after 7 days without a visit — unless Пульк is on the home screen; a copy to a file keeps them in any case ----
  const iosBrowser = navigator.standalone === false;   // iPhone Safari, not opened from the home screen
  if (navigator.storage && navigator.storage.persist) navigator.storage.persisted().then(function (p) { if (!p) navigator.storage.persist(); }).catch(function () {});
  function hasOwnData() { return mine().length || family().length || groups().length || aboutMe() || drops() > 0; }
  function backupData() {
    const o = {};
    for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); if (k && k.indexOf('pulk.') === 0) o[k] = localStorage.getItem(k); }
    return { app: 'pulk', v: 1, at: new Date().toISOString(), data: o };
  }
  function saveBackup(done) {
    store.set('backupAt', Date.now());
    let json = JSON.stringify(backupData()), name = 'pulk-kopiya-' + today() + '.json', file = null;
    try { file = new File([json], name, { type: 'application/json' }); } catch (e) {}
    function download() {
      const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([json], { type: 'application/json' })); a.download = name;
      document.body.appendChild(a); a.click(); setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1500); done('Копия сохранена: ' + name);
    }
    // a phone offers its own sheet: Files, a messenger, mail; elsewhere the file is downloaded
    if (file && navigator.canShare && navigator.canShare({ files: [file] })) {
      navigator.share({ files: [file], title: 'Пульк — копия' }).then(function () { done('Копия отправлена.'); }, function (e) { if (e && e.name === 'AbortError') done(''); else download(); });
    } else download();
  }
  function restoreBackup(done) {
    const f = document.createElement('input'); f.type = 'file'; f.accept = 'application/json,.json';
    f.onchange = function () {
      const file = f.files && f.files[0]; if (!file) return;
      const r = new FileReader();
      r.onload = function () {
        if (applyBackup(r.result)) { done('Восстановлено. Пульк откроется заново.'); setTimeout(function () { location.reload(); }, 900); }
        else done('Это не копия Пулька — ничего не изменено.');
      };
      r.readAsText(file);
    };
    f.click();
  }
  function applyBackup(text) {
    try {
      const b = JSON.parse(text); if (!b || b.app !== 'pulk' || !b.data) return false;
      Object.keys(b.data).forEach(function (k) { if (k.indexOf('pulk.') === 0 && typeof b.data[k] === 'string') localStorage.setItem(k, b.data[k]); });
      return true;
    } catch (e) { return false; }
  }
  function safetyCard(s) {
    const c = h('div', '', 'card'); s.appendChild(c);
    c.appendChild(h('div', 'Сохранность слов и фото', 'title'));
    if (iosBrowser) c.appendChild(h('p', 'Добавьте Пульк на экран «Домой» (в Safari «Поделиться» → «На экран „Домой“»). Иначе iPhone может стереть слова, фото и фразы, если Пульк не открывали 7 дней.', 'warn'));
    const at = store.get('backupAt', 0);
    c.appendChild(h('p', at ? 'Последняя копия: ' + new Date(at).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' }) + '.' : 'Копию ещё не сохраняли. Она нужна, чтобы перенести Пулька на другой телефон или вернуть всё после сбоя.', 'muted'));
    const ok = h('div', '', 'ok'), r = row();
    r.appendChild(btn('Сохранить копию', 'main', function () { saveBackup(function (m) { ok.textContent = m; }); }));
    r.appendChild(btn('Восстановить из копии', '', function () { restoreBackup(function (m) { ok.textContent = m; }); }));
    c.appendChild(r); c.appendChild(ok);
  }

  // the week, kept on this phone only: phrases said and new words, so the adult sees that it grows
  function today() { const d = new Date(); return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate(); }
  function logSaid(newWords) {
    const log = store.get('log', {}), k = today(), v = log[k] || [0, 0];
    v[0]++; v[1] += newWords; log[k] = v;
    const keys = Object.keys(log); if (keys.length > 60) delete log[keys[0]];
    store.set('log', log);
  }
  function week() {
    let log = store.get('log', {}), said = 0, fresh = 0, days = 0;
    for (let i = 0; i < 7; i++) { const d = new Date(Date.now() - i * 864e5), v = log[d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate()]; if (v) { said += v[0]; fresh += v[1]; days++; } }
    return { said: said, fresh: fresh, days: days };
  }
  // one small step for today, from what has (not) been tried yet
  function todayTip() {
    if (!store.get('lessonDone', false)) return 'Сегодня — урок «Учиться», 3 минуты. Вы рядом, палец — его.';
    if (!store.get('choices', []).length) return 'Сегодня — выбор из двух: «Предложить выбор» → «сок, вода». Спросите вслух и подождите.';
    if (family().length < 3) return 'Сегодня — словарь семьи: три слова с фото (мама, любимая игрушка, питомец).';
    if (hasOwnData() && Date.now() - store.get('backupAt', 0) > 14 * 864e5) return 'Сегодня — сохраните копию слов и фото: кнопка «Сохранить копию» чуть ниже. Минута — и ничего не пропадёт.';
    if (!store.get('drillsDone', {})['Просьбы']) return 'Сегодня — тренировка «Просьбы» во вкладке «Тренировки».';
    const tips = [
      'Сегодня — фразы «Близким»: покажите «Обними меня» и «Посмотри!».',
      'Сегодня — ответьте через Пульк сами: напечатайте ему короткую фразу.',
      'Сегодня — один вопрос с выбором за едой: «Что ещё положить?»',
      'Сегодня — «Про меня»: покажите, как представиться незнакомому.',
      'Сегодня — пусть он напечатает одно своё слово. Любое.',
    ];
    return tips[Math.floor(Date.now() / 864e5) % tips.length];
  }

  // the adult's screen: what to try today, the week, a choice to offer, the family's words, own phrases, settings
  function adultScreen(focus) {
    const s = begin(); s.scrollTop = 0;
    screenAt('adult', function () { adultScreen(); });
    s.appendChild(btn('← Вернуться', '', function () { sitName = null; speakScreen(true); }));
    s.appendChild(h('h1', 'Для взрослого'));
    const tc = h('div', '', 'card choice'); tc.appendChild(h('div', todayTip(), 'title')); s.appendChild(tc);
    if (hasOwnData() || iosBrowser) safetyCard(s);
    const cc = h('div', '', 'card'); s.appendChild(cc);
    cc.appendChild(h('div', 'Удобство — под особенности человека', 'title'));
    const on0 = comfortSummary(); cc.appendChild(h('p', on0.length ? 'Включено: ' + on0.join(', ') + '.' : 'Сейчас обычные настройки.', 'muted'));
    cc.appendChild(btn('Открыть «Удобство»', 'main wide', function () { comfortScreen(); }));
    const w = week();
    s.appendChild(h('p', w.said ? 'За неделю: сказано ' + w.said + ' раз, новых слов — ' + w.fresh + ', дней с Пульком — ' + w.days + '. Всё это хранится только на этом телефоне.' : 'Здесь появится, сколько сказано за неделю. Хранится только на этом телефоне.', 'muted'));

    const ch = h('h2', 'Предложить выбор'); s.appendChild(ch);
    s.appendChild(h('p', 'Спросите вслух и напишите варианты обычной клавиатурой. Ребёнок коснётся — телефон скажет. Фото из словаря семьи появятся на кнопках сами.', 'note'));
    let q = '', opts = '';
    s.appendChild(field('', 'Вопрос, можно пусто: Что будешь пить?', function (v) { q = v; }));
    s.appendChild(field('', 'Варианты через запятую: сок, вода, молоко', function (v) { opts = v; }));
    function offer(question, list) {
      list = list.map(function (o) { o = o.trim(); return o && o[0].toUpperCase() + o.slice(1); }).filter(Boolean).slice(0, 6); if (!list.length) return;
      const c = { q: question.trim(), opts: list, picked: -1 };
      store.set('choice', c); talk('you', c.q);
      const recent = store.get('choices', []).filter(function (x) { return x.opts.join() !== c.opts.join(); });
      store.set('choices', [{ q: c.q, opts: c.opts }].concat(recent).slice(0, 8));
      tab = 'speak'; speakScreen(true);
    }
    s.appendChild(btn('Показать ребёнку', 'main wide', function () { offer(q, opts.split(/[,;\n]/)); }));
    const recent = store.get('choices', []);
    if (recent.length) {
      s.appendChild(h('p', 'Недавние — одним касанием:', 'muted'));
      recent.forEach(function (c) { s.appendChild(btn((c.q ? c.q + ' — ' : '') + c.opts.join(', '), 'wide', function () { offer(c.q, c.opts); })); });
    }

    const fh = h('h2', 'Словарь семьи'); s.appendChild(fh);
    s.appendChild(h('p', 'Имена, питомцы, любимые вещи и места — с фото, если хотите. Эти слова первыми идут в подсказках, а фото — на кнопках выбора и во вкладке «Семья».', 'note'));
    let fw = '', fimg = null, fl = h('div', '', 'tiles'), fr = row();
    const fwf = field('', 'Слово: Барсик, бабушка, качели', function (v) { fw = v.trim(); });
    const pb = btn('📷 Фото', '', function () { pickPhoto(function (d) { fimg = d; pb.textContent = d ? '📷 Фото ✓' : '📷 Фото не открылось — другое?'; }); });
    fr.appendChild(pb);
    fr.appendChild(btn('Добавить', 'main', function () {
      if (!fw) return;
      const word = fw[0].toUpperCase() + fw.slice(1), list = family().filter(function (x) { return norm(x.w) !== norm(word); });
      list.push({ w: word, img: fimg });
      if (!store.set('family', list.slice(-60))) {   // storage full: keep the word without its photo, and say so
        list[list.length - 1].img = null; store.set('family', list.slice(-60));
        fr.appendChild(h('p', 'Память браузера заполнена: слово сохранено без фото. Уберите старые фото и добавьте снова.', 'muted'));
      }
      const u = userWords(), k = fold(word.toLowerCase()); u[k] = Math.max(own(u, k), 5); store.set('user', u);   // known and first in suggestions
      fw = ''; fimg = null; fwf.value = ''; pb.textContent = '📷 Фото'; paintFamily();
    }));
    s.appendChild(fwf); s.appendChild(fr); s.appendChild(fl);
    function paintFamily() {
      fl.innerHTML = '';
      family().forEach(function (f) {
        let armed = 0;
        const fb = photoButton(f.w + ' ✕', f.img, '', function () {
          if (Date.now() - armed < 4000) { store.set('family', family().filter(function (x) { return x !== f; })); paintFamily(); return; }
          armed = Date.now(); fb.lastChild.textContent = 'Убрать? Ещё раз'; fb.classList.add('urgent');   // a photo is hard to bring back
        });
        fl.appendChild(fb);
      });
      if (family().length) fl.appendChild(h('p', 'Два касания по слову здесь — убрать его.', 'muted'));
    }
    paintFamily();

    s.appendChild(h('h2', 'Свои фразы по темам'));
    s.appendChild(h('p', 'Например «Школа», «Бабушка», «Перед сном». Темы появятся в «Говорить» рядом с «Мои».', 'note'));
    let gname = '', gphrase = '', gl = document.createElement('div');
    const gnf = field('', 'Тема: Школа', function (v) { gname = v.trim(); });
    const gpf = field('', 'Фраза: Можно выйти?', function (v) { gphrase = v.trim(); });
    s.appendChild(gnf); s.appendChild(gpf);
    s.appendChild(btn('Добавить фразу в тему', 'wide', function () {
      if (!gname || !gphrase) return;
      let gs = groups(), g = gs.filter(function (x) { return norm(x.name) === norm(gname); })[0];
      if (!g) { g = { name: gname[0].toUpperCase() + gname.slice(1), list: [] }; gs.push(g); }
      if (!g.list.some(function (p) { return norm(p) === norm(gphrase); })) g.list.push(gphrase);
      store.set('groups', gs); gphrase = ''; gpf.value = ''; paintGroups();
    }));
    s.appendChild(gl);
    function paintGroups() {
      gl.innerHTML = '';
      groups().forEach(function (g) {
        gl.appendChild(h('div', g.name, 'title'));
        g.list.forEach(function (p) {
          const line = row('line'); line.appendChild(h('div', p, 'grow'));
          line.appendChild(sureBtn('✕', 'x', function () {
            const gs = groups(); gs.forEach(function (x) { if (x.name === g.name) x.list = x.list.filter(function (y) { return y !== p; }); });
            store.set('groups', gs.filter(function (x) { return x.list.length; })); paintGroups();
          }));
          gl.appendChild(line);
        });
      });
    }
    paintGroups();

    s.appendChild(h('h2', 'Про меня'));
    s.appendChild(h('p', 'То, что ребёнок покажет и скажет незнакомым одним касанием.', 'note'));
    s.appendChild(field(aboutMe(), 'Меня зовут Миша. Я не говорю, но всё понимаю. Я отвечу на телефоне — подождите, пожалуйста.', function (v) { store.set('about', v.trim()); }, true));

    s.appendChild(h('h2', 'Помощь'));
    const hr = row();
    hr.appendChild(btn('Первая проверка: что заметить', '', function () { checkScreen(function () { adultScreen(); }); }));
    hr.appendChild(btn('Как помочь ребёнку', '', function () { parentsScreen(function () { adultScreen(); }); }));
    hr.appendChild(btn('Первый экран', '', welcomeScreen));
    hr.appendChild(btn('Удобство', '', comfortScreen));
    s.appendChild(hr);
    if (focus === 'choice') setTimeout(function () { ch.scrollIntoView({ block: 'start' }); }, 0);
    render();
  }

  // «Первая проверка»: what to notice in the first ten minutes with the child, and a place for notes to send on
  const CHECK = [
    ['До начала', ['Звук включён, телефон заряжен. Пульк на экране «Домой».', 'Пройдите приветствие вместе: рука, голос, имя.', 'Добавьте 2–3 слова в словарь семьи — лучше с фото.']],
    ['Что заметить', [
      'Понял ли, куда смотреть: на точку на звезде или на текст сверху?',
      'Какую руку и какой палец берёт сам? Держит телефон или кладёт на стол?',
      'Где замер дольше 10 секунд — на каком шаге?',
      'Какие буквы получаются сразу, а где Пульк помогал («Я помог…»)?',
      'Как реагирует на голос телефона: радуется, пугается, повторяет?',
      'Что сказал сам — первое своё слово или выбор?',
      'Когда устал или отвлёкся — на какой минуте?']],
    ['После', ['Запишите заметки ниже — коротко, как есть.', 'Кнопка «Скопировать заметки» — и отправьте их тому, кто делает Пульк. Больше ничего не отправляется.']],
  ];
  function checkScreen(back) {
    const s = begin(); s.scrollTop = 0;
    screenAt('check', function () { checkScreen(back); });
    s.appendChild(btn('← Вернуться', '', back));
    s.appendChild(h('h1', 'Первая проверка'));
    s.appendChild(h('p', '10 минут с ребёнком. Ничего не нужно добиваться — нужно заметить.', 'note'));
    CHECK.forEach(function (sec) {
      s.appendChild(h('h2', sec[0]));
      const ul = document.createElement('ul'); ul.className = 'tips';
      sec[1].forEach(function (x) { ul.appendChild(h('li', x)); }); s.appendChild(ul);
    });
    const notes = field(store.get('notes', ''), 'Заметки: что получилось, где замер, что понравилось…', function (v) { store.set('notes', v); }, true);
    notes.rows = 6; s.appendChild(notes);
    const w = week(), ok = h('div', '', 'ok');
    s.appendChild(btn('Скопировать заметки', 'main wide', function () {
      const t = 'Пульк, первая проверка (' + today() + ')\n' + notes.value + '\n— за неделю сказано ' + w.said + ', новых слов ' + w.fresh + ', капель ' + drops();
      function fallback() { notes.focus(); notes.select(); ok.textContent = 'Выделено — нажмите «Скопировать».'; }
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t).then(function () { ok.textContent = 'Скопировано. Вставьте в сообщение.'; }, fallback); else fallback();
    }));
    s.appendChild(ok);
    render();
  }

  function comfortScreen() {
    const s = begin(); s.scrollTop = 0;
    screenAt('comfort', comfortScreen);
    s.appendChild(btn('← Вернуться', '', function () { adultScreen(); }));
    s.appendChild(h('h1', 'Удобство'));
    const on = comfortSummary();
    s.appendChild(h('p', on.length ? 'Включено: ' + on.join(', ') + '.' : 'Сейчас обычные настройки.', 'note'));
    s.appendChild(btn('Пройти пять вопросов заново', 'wide', function () { document.body.classList.add('welcome'); doorScreen(0, {}); }));
    s.appendChild(h('h2', 'Руки'));
    s.appendChild(handButtons());
    s.appendChild(btn('Проверить руку — 30 секунд', 'wide', function () { handCheck(comfortScreen); }));
    pick(s, 'Защита от случайных касаний', 'guard', 0, [['Нет', 0], ['Слабая', 1], ['Сильная', 2]], 'Короткие задевания и повторные «дребезжащие» касания не станут буквами.');
    pick(s, 'Сгладить дрожь', 'smooth', 0, [['Нет', 0], ['Слегка', 1], ['Сильно', 2]], 'Дрожь сглаживается, когда палец движется медленно; быстрый смах не запаздывает.');
    pick(s, 'Длина смаха', 'tol', 1, [['Короткая', 0.75], ['Обычная', 1], ['Длинная', 1.4]], '«Короткая» — хватит маленького движения; «Длинная» — мелкие подёргивания не считаются.');
    s.appendChild(toggle('Крупная звезда — больше места для пальца', 'bigStar', false, function () { layout(true); }));
    s.appendChild(h('h2', 'Для опытных'));
    jabsToggle(s);
    s.appendChild(h('p', 'Джойстик (и коляски), геймпад и большая клавиатура по Bluetooth работают сами. Джойстик: толчок к букве и назад — буква, короткий — у центра, с поворотом — с поворотом; A — пробел, B — стереть, X — сказать. Клавиатура: цифры справа или стрелки — направления; с 5 — у центра, с соседней — с поворотом.', 'muted'));
    s.appendChild(h('h2', 'Глаза и уши'));
    s.appendChild(toggle('Крупный текст на экране', 'bigText', false, applyLook));
    s.appendChild(toggle('Звук «пульк» на каждую букву', 'tick', true));
    s.appendChild(h('p', 'Звукового компаса и экранного диктора в веб-версии нет — для незрячих лучше Android-версия.', 'muted'));
    s.appendChild(h('h2', 'Внимание'));
    s.appendChild(toggle('Спокойный режим: меньше кнопок, мягче голос, без звука букв', 'calm', false));
    s.appendChild(h('h2', 'Речь'));
    voiceControls(s);
    s.appendChild(toggle('Говорить по словам, пока печатаю', 'words', false));
    s.appendChild(h('h2', 'Помощник рядом'));
    const hr = row(); s.appendChild(hr);
    hr.appendChild(btn('Как помочь ребёнку', '', function () { parentsScreen(comfortScreen); }));
    hr.appendChild(btn('Первая проверка', '', function () { checkScreen(comfortScreen); }));
    s.appendChild(h('h2', 'О программе'));
    s.appendChild(h('p', 'Пульк в браузере. Бесплатно, без рекламы; набранное остаётся на этом устройстве. Свободная программа, GPL-3.0. Словари: FrequencyWords (Hermit Dave), CC BY-SA 4.0. Следующее слово: предложения Tatoeba (tatoeba.org), CC BY 2.0 FR. Фильтр дрожи: 1€ filter (Casiez, Roussel, Vogel, 2012).', 'muted'));
    // «Передать устройство чистым» (as in the app): for a centre where one tablet goes from child to child
    s.appendChild(h('h2', 'Передать устройство'));
    s.appendChild(h('p', 'Сотрёт всё, что Пульк хранит в этом браузере: настройки, фразы, «Про меня», слова семьи и фото, разговор, капли и выученные слова. Как в первый раз.', 'muted'));
    let sure = false, wipe = btn('Передать устройство чистым', 'wide', function () {
      if (!sure) { sure = true; wipe.textContent = 'Точно стереть всё? Нажмите ещё раз'; return; }
      try { Object.keys(localStorage).forEach(function (k) { if (k.indexOf('pulk.') === 0) localStorage.removeItem(k); }); } catch (e) {}
      location.reload();
    });
    s.appendChild(wipe);
    render();
  }

  // «Учимся говорить»: tiny steps, one new thing each, nothing can go wrong. Letters come with a guide on the star:
  // first one straight stroke, then a word of straight strokes, only then a stroke with a turn.
  const LESSON = [
    { t: 'Коснись «Да»', n: 'Телефон скажет «да».', kind: 'tap', want: 'Да' },
    { t: 'Теперь коснись «Нет»', kind: 'tap', want: 'Нет' },
    { t: 'Первая буква — «а»', n: 'Смотри на звезду внизу: бежит точка. Проведи пальцем так же — вверх. Начинать можно где угодно на звезде.', kind: 'guide', want: 'а' },
    { t: 'Слово «нет»', n: 'Три буквы — три движения. Точка показывает каждое.', kind: 'guide', want: 'нет' },
    { t: 'Выбери «Хочу пить»', kind: 'pick', want: 'Хочу пить' },
    { t: 'Слово «мама»', n: 'Буква «м» — с поворотом: веди вправо и в конце поверни вверх. Точка покажет.', kind: 'guide', want: 'мама' },
    { t: 'Покажи фразу маме', n: 'Нажми «Показать» — фраза станет крупной.', kind: 'show', want: 'Привет' },
    { t: 'Своё слово', n: 'Напечатай, что хочешь, и нажми «Сказать» — над вкладками.', kind: 'free' },
  ];
  let step = 0;
  // one letter more, less or different: still the same word
  function almost(a, b) { a = norm(a); b = norm(b); if (a === b) return true; if (Math.abs(a.length - b.length) > 1) return false; let d = 0, i = 0, j = 0; while (i < a.length && j < b.length) { if (a[i] === b[j]) { i++; j++; continue; } if (++d > 1) return false; if (a.length > b.length) i++; else if (b.length > a.length) j++; else { i++; j++; } } return d + (a.length - i) + (b.length - j) <= 1; }
  function lessonScreen() {
    const s = begin();
    screenAt('lesson', lessonScreen);
    if (step >= LESSON.length) {
      store.set('lessonDone', true);
      s.appendChild(h('h1', 'Ты умеешь говорить с Пульком'));
      s.appendChild(h('p', 'Печатай — телефон скажет. «Сказать» — над вкладками или ⏎ в углу звезды. Быстрые ответы и фразы — во вкладке «Говорить».'));
      s.appendChild(btn('Ещё раз', 'wide', function () { step = 0; lessonScreen(); }));
      s.appendChild(btn('Для взрослого рядом', 'wide', function () { parentsScreen(lessonScreen); }));
      render(); return;
    }
    const st = LESSON[step];
    s.appendChild(dots(LESSON.length, step, 'шаг ' + (step + 1) + ' из ' + LESSON.length));
    if (step === 0) {
      const pc = h('div', '', 'card');
      pc.appendChild(h('div', 'Взрослому рядом: как помочь — на одну минуту чтения.', 'muted'));
      pc.appendChild(btn('Для взрослого рядом', 'wide', function () { parentsScreen(lessonScreen); }));
      s.appendChild(pc);
    }
    s.appendChild(h('h1', st.t));
    if (st.n) s.appendChild(h('p', st.n, 'note'));
    let ok = h('div', '', 'ok'), done = false;
    function success() { if (done) return; done = true; const n = addDrop(); ok.style.color = ''; ok.textContent = 'Получилось. +1 капля — в пруду ' + n; setTimeout(function () { if (tab === 'lesson' && LESSON[step] === st) { step++; lessonScreen(); } }, 1300); }
    if (st.kind === 'tap') {
      const r = row();
      (step === 0 ? ['Да'] : ['Да', 'Нет']).forEach(function (w) { const b = btn(w, w === st.want ? 'main' : '', function () { say(w); if (w === st.want) success(); }); b.className += ' big'; r.appendChild(b); });
      s.appendChild(r);
    }
    if (st.kind === 'guide') {
      text = ''; lang = 'ru'; symbols = false; shiftOnce = false; lastFix = null;
      s.appendChild(textBox(''));
      guided = { word: st.want, i: 0, miss: 0, note: function (m) { ok.style.color = 'var(--muted)'; ok.textContent = m; }, done: function () { say(text.trim()); success(); } };
      showGuide(st.want[0]);
    }
    if (st.kind === 'free') {
      text = ''; s.appendChild(textBox('Печатай звездой'));
      onEnter = function () { fixLastWord(); const t = text.trim(); if (!t) return; say(t); remember(t); success(); };
      chipAction = { label: 'Сказать', fn: function () { onEnter(); } };
    }
    if (st.kind === 'pick') ['Хочу есть', 'Хочу пить', 'Мне холодно'].forEach(function (p) { s.appendChild(btn(p, 'wide', function () { say(p); if (p === st.want) success(); })); });
    if (st.kind === 'show') { s.appendChild(h('h1', st.want)); s.appendChild(btn('Показать', 'main wide', function () { showBig(st.want); afterBig = success; })); }
    s.appendChild(ok); render();
  }

  // «Пузыри»: a letter in a bubble; the right stroke pops it with a «пульк». No time, no losing: a bubble waits.
  // Big letters first, then the ones at the centre, then the turns; letters this hand misses come more often.
  let game = null;
  function poppedAll() { return store.get('popped', 0); }
  function gameLevel() { const n = poppedAll(); return n < 15 ? 0 : n < 40 ? 1 : 2; }
  function pool() {
    const kinds = [[0], [0, 3], [0, 3, 1, 2]][gameLevel()], out = [];
    for (let p = 0; p < 8; p++) kinds.forEach(function (k) { const c = D.RU[p][k]; if (c && /[а-я]/.test(c)) out.push(c); });
    return out;
  }
  function nextLetter(prev) {
    let st = store.get('letters', {}), ls = pool().filter(function (c) { return c !== prev; }), sum = 0;
    const ws = ls.map(function (c) { const v = own(st, c) || [0, 0], w = 1 + 3 * v[1] / (v[0] + v[1] + 1); sum += w; return w; });
    let r = Math.random() * sum;
    for (let i = 0; i < ls.length; i++) { r -= ws[i]; if (r <= 0) return ls[i]; }
    return ls[ls.length - 1];
  }
  const ROUND = 10;
  function gameScreen() {
    const s = begin(); lang = 'ru'; symbols = false; shiftOnce = false; text = '';
    screenAt('game', gameScreen);
    const lv = gameLevel();
    game = { letter: nextLetter(''), miss: 0, n: 0, level: lv };
    s.appendChild(h('h1', 'Пузыри'));
    s.appendChild(h('p', ['Крупные буквы: прямой смах.', 'Добавились буквы у центра: туда и обратно.', 'Все буквы: и с поворотом.'][lv] + ' Пузырь ждёт — спешить некуда.', 'note'));
    const d = h('div', '', 'dots'); s.appendChild(d);
    const area = h('div', '', 'pond-area'), bub = h('div', '', 'bubble-l'); area.appendChild(bub); s.appendChild(area);
    const ok = h('div', '', 'ok'); s.appendChild(ok);
    s.appendChild(btn('К тренировкам', 'wide', function () { theme = -1; drillsScreen(); }));
    function paint() {
      d.textContent = ''; const b = document.createElement('b');
      for (let i = 0; i < ROUND; i++) b.textContent += i < game.n ? '●' : '○';
      d.appendChild(b); d.appendChild(document.createTextNode(game.n + ' из ' + ROUND));
      bub.textContent = game.letter; bub.className = 'bubble-l';
      if (game.level === 0) showGuide(game.letter); else showGuide(null);   // at first the way is shown at once
    }
    game.hit = function () {
      const st = store.get('letters', {}), v = own(st, game.letter) || [0, 0]; v[0]++; st[game.letter] = v; store.set('letters', st);
      store.set('popped', poppedAll() + 1); tick();
      bub.className = 'bubble-l pop'; ok.style.color = ''; ok.textContent = ''; game.n++; game.miss = 0;
      const g = game;
      setTimeout(function () {
        if (game !== g) return;
        if (g.n < ROUND) { g.letter = nextLetter(g.letter); paint(); return; }
        showGuide(null); game = null;
        ok.textContent = ROUND + ' пузырей! +1 капля — в пруду ' + addDrop() + (gameLevel() > g.level ? '. Дальше — новые буквы.' : '');
        area.innerHTML = ''; area.appendChild(btn('Ещё раунд', 'main wide', gameScreen));
      }, 380);
    };
    game.wrong = function (got) {
      missed(game.letter); game.miss++;
      bub.className = 'bubble-l wobble'; void bub.offsetWidth; bub.className = 'bubble-l wobble';
      ok.style.color = 'var(--muted)';
      ok.textContent = got ? 'Это «' + got + '». Пузырь «' + game.letter + '» — смотри, как бежит точка.' : 'Веди пальцем по звезде, как бежит точка.';
      showGuide(game.letter);
    };
    paint(); render();
  }
  function gameStroke(t) {
    if (game.words) { const last = t.jabs && t.jabs.finish(); if (last) game.add(jab(table(), last).v); game.lift(); return; }
    const s = t.slot || resolve(table(), t.pts); if (s.petal >= 0 && s.v === game.letter) game.hit(); else game.wrong(s.petal >= 0 ? s.v : ''); }

  // «Пузыри-слова»: a word in a bubble, popped by typing it whole with «Толчки» — one touch, the finger lifted checks it.
  // Whatever the mode in the settings, here the touch is cut into jabs. No time, no losing: a wrong word only wobbles.
  function poppedWords() { return store.get('poppedWords', 0); }
  function wordLevel() { const n = poppedWords(); return n < 10 ? 0 : n < 25 ? 1 : 2; }
  function nextWord(prev) { const ws = P.jabWords(D.RU, wordLevel()).filter(function (w) { return w !== prev; }); return ws[Math.floor(Math.random() * ws.length)]; }
  const WROUND = 5;
  function howJab(ch) { const f = strokeFor(ch); return !f ? '' : f.kind === 'c' ? 'толчок к ней подальше и назад' : f.kind === 'b' ? 'короткий толчок и назад' : 'толчок с поворотом и назад'; }
  function wordGameScreen() {
    const s = begin(); lang = 'ru'; symbols = false; shiftOnce = false; text = '';
    screenAt('wgame', wordGameScreen);
    const lv = wordLevel();
    game = { words: true, word: nextWord(''), got: '', n: 0, level: lv, busy: false };
    s.appendChild(h('h1', 'Пузыри-слова'));
    s.appendChild(h('p', ['Крупные буквы.', 'Плюс короткие толчки.', 'Все буквы.'][lv] + ' Слово — одним касанием, потом подними палец.', 'note'));
    const d = h('div', '', 'dots'); s.appendChild(d);
    const ok = h('div', '', 'ok'); s.appendChild(ok);   // above the bubble: the hint must be seen above the star
    const area = h('div', '', 'pond-area words'), bub = h('div', '', 'bubble-l word'); area.appendChild(bub); s.appendChild(area);
    const other = btn('Другое слово', 'wide', function () { if (game && !game.busy) { game.word = nextWord(game.word); game.got = ''; ok.textContent = ''; paint(); } });
    s.appendChild(other);
    s.appendChild(btn('К тренировкам', 'wide', function () { theme = -1; drillsScreen(); }));
    function letters() {   // the word, its letters lit as they come: right ones in colour, a wrong one crossed
      bub.textContent = '';
      for (let i = 0; i < game.word.length; i++) {
        const sp = document.createElement('span'); sp.textContent = game.word[i];
        if (i < game.got.length) sp.className = game.got[i] === game.word[i] ? 'got' : 'bad';
        bub.appendChild(sp);
      }
    }
    function count(n) {
      d.textContent = ''; const b = document.createElement('b');
      for (let i = 0; i < WROUND; i++) b.textContent += i < n ? '●' : '○';
      d.appendChild(b); d.appendChild(document.createTextNode(n + ' из ' + WROUND));
    }
    function paint() { count(game.n); bub.className = 'bubble-l word'; letters(); }
    game.start = function () { if (game.busy) return; game.got = ''; letters(); };
    game.add = function (v) { if (game.busy) return; tick(); game.got += v; letters(); };
    game.lift = function () {
      if (game.busy || !game.got) return;   // a tap is nothing
      if (game.got === game.word) {
        store.set('poppedWords', poppedWords() + 1);
        bub.className = 'bubble-l word pop'; ok.style.color = ''; ok.textContent = ''; game.n++; game.busy = true; count(game.n);
        const g = game;
        setTimeout(function () {
          if (game !== g) return;
          g.busy = false;
          if (g.n < WROUND) { g.word = nextWord(g.word); g.got = ''; paint(); return; }
          game = null; other.remove();
          ok.textContent = WROUND + ' слов! +1 капля — в пруду ' + addDrop() + (wordLevel() > g.level ? '. Дальше — новые буквы.' : '');
          area.innerHTML = ''; area.appendChild(btn('Ещё раунд', 'main wide', wordGameScreen));
        }, 380);
        return;
      }
      let i = 0; while (i < game.got.length && game.got[i] === game.word[i]) i++;
      bub.className = 'bubble-l word'; void bub.offsetWidth; bub.className = 'bubble-l word wobble';
      ok.style.color = 'var(--muted)';
      ok.textContent = 'Вышло «' + game.got + '». ' + (i < game.word.length ? 'Буква «' + game.word[i] + '» — ' + howJab(game.word[i]) + '. ' : 'Лишняя буква в конце. ') + 'Пузырь ждёт.';
    };
    paint(); render();
  }

  // «Тренировки»: everyday scenes; type or choose; any answer is said
  let theme = -1, at = 0;
  function themes() { return D.themes.concat(drops() >= 15 && D.play ? [D.play] : []); }
  function fits(a, task) { if (!task.keys.length) return !!a.trim(); const n = norm(a); return task.keys.some(function (k) { return n.indexOf(norm(k)) >= 0; }) || almost(a, task.model); }
  function drillsScreen() {
    const s = begin();
    const th0 = theme, at0 = at; screenAt(theme < 0 ? 'drills' : 'drill', function () { theme = th0; at = at0; drillsScreen(); });
    if (theme < 0) {
      s.appendChild(h('h1', 'Тренировки'));
      s.appendChild(h('p', 'Короткие жизненные ситуации. Читаешь — отвечаешь: печатаешь звездой или выбираешь. Телефон говорит ответ.', 'muted'));
      const done = store.get('drillsDone', {});
      const gc = h('div', '', 'card choice');
      gc.appendChild(h('div', 'Пузыри — игра на буквы', 'title'));
      gc.appendChild(h('div', 'Лопай пузыри смахами. Трудные буквы попадаются чаще. Без времени и без проигрыша.', 'muted'));
      gc.appendChild(btn('Играть', 'main wide', gameScreen));
      s.appendChild(gc);
      const wc = h('div', '', 'card choice');
      wc.appendChild(h('div', 'Пузыри-слова — для «Толчков»', 'title'));
      wc.appendChild(h('div', 'В пузыре слово. Лопай его целиком, одним касанием: толчок к букве и назад, не отрывая пальца. Без времени и без проигрыша.', 'muted'));
      wc.appendChild(btn('Играть', 'main wide', wordGameScreen));
      s.appendChild(wc);
      themes().forEach(function (t, i) {
        const c = h('div', '', 'card');
        c.appendChild(h('div', (done[t.title] ? '✓ ' : '') + t.title, 'title')); c.appendChild(h('div', t.about, 'muted'));
        c.appendChild(btn(done[t.title] ? 'Ещё раз' : 'Начать', 'wide', function () { theme = i; at = 0; drillsScreen(); }));
        s.appendChild(c);
      });
      if (drops() < 15) s.appendChild(h('p', 'На 15 каплях откроется тема «Игра».', 'muted'));
      render(); return;
    }
    const th = themes()[theme];
    if (at >= th.tasks.length) {
      const dd = store.get('drillsDone', {}); dd[th.title] = 1; store.set('drillsDone', dd);
      s.appendChild(h('h1', '«' + th.title + '» — получилось'));
      s.appendChild(btn('К темам', 'main wide', function () { theme = -1; drillsScreen(); })); render(); return;
    }
    let task = th.tasks[at], tries = 0, moving = false;
    s.appendChild(dots(th.tasks.length, at, th.title + ' · ' + (at + 1) + ' из ' + th.tasks.length));
    s.appendChild(h('h1', task.scene));
    text = ''; s.appendChild(textBox('Напечатай ответ звездой'));
    const ok = h('div', '', 'ok');
    function answer(a) {
      if (moving || !a.trim()) return; say(a);
      const good = fits(a, task);
      if (good || ++tries >= 2) {
        moving = true; ok.style.color = '';
        ok.textContent = good ? 'Получилось. +1 капля — в пруду ' + addDrop() : 'Можно и так: «' + task.model + '». Дальше.';
        const cur = th.tasks[at];
        setTimeout(function () { if (tab === 'drills' && themes()[theme] === th && th.tasks[at] === cur) { at++; drillsScreen(); } }, good ? 1100 : 2400);
      } else { ok.style.color = 'var(--muted)'; ok.textContent = 'Сказано «' + a + '». Можно и так: «' + task.model + '».'; }
      text = ''; render();
    }
    onEnter = function () { fixLastWord(); answer(text.trim()); };
    chipAction = { label: 'Сказать', fn: onEnter };
    s.appendChild(h('p', '…или выбери:', 'muted'));
    const picks = row('picks'); s.appendChild(picks);   // two columns: the screen above the star is short
    task.choices.forEach(function (c) { picks.appendChild(btn(c, '', function () { answer(c); })); });
    s.appendChild(ok); s.appendChild(btn('К темам', 'wide', function () { theme = -1; drillsScreen(); }));
    render();
  }

  // «Для взрослого рядом»: most children start with a parent beside them; a minute of reading so the help helps
  const PARENTS = [
    ['Перед началом', [
      'Включите звук. Сядьте рядом, сбоку, а не напротив: так вы видите экран так же, как ребёнок.',
      'Телефон держит ребёнок или он лежит на столе. Палец — его.',
      'Пяти минут достаточно. Заканчивайте, пока ещё интересно.']],
    ['Как помогать', [
      'Покажите движение один раз своим пальцем над звездой — и уберите руку. Не водите его пальцем: тогда это сделал не он.',
      'Подождите 5–10 секунд, прежде чем подсказывать. Тишина — время подумать.',
      'Ошибок здесь нет. Не та буква просто не печатается, а точка на звезде показывает ещё раз. После трёх попыток Пульк поможет сам.',
      'Хвалите за попытку, коротко и спокойно: «Ты напечатал». Громкая похвала многим детям неприятна.']],
    ['Первый разговор', [
      'Начните с выбора. «Говорить» → «Предложить выбор»: спросите вслух и напишите «сок, вода». Ребёнок коснётся — телефон скажет. Так разговор получается сразу, ещё до того, как буквы стали лёгкими.',
      'В «Говорить» → «Фразы» → «Близким» — слова для вас: «Я тебя люблю», «Обними меня», «Посмотри!». Покажите их: речь — это не только просьбы.',
      'Пульк поправляет промахи звезды: «маьа» станет «мама». Если поправил не так — ⌫ сразу после пробела вернёт как было, и Пульк запомнит это слово.']],
    ['Когда ребёнок говорит', [
      'Отвечайте на сказанное как на настоящие слова. Сказал «нет» — это «нет»; уважайте его, когда это возможно. Так ребёнок узнаёт, что слова работают.',
      'Не экзаменуйте («Напиши, что это»). Предлагайте настоящий выбор: «Сок или воду?»',
      'Говорите и сами через Пульк: напечатайте и скажите ребёнку что-нибудь. Он увидит, как этим пользуются, — без требования повторить.']],
    ['Если что-то не так', [
      'Устал, расстроился, отворачивается — остановитесь. Вернётесь потом: капли в пруду не пропадают.',
      'Левой рукой удобнее: «Говорить» → «Настройки и помощь» → «Удобство».',
      'Рука дрожит или задевает экран: там же — защита от случайных касаний, сглаживание дрожи, длина смаха. Можно подключить джойстик или большую клавиатуру.',
      'Голос звучит плохо: Настройки iPhone → Универсальный доступ → Устная речь → Голоса → Русский, скачайте улучшенный голос.',
      'Чтобы ребёнок случайно не выходил со страницы: Настройки → Универсальный доступ → Гид-доступ. Включите, откройте Пульк и трижды нажмите боковую кнопку.',
      'Добавьте Пульк на экран «Домой»: в Safari «Поделиться» → «На экран „Домой“». Без адресной строки места больше.']],
    ['Что дальше', [
      'Урок → «Тренировки» по ситуациям (просьбы, самочувствие, окружение) → «Говорить» в жизни. Каждая получившаяся фраза в уроке и тренировках — капля в пруду.',
      'Раз в пару недель сохраняйте копию: «Настройки и помощь» → «Сохранить копию». Из копии всё восстанавливается и на другом телефоне.',
      'Всё набранное остаётся на этом телефоне: Пульк ничего никуда не отправляет. Голос — встроенный в телефон; если в списке голосов есть пометка «через интернет», такой голос отправляет фразы своему сервису, и Пульк о нём предупредит.']],
  ];
  function parentsScreen(back) {
    const s = begin(); s.scrollTop = 0;
    screenAt('parents', function () { parentsScreen(back); });
    s.appendChild(btn('← Вернуться', '', back));
    s.appendChild(h('h1', 'Для взрослого рядом'));
    s.appendChild(h('p', 'Пульк — способ говорить: ребёнок печатает одним пальцем, телефон произносит. Заранее ничего знать не нужно.', 'note'));
    PARENTS.forEach(function (sec) {
      s.appendChild(h('h2', sec[0]));
      const ul = document.createElement('ul'); ul.className = 'tips';
      sec[1].forEach(function (x) { ul.appendChild(h('li', x)); }); s.appendChild(ul);
    });
    s.appendChild(btn('Понятно — вернуться', 'main wide', back));
    render();
  }

  function pondScreen() {
    const s = begin(), n = drops();
    screenAt('pond', pondScreen);
    s.appendChild(h('h1', 'Мой пруд'));
    s.appendChild(h('div', 'Капель: ' + n, 'ok'));
    const w = Math.min(window.innerWidth - 32, 420), hh = 200, cx = w / 2, cy = hh / 2, r = hh / 2 - 6;
    let colours = ['var(--accent)', '#4CAF7D', '#E0A030', '#8E7CC3', '#E57373'], art = '';
    for (let i = 0; i < Math.min(n, 120); i++) { const a = i * 2.39996, rr = Math.sqrt(i / 120) * (r - 10); art += '<circle cx="' + (cx + Math.cos(a) * rr * 1.4).toFixed(1) + '" cy="' + (cy + Math.sin(a) * rr).toFixed(1) + '" r="5" fill="' + (n >= 5 ? colours[i % 5] : 'var(--accent)') + '" opacity="' + (0.6 + (i * 37 % 100) / 250) + '"/>'; }
    if (n >= 30) [[-0.55, -0.35], [0.5, 0.4]].forEach(function (f) { const x = cx + f[0] * r * 1.4, y = cy + f[1] * r; art += '<ellipse cx="' + x + '" cy="' + y + '" rx="14" ry="7" fill="#E0A030"/><polygon points="' + (x + 12) + ',' + y + ' ' + (x + 24) + ',' + (y - 8) + ' ' + (x + 24) + ',' + (y + 8) + '" fill="#E0A030"/>'; });
    const glow = n >= 50 && new Date().getHours() >= 18;
    s.insertAdjacentHTML('beforeend', '<svg width="' + w + '" height="' + hh + '" aria-hidden="true"><ellipse cx="' + cx + '" cy="' + cy + '" rx="' + r * 1.4 + '" ry="' + r + '" fill="' + (glow ? 'var(--press)' : 'var(--panel)') + '" stroke="var(--line)"/>' + art + '</svg>');
    s.appendChild(h('p', 'Каждый получившийся шаг урока или тренировки — капля. Капли только копятся.', 'muted'));
    D.pond.forEach(function (m) { s.appendChild(h('div', (n >= m[0] ? '✓ ' : '○ ') + m[0] + ' капель — ' + m[1], n >= m[0] ? '' : 'muted')); });
    render();
  }

  const SCREENS = { speak: function () { speakScreen(true); }, lesson: function () { step = 0; lessonScreen(); }, drills: function () { theme = -1; drillsScreen(); }, pond: pondScreen,
    settings: function () { tab = 'speak'; adultScreen(); } };   // the adult's screen belongs to «Говорить»: its «назад» returns there
  // the tab that is lit: «Говорить» again when the settings send one back to it
  function pressTab(name) { document.querySelectorAll('#tabs button').forEach(function (x) { x.setAttribute('aria-pressed', x.dataset.tab === name); }); }
  document.querySelectorAll('#tabs button').forEach(function (b) {
    b.onclick = function () {
      tab = b.dataset.tab; $('screen').scrollTop = 0; if (tab !== 'speak') letSleep();
      document.querySelectorAll('#tabs button').forEach(function (x) { x.setAttribute('aria-pressed', x === b); });
      SCREENS[tab]();
    };
  });
  window.addEventListener('resize', function () { layout(); });
  if (window.matchMedia) { const mq = matchMedia('(prefers-color-scheme: dark)'); if (mq.addEventListener) mq.addEventListener('change', function () { layout(true); }); }
  applyLook();
  layout(true);
  // the welcome comes once; those who already used Пульк go straight on
  if (!store.get('welcomed', false) && !store.get('lessonDone', false) && !store.get('drops', 0)) welcomeScreen();
  else document.querySelector('#tabs button[data-tab="' + (store.get('lessonDone', false) ? 'speak' : 'lesson') + '"]').click();
  // for checks on a computer
  window.PULK_WEB = { resolve: resolve, table: table, spot: spot, PH: PH, hit: hit, text: function () { return text; }, lastSaid: function () { return lastSaid; },
    words: function () { return words(); }, backupData: backupData, applyBackup: applyBackup, voices: voices, userWords: userWords, geo: function () { return { K: K, CX: CX, CY: CY, W: W, left: left }; }, screens: function () { return visited.map(function (e) { return e.name; }); } };
})();
