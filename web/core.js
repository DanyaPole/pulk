// Пульк в браузере — the pure core: the star's geometry and recogniser (as Pulse.kt), «Толчки» (Pulse.Jabs),
// words with stroke-slip correction (Words.kt) and the 1€ filter (Pulse.OneEuro). No page, no storage: tested in
// Node by tools/test-web.js on every build of the site. app.js takes all of it from window.PulkCore.
(function (root) {
  'use strict';
  const DEG = Math.PI / 180;
  function own(o, k) { return Object.prototype.hasOwnProperty.call(o, k) ? o[k] : 0; }

  // ---- the star (as Pulse.kt) ----
  const R = 96, PAD = 158, OUTER = 300, P_MIN = 16, P_TURN = 14, HOLD_MS = 420;
  const START_R = 12; // ignore settling inside this circle; Steady has already applied tol
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
    let start = 1;
    while (Math.hypot(px(start), py(start)) < START_R) start++;
    // Anchor = the first three samples outside the circle, projected onto it; one sample is too noisy.
    let ax = 0, ay = 0;
    for (let j = start; j < Math.min(n, start + 3); j++) if (Math.hypot(px(j), py(j)) >= START_R) { ax += px(j); ay += py(j); }
    const sd = Math.hypot(ax, ay) || 1, sx = ax * START_R / sd, sy = ay * START_R / sd;
    const ex = px(n - 1) - sx, ey = py(n - 1) - sy, endD = Math.hypot(px(n - 1), py(n - 1));
    // Tap/return distances stay relative to the touch to preserve short out-and-back strokes.
    if (far >= 24 && endD < far * 0.5) return slot(t, petalOf(px(fi) - sx, py(fi) - sy), 'b');
    let l = Math.hypot(ex, ey) || 1, nx = -ey / l, ny = ex / l, dev = 0, ci = 0;
    for (let j = start; j < n - 1; j++) {
      if (Math.hypot(px(j), py(j)) < START_R) continue;
      const dj = Math.abs((px(j) - sx) * nx + (py(j) - sy) * ny);
      if (dj > dev) { dev = dj; ci = j; }
    }
    if (dev >= Math.max(7, 0.14 * l)) {
      let cx = px(ci) - sx, cy = py(ci) - sy, dt = Math.atan2(ey - cy, ex - cx) - Math.atan2(cy, cx);
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

  function jabPetal(pts) {
    let far = 0, fi = 2;
    for (let i = 2; i < pts.length; i += 2) { const d = Math.hypot(pts[i] - pts[0], pts[i + 1] - pts[1]); if (d > far) { far = d; fi = i; } }
    return petalOf(pts[fi] - pts[0], pts[fi + 1] - pts[1]);
  }
  function PetalIndex(dict, t) {
    const letters = Object.create(null); let index = null;
    let personalSignature = null, personal = Object.create(null);
    t.forEach((a, p) => a.forEach(c => { if (c.length === 1) letters[c] = p; }));
    if (Object.prototype.hasOwnProperty.call(letters, 'е')) letters['ё'] = letters['е'];
    if (Object.prototype.hasOwnProperty.call(letters, 'ь')) letters['ъ'] = letters['ь'];
    this.key = function (word) {
      if (!word) return null;
      let k = ''; for (const c of word.toLowerCase()) { if (!Object.prototype.hasOwnProperty.call(letters, c)) return null; k += letters[c]; } return k;
    };
    this.candidates = function (key, user) {
      if (!/^[0-7]+$/.test(key)) return [];
      if (!index) { index = Object.create(null); dict.forEach(w => { const k = this.key(w); if (k) (index[k] || (index[k] = [])).push(w); }); }
      // Counts may change on every word; directions change only when a word is added or removed.
      const names = Object.keys(user || {}), signature = JSON.stringify(names);
      if (signature !== personalSignature) {
        personal = Object.create(null);
        names.forEach(w => { const k = this.key(w); if (k) (personal[k] || (personal[k] = [])).push(w); });
        personalSignature = signature;
      }
      const mine = (personal[key] || []).filter(w => own(user, w) > 0).sort((a, b) => user[b] - user[a] || (a < b ? -1 : a > b ? 1 : 0));
      return Array.from(new Set(mine.concat(index[key] || [])));
    };
  }

  function Words(list, t, bad) {
    bad = bad || never;
    const words = [], folded = [], byFirst = Object.create(null), rank = Object.create(null), pos = Object.create(null);
    list.forEach(function (w) {
      w = w.trim().toLowerCase(); if (!w || bad(w)) return;
      const f = fold(w), i = words.length; words.push(w); folded.push(f);
      (byFirst[f[0]] || (byFirst[f[0]] = [])).push(i); if (!(f in rank)) rank[f] = i;
    });
    for (let p = 0; p < 8; p++) for (let k = 0; k < 4; k++) { const c = t[p][k]; if (c && c.length === 1) pos[c] = p * 4 + k; }
    let petals = null;
    this.petalCandidates = function (key, user) {
      if (!petals) petals = new PetalIndex(words, t);
      return petals.candidates(key, user).filter(w => !bad(w));
    };
    // Pair lookup as Words.kt. The bank disables the sentence-start fallback: it is not pair evidence.
    const nextCorpus = Object.create(null);
    this.pairs = Object.create(null);
    this.loadNext = function (lines) {
      lines.forEach(l => { const i = l.indexOf('\t'); if (i > 0) nextCorpus[l.slice(0, i)] = l.slice(i + 1).split(' ').filter(Boolean); });
    };
    this.next = function (prev, n = 3, fallbackToStart = true) {
      const p = fold(prev.toLowerCase()), out = [], mine = own(this.pairs, p) || {};
      Object.keys(mine).filter(w => mine[w] >= 2).sort((a, b) => mine[b] - mine[a] || (a < b ? -1 : a > b ? 1 : 0)).forEach(w => { if (!out.includes(w)) out.push(w); });
      (own(nextCorpus, p) || own(nextCorpus, prev.toLowerCase()) || []).forEach(w => { if (!out.includes(w)) out.push(w); });
      if (!out.length && fallbackToStart) out.push(...(own(nextCorpus, '^') || []));
      return out.filter(w => !bad(w)).slice(0, n);
    };
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

  // Phrase-local proposals, as ReplacementBank.kt. Feed completed words; omit the key for firm words.
  // Every i is a stable zero-based WORD index. No voice, storage or text sink; the caller applies the
  // returned accept change to its editor and can compare textWords() after undo. Reset on external edits.
  function ReplacementBank(words, profile = 'speak', enabled = true) {
    const cfg = root.PULK.replacement, phrase = [], refused = new Set(), history = [];
    let active = enabled, locked = -1;
    const normal = w => fold(w.toLowerCase());
    const pairKey = c => JSON.stringify([normal(c.before), normal(c.after)]);
    Object.defineProperty(this, 'enabled', {
      get: () => active,
      set: value => { if (!value) this.endPhrase(); active = value; }
    });
    this.addWord = function (word, petalKey, user) {
      if (!active || !word.trim()) return -1;
      const candidates = petalKey == null ? [] : words.petalCandidates(petalKey, user);
      const seen = new Set([normal(word)]), backups = [];
      // Multiple frequency-ranked candidates are the existing petal engine's only ambiguity signal.
      if (candidates.length && normal(candidates[0]) === normal(word)) {
        for (const c of candidates) {
          if (seen.has(normal(c))) continue;
          seen.add(normal(c)); backups.push(shape(c, word)); if (backups.length === cfg.MAX_ALTERNATIVES) break;
        }
      }
      phrase.push({ word, backups }); return phrase.length - 1;
    };
    this.textWords = () => phrase.map(e => e.word);
    function support(prev, word) {
      const rank = words.next(prev, Number.MAX_SAFE_INTEGER, false).findIndex(w => normal(w) === normal(word));
      return rank < 0 ? 0 : 1 / (rank + 1);
    }
    this.pending = function () {
      if (!active) return [];
      const out = [];
      phrase.forEach((entry, i) => {
        if (i <= locked || !entry.backups.length) return;
        const candidates = [entry.word].concat(entry.backups);
        const scores = candidates.map(c => (i > 0 ? support(phrase[i - 1].word, c) : 0) + (i + 1 < phrase.length ? support(c, phrase[i + 1].word) : 0));
        const best = scores.indexOf(Math.max(...scores)); if (best === 0) return;
        // Thresholds and evidence floor come from Kotlin through build-web.js (heuristic, not probability).
        const confidence = scores[best] / (cfg.EVIDENCE_FLOOR + scores.reduce((a, b) => a + b, 0));
        if (confidence <= (profile === 'write' ? cfg.WRITE_THRESHOLD : cfg.SPEAK_THRESHOLD)) return;
        const after = candidates[best], change = { index: i, before: entry.word, after,
          context: phrase.slice(Math.max(0, i - 1), i + 2).map((e, j) => j + Math.max(0, i - 1) === i ? after : e.word).join(' ') };
        if (!refused.has(pairKey(change))) out.push(change);
      });
      return out;
    };
    this.count = () => this.pending().length;
    const find = i => Number.isInteger(i) ? this.pending().find(c => c.index === i) : null;
    this.listenText = function (i) { const c = find(i); return c ? c.before + ' → ' + c.after + '; ' + c.context : null; };
    this.accept = function (i) {
      const c = find(i); if (!c) return null;
      history.push({ index: i, previous: phrase[i], refusals: [] }); phrase[i] = { word: c.after, backups: [] }; return c;
    };
    function refuse(changes) {
      const keys = Array.from(new Set(changes.map(pairKey))); if (!keys.length) return;
      keys.forEach(k => refused.add(k)); history.push({ index: -1, previous: null, refusals: keys });
    }
    this.keep = function (i) { const c = find(i); if (!c) return false; refuse([c]); return true; };
    this.keepAll = () => refuse(this.pending());
    this.undo = function () {
      const action = history.pop(); if (!action) return false;
      if (action.previous) phrase[action.index] = action.previous;
      action.refusals.forEach(k => refused.delete(k)); return true;
    };
    this.lock = function (upToIndex) {
      if (!active || !Number.isInteger(upToIndex)) return;
      locked = Math.max(locked, upToIndex);
      for (let j = history.length - 1; j >= 0; j--) if (history[j].previous && history[j].index <= locked) history.splice(j, 1);
    };
    // All phrase state, including declined replacement pairs and undo, stays local until this call.
    this.endPhrase = function () { phrase.length = 0; refused.clear(); history.length = 0; locked = -1; };
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

  // «Пузыри» for «Толчки»: short words; accepted letters survive lifting. Like the letter game, the big letters first
  // (one straight jab), then the short jabs, then the jabs with a turn; a level gives only words its letters can type
  const JAB_WORDS = root.PULK.jabWords;
  function jabWords(t, level) {
    const kinds = [[0], [0, 3], [0, 3, 1, 2]][level], ok = {};
    for (let p = 0; p < 8; p++) kinds.forEach(function (k) { if (t[p][k]) ok[t[p][k]] = 1; });
    return JAB_WORDS.filter(function (w) { for (let i = 0; i < w.length; i++) if (!ok[w[i]]) return false; return true; });
  }
  function nextJabWord(t, level, stats, prev, r) {
    let ws = jabWords(t, level).filter(w => w !== prev);
    if (!ws.length) ws = jabWords(t, 0).filter(w => w !== prev);
    if (!ws.length) return prev;
    const weights = ws.map(w => 1 + 3 * [...w].reduce((sum, c) => { const v = own(stats, c) || [0, 0]; return sum + v[1] / (v[0] + v[1] + 1); }, 0) / w.length);
    let x = r * weights.reduce((a, b) => a + b, 0);
    for (let i = 0; i < ws.length; i++) { x -= weights[i]; if (x <= 0) return ws[i]; }
    return ws[ws.length - 1];
  }
  function BubbleProgress(word) { this.word = word; this.done = 0; this.misses = 0; }
  Object.defineProperties(BubbleProgress.prototype, {
    complete: { get: function () { return this.done === this.word.length; } },
    next: { get: function () { return this.word[this.done] || null; } }
  });
  BubbleProgress.prototype.add = function (got, help) {
    if (this.complete) return false;
    if (got === this.next) { this.done++; this.misses = 0; return true; }
    this.misses++;
    if (help && this.misses >= 3) { this.done++; this.misses = 0; }
    return false;
  };

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

  root.PulkCore = { DEG: DEG, own: own, R: R, PAD: PAD, OUTER: OUTER, P_MIN: P_MIN, START_R: START_R, P_TURN: P_TURN, HOLD_MS: HOLD_MS, PH: PH, KINDS: KINDS, SPOT: SPOT, ALT: ALT, SPACE: SPACE, spot: spot, petalOf: petalOf, slot: slot, tap: tap, resolve: resolve, KEYS: KEYS, hit: hit, fold: fold, shape: shape, Stop: Stop, Words: Words, JAB_OUT: JAB_OUT, JAB_HOME: JAB_HOME, JAB_SHORT: JAB_SHORT, Jabs: Jabs, jab: jab, JAB_WORDS: JAB_WORDS, jabWords: jabWords, nextJabWord: nextJabWord, BubbleProgress: BubbleProgress, alpha: alpha, OneEuro: OneEuro, LEAN: LEAN, LEAN_MAX: LEAN_MAX, lean: lean };
  root.PulkCore.PetalIndex = PetalIndex;
  root.PulkCore.jabPetal = jabPetal;
  root.PulkCore.ReplacementBank = ReplacementBank;

  // Text typed since a boundary; proposal word indices never double as character offsets.
  function ReplacementInput(words, profile, enabled) {
    this.bank = new ReplacementBank(words, profile, enabled);
    this.text = ''; this.spans = []; this.scanned = 0;
  }
  ReplacementInput.prototype.scan = function (finish, autoStart, key, user) {
    const re = /[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*/gu; re.lastIndex = this.scanned;
    let m;
    while ((m = re.exec(this.text))) {
      const end = m.index + m[0].length;
      if (end === this.text.length && !finish) break;
      this.bank.addWord(m[0], m.index === autoStart ? key : null, user);
      this.spans.push({ start: m.index, end }); this.scanned = end;
    }
  };
  ReplacementInput.prototype.append = function (s, key, user) {
    if (!this.bank.enabled || !s) return;
    if (this.spans.length && this.spans.at(-1).end === this.text.length && /^[\p{L}\p{N}]/u.test(s)) this.endPhrase();
    const start = this.text.length; this.text += s; this.scan(false, start, key, user);
  };
  ReplacementInput.prototype.trim = function (n) {
    if (n > this.text.length - (this.spans.length ? this.spans.at(-1).end : 0)) { this.endPhrase(); return; }
    this.text = this.text.slice(0, this.text.length - n); this.scanned = Math.min(this.scanned, this.text.length);
  };
  ReplacementInput.prototype.finishWord = function () { this.scan(true); };
  ReplacementInput.prototype.rewrite = function () {
    const ws = this.bank.textWords(); let out = '', at = 0;
    this.spans = this.spans.map((s, i) => {
      out += this.text.slice(at, s.start); const start = out.length; out += ws[i]; at = s.end;
      return { start, end: out.length };
    });
    this.text = out + this.text.slice(at); this.scanned = this.spans.length ? this.spans.at(-1).end : 0;
  };
  ReplacementInput.prototype.accept = function (i) { if (!this.bank.accept(i)) return false; this.rewrite(); return true; };
  ReplacementInput.prototype.undo = function () { if (!this.bank.undo()) return false; this.rewrite(); return true; };
  ReplacementInput.prototype.endPhrase = function () { this.bank.endPhrase(); this.text = ''; this.spans = []; this.scanned = 0; };
  root.PulkCore.ReplacementInput = ReplacementInput;
})(typeof window !== 'undefined' ? window : globalThis);
