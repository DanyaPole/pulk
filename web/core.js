// Пульк в браузере — the pure core: the star's geometry and recogniser (as Pulse.kt), «Толчки» (Pulse.Jabs),
// words with stroke-slip correction (Words.kt) and the 1€ filter (Pulse.OneEuro). No page, no storage: tested in
// Node by tools/test-pulk-web.js on every build of the site. app.js takes all of it from window.PulkCore.
(function (root) {
  'use strict';
  const DEG = Math.PI / 180;
  function own(o, k) { return Object.prototype.hasOwnProperty.call(o, k) ? o[k] : 0; }

  // ---- the star (as Pulse.kt) ----
  const R = 96, PAD = 158, OUTER = 300, P_MIN = 16, P_TURN = 14, HOLD_MS = 420;
  const PH = [262 * Math.cos(135 * DEG), 262 * Math.sin(135 * DEG)];
  const KINDS = ['c', 'l', 'r', 'b'];
  const SPOT = { c: [52, 0], l: [86, 14], r: [86, -14], b: [24, 0] };
  const ALT = { 'е': 'ё', 'ь': 'ъ', '?': '!', '.': '…', '-': '—' };
  function spot(p, k) { const s = SPOT[k], a = (p * 45 + s[1]) * DEG; return [PH[0] + s[0] * Math.cos(a), PH[1] + s[0] * Math.sin(a)]; }
  function petalOf(x, y) { return ((Math.round(Math.atan2(y, x) / (Math.PI / 4)) % 8) + 8) % 8; }
  function slot(t, p, kind) { const i = KINDS.indexOf(kind), v = t[p][i]; return v ? { petal: p, kind: kind, v: v } : { petal: p, kind: 'c', v: t[p][0] }; }
  const SPACE = { petal: -1, kind: 's', v: ' ' };
  function tap(t, x, y) {
    const d = Math.hypot(x - PH[0], y - PH[1]);
    if (d < 15 || d > R + 10) return SPACE;
    let best = null, bd = 1e9;
    for (let p = 0; p < 8; p++) for (let i = 0; i < 4; i++) { if (!t[p][i]) continue; const s = spot(p, KINDS[i]), dd = Math.hypot(x - s[0], y - s[1]); if (dd < bd) { bd = dd; best = { petal: p, kind: KINDS[i], v: t[p][i] }; } }
    return best;
  }
  function resolve(t, pts) {
    const ox = pts[0], oy = pts[1], n = pts.length / 2;
    function px(i) { return pts[2 * i] - ox; } function py(i) { return pts[2 * i + 1] - oy; }
    let far = 0, fi = 0;
    for (let i = 1; i < n; i++) { const d = Math.hypot(px(i), py(i)); if (d > far) { far = d; fi = i; } }
    if (far < P_MIN) return tap(t, ox, oy);
    const ex = px(n - 1), ey = py(n - 1), endD = Math.hypot(ex, ey);
    if (far >= 24 && endD < far * 0.5) return slot(t, petalOf(px(fi), py(fi)), 'b');
    let l = endD || 1, nx = -ey / l, ny = ex / l, dev = 0, ci = 0;
    for (let j = 1; j < n - 1; j++) { const dj = Math.abs(px(j) * nx + py(j) * ny); if (dj > dev) { dev = dj; ci = j; } }
    if (dev >= Math.max(7, 0.14 * l)) {
      let cx = px(ci), cy = py(ci), dt = Math.atan2(ey - cy, ex - cx) - Math.atan2(cy, cx);
      dt = Math.atan2(Math.sin(dt), Math.cos(dt));
      if (Math.abs(dt) >= 130 * DEG) return slot(t, petalOf(cx, cy), 'b');
      if (Math.abs(dt) >= 30 * DEG && Math.hypot(ex - cx, ey - cy) >= P_TURN) return slot(t, petalOf(cx, cy), dt > 0 ? 'l' : 'r');
    }
    return slot(t, petalOf(ex, ey), 'c');
  }
  // service keys: the corner is space and ⏎, ⌫ up the right edge, the rare keys along the bottom edge; 🔊 says it
  const KEYS = [
    { fn: 'space', r0: 30, r1: PAD, a0: 91, a1: 150 }, { fn: 'enter', r0: 30, r1: PAD, a0: 150, a1: 179 },
    { fn: 'bksp', r0: PAD, r1: OUTER, a0: 91, a1: 110 },
    { fn: 'shift', r0: PAD, r1: 194, a0: 160, a1: 179 }, { fn: 'lang', r0: 194, r1: 230, a0: 160, a1: 179 },
    { fn: 'sym', r0: 230, r1: 266, a0: 160, a1: 179 }, { fn: 'say', r0: 266, r1: OUTER, a0: 160, a1: 179 },
  ];
  function hit(x, y) {
    let r = Math.max(Math.hypot(x, y), 30), a = Math.atan2(y, x) / DEG; if (a < 0) a += 360;
    if (r >= PAD && (r >= OUTER || (a >= 110 && a < 160))) return null;
    a = Math.min(Math.max(a, 91.01), 178.99);
    for (let i = 0; i < KEYS.length; i++) { const k = KEYS[i]; if (r >= k.r0 && (r < k.r1 || k.r1 === OUTER) && a >= k.a0 && a < k.a1) return k; }
    return null;
  }


  // ---- words (as Words.kt): a letter may differ from the typed one only the way star strokes get confused —
  // the same petal with another gesture, or the neighbouring petal with the same gesture. Frequency decides. ----
  function fold(s) { return s.replace(/ё/g, 'е'); }
  function shape(w, typed) {
    if (typed.length > 1 && typed === typed.toUpperCase() && typed !== typed.toLowerCase()) return w.toUpperCase();
    return typed[0] && typed[0] !== typed[0].toLowerCase() ? w[0].toUpperCase() + w.slice(1) : w;
  }
  // the stop list (stopwords.js): a test for words never to offer — whole words, roots at the start (also after a
  // prefix) and, for English, parts anywhere
  function Stop(s) {
    s = s || {};
    const f = function (a) { return (a || []).map(function (w) { return fold(w.toLowerCase()); }); };
    const words = Object.create(null), roots = f(s.roots), prefixes = [''].concat(f(s.prefixes)), parts = f(s.parts);
    f(s.words).forEach(function (w) { words[w] = true; });
    return function (w) {
      w = fold(w.toLowerCase());
      if (words[w]) return true;
      for (let i = 0; i < parts.length; i++) if (w.indexOf(parts[i]) >= 0) return true;
      for (let i = 0; i < prefixes.length; i++) {
        const p = prefixes[i]; if (w.lastIndexOf(p, 0) !== 0) continue;
        for (let j = 0; j < roots.length; j++) if (w.lastIndexOf(roots[j], p.length) === p.length) return true;
      }
      return false;
    };
  }
  function never() { return false; }

  function Words(list, t, bad) {
    bad = bad || never;
    const words = [], folded = [], byFirst = Object.create(null), rank = Object.create(null), pos = Object.create(null);
    list.forEach(function (w) {
      w = w.trim().toLowerCase(); if (!w || bad(w)) return;
      const f = fold(w), i = words.length; words.push(w); folded.push(f);
      (byFirst[f[0]] || (byFirst[f[0]] = [])).push(i); if (!(f in rank)) rank[f] = i;
    });
    for (let p = 0; p < 8; p++) for (let k = 0; k < 4; k++) { const c = t[p][k]; if (c && c.length === 1) pos[c] = p * 4 + k; }
    if ('е' in pos) pos['ё'] = pos['е']; if ('ь' in pos) pos['ъ'] = pos['ь'];
    const NEAR = Object.create(null);
    Object.keys(pos).forEach(function (a) {
      NEAR[a] = Object.create(null);
      Object.keys(pos).forEach(function (b) {
        const pa = pos[a], pb = pos[b], d = ((pa >> 2) - (pb >> 2) + 8) % 8;
        if ((pa >> 2) === (pb >> 2) || ((pa & 3) === (pb & 3) && (d === 1 || d === 7))) NEAR[a][b] = true;
      });
    });
    function near(a, b) { const m = NEAR[a]; return !!(m && m[b]); }
    function allowance(n) { return n <= 2 ? 0 : n <= 5 ? 1 : 2; }
    let miss = 0;
    // rank: a dictionary index (its frequency order), or for the person's own words a count given as -count;
    // own words weigh more than in the app's keyboard: in a conversation, one's names and words come first
    function score(t, w, allow, rank) {
      if (w.length < t.length) return NaN;
      let m = 0;
      for (let j = 0; j < t.length; j++) { const a = t[j], b = w[j]; if (a === b) continue; if (!near(a, b) || ++m > allow) return NaN; }
      miss = m; const rest = w.length - t.length;
      return (rank >= 0 ? Math.log(rank + 20) : Math.log(20 - 400 / rank)) + m * 3 + rest * 0.12 + (rest === 0 ? -1 : 0);
    }
    function firsts(c, allow) { const out = []; if (byFirst[c]) out.push(byFirst[c]); if (allow > 0) for (const f in byFirst) if (f !== c && near(c, f)) out.push(byFirst[f]); return out; }
    // a stop-list word typed on purpose is left as typed: never offered, never "corrected" into another word
    this.known = function (w, user) { const f = fold(w.toLowerCase()); return f in rank || own(user, f) >= 2 || bad(f); };
    this.suggest = function (typed, n, user) {
      const t = fold(typed.toLowerCase()), best = [], allow = allowance(t.length);
      function offer(key, word, sc) {
        if (best.length === n && sc >= best[n - 1].sc) return;
        for (let i = 0; i < best.length; i++) if (best[i].key === key) { if (sc < best[i].sc) best.splice(i, 1); else return; break; }
        best.push({ key: key, word: word, sc: sc }); best.sort(function (a, b) { return a.sc - b.sc; }); if (best.length > n) best.pop();
      }
      firsts(t[0], allow).forEach(function (idx) { for (let i = 0; i < idx.length; i++) { const sc = score(t, folded[idx[i]], allow, idx[i]); if (sc === sc) offer(folded[idx[i]], words[idx[i]], sc); } });
      for (const w in user) { const cnt = user[w]; if (cnt >= 1 && !bad(w)) { const sc = score(t, w, allow, -cnt); if (sc === sc) offer(w, w, sc); } }
      return best.map(function (b) { return shape(b.word, typed); });
    };
    this.correction = function (typed, user) {
      if (typed.length < 2 || this.known(typed, user)) return null;
      let t = fold(typed.toLowerCase()), allow = allowance(t.length), best = null;
      function consider(w, rank, original) {
        if (w.length !== t.length) return;
        const sc = score(t, w, allow, rank); if (sc !== sc || miss === 0) return;
        if (!best || sc < best.sc) best = { word: original, sc: sc };
      }
      firsts(t[0], allow).forEach(function (idx) { for (let i = 0; i < idx.length; i++) consider(folded[idx[i]], idx[i], words[idx[i]]); });
      for (const w in user) if (user[w] >= 2 && !bad(w)) consider(w, -user[w], w);
      return best && shape(best.word, typed);
    };
  }

  // ---- «Толчки» (as Pulse.kt): a whole word without lifting the finger. One touch is cut into jabs — from home
  // out towards a petal and back; each return home is a letter ----
  const JAB_OUT = 14, JAB_HOME = 10, JAB_SHORT = 30;
  function Jabs(ax, ay) { this.ax = ax; this.ay = ay; this.cur = []; this.far = 0; this.settling = false; this.hx = 0; this.hy = 0; this.best = 0; }
  Jabs.prototype.add = function (x, y) {
    if (this.settling) {   // after a jab the finger is still coming back: home settles where it comes closest to the old home
      const d0 = Math.hypot(x - this.hx, y - this.hy);
      if (d0 <= this.best + 0.8) { if (d0 < this.best) this.best = d0; this.ax = x; this.ay = y; return null; }
      this.settling = false;
    }
    const d = Math.hypot(x - this.ax, y - this.ay);
    if (!this.cur.length) { if (d < JAB_OUT) return null; this.cur.push(this.ax, this.ay); }
    this.cur.push(x, y); if (d > this.far) this.far = d;
    if (d < Math.max(JAB_HOME, this.far * 0.4)) {
      const r = this.cur; this.cur = []; this.far = 0;
      this.settling = true; this.hx = this.ax; this.hy = this.ay; this.best = d; this.ax = x; this.ay = y;
      return r;
    }
    return null;
  };
  Jabs.prototype.finish = function () { if (!this.cur.length) return null; const r = this.cur; this.cur = []; this.far = 0; return r; };
  // the letter of one jab: its way out decides, as a «Пульс» stroke would (straight, or with a turn)
  function jab(t, pts) {
    let ox = pts[0], oy = pts[1], n = pts.length / 2, far = 0, fi = 1;
    for (let i = 1; i < n; i++) { const d = Math.hypot(pts[2 * i] - ox, pts[2 * i + 1] - oy); if (d > far) { far = d; fi = i; } }
    let s = resolve(t, pts.slice(0, (fi + 1) * 2));
    if (s.petal < 0 || s.kind === 'b') s = slot(t, petalOf(pts[2 * fi] - ox, pts[2 * fi + 1] - oy), 'c');
    if (s.kind === 'c' && far < JAB_SHORT) s = slot(t, s.petal, 'b');
    return s;
  }

  // «Пузыри» for «Толчки»: simple words to pop with one touch each. Like the letter game, the big letters first
  // (one straight jab), then the short jabs, then the jabs with a turn; a level gives only words its letters can type
  const JAB_WORDS = ['нос', 'сон', 'рот', 'сто', 'три', 'рис', 'она', 'они', 'оса', 'нет', 'торт', 'сено', 'нота', 'роса', 'нора', 'тесто', 'сирена',
    'шар', 'нож', 'щит', 'щи', 'жена', 'шина', 'отец', 'цена', 'это', 'шарф', 'жара', 'наш',
    'да', 'мама', 'папа', 'дом', 'кот', 'мяч', 'сок', 'суп', 'хлеб', 'вода', 'лук', 'чай', 'мир', 'лес', 'день', 'ночь', 'утро', 'рука', 'небо', 'друг', 'кошка', 'книга', 'привет', 'спасибо'];
  function jabWords(t, level) {
    const kinds = [[0], [0, 3], [0, 3, 1, 2]][level], ok = {};
    for (let p = 0; p < 8; p++) kinds.forEach(function (k) { if (t[p][k]) ok[t[p][k]] = 1; });
    return JAB_WORDS.filter(function (w) { for (let i = 0; i < w.length; i++) if (!ok[w[i]]) return false; return true; });
  }

  // ---- the 1€ filter (Casiez, Roussel, Vogel, CHI 2012), one coordinate ----
  function alpha(cut, dt) { const tau = 1 / (2 * Math.PI * cut); return 1 / (1 + tau / dt); }
  function OneEuro(minCut, beta) { this.minCut = minCut; this.beta = beta; this.x0 = NaN; this.dx0 = 0; this.t0 = 0; }
  OneEuro.prototype.f = function (x, t) {
    if (this.x0 !== this.x0) { this.x0 = x; this.t0 = t; return x; }
    const dt = Math.max(1, t - this.t0) / 1000; this.t0 = t;
    const dx = (x - this.x0) / dt;
    this.dx0 += alpha(1, dt) * (dx - this.dx0);
    this.x0 += alpha(this.minCut + this.beta * Math.abs(this.dx0), dt) * (x - this.x0);
    return this.x0;
  };

  // ---- the drop in the centre leans after the finger: a small share of the way, never further than max ----
  const LEAN = 0.15, LEAN_MAX = 8;
  function lean(dx, dy, max) {
    const x = dx * LEAN, y = dy * LEAN, d = Math.hypot(x, y), m = max === undefined ? LEAN_MAX : max, f = d > m ? m / d : 1;
    return [x * f, y * f];
  }

  root.PulkCore = { DEG: DEG, own: own, R: R, PAD: PAD, OUTER: OUTER, P_MIN: P_MIN, P_TURN: P_TURN, HOLD_MS: HOLD_MS, PH: PH, KINDS: KINDS, SPOT: SPOT, ALT: ALT, SPACE: SPACE, spot: spot, petalOf: petalOf, slot: slot, tap: tap, resolve: resolve, KEYS: KEYS, hit: hit, fold: fold, shape: shape, Stop: Stop, Words: Words, JAB_OUT: JAB_OUT, JAB_HOME: JAB_HOME, JAB_SHORT: JAB_SHORT, Jabs: Jabs, jab: jab, JAB_WORDS: JAB_WORDS, jabWords: jabWords, alpha: alpha, OneEuro: OneEuro, LEAN: LEAN, LEAN_MAX: LEAN_MAX, lean: lean };
})(typeof window !== 'undefined' ? window : globalThis);
