// Deterministic stroke corpus for web/core.js. The same JSON is replayed by tools/test-golden.js
// and, later, by JavaScriptCore. No packages: node tools/make-golden.js
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');
const root = path.join(__dirname, '..');
const SEED = 20261126;
const TABLES = ['RU', 'EN', 'SYM'];

function loadCore() {
  const read = f => fs.readFileSync(path.join(root, f), 'utf8').replace(/\r\n/g, '\n');
  const box = { window: {}, Math, Object, Array, JSON, isNaN };
  box.globalThis = box.window;
  vm.createContext(box);
  for (const f of ['web/data.js', 'web/stopwords.js', 'web/core.js']) vm.runInContext(read(f), box, { filename: f });
  return box.window;
}
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function round(x) { return Math.round(x * 1e6) / 1e6; }
function pack(hit) {
  if (!hit || typeof hit.petal !== 'number' || typeof hit.kind !== 'string') throw new Error('resolve returned ' + JSON.stringify(hit));
  return { petal: hit.petal, kind: hit.kind, v: typeof hit.v === 'string' ? hit.v : null };
}

const { PULK: D, PulkCore: P } = loadCore();
const rng = mulberry32(SEED);
const KINDS = P.KINDS;
const cases = [];

function lerp(a, b, steps, pts, skipFirst) {
  for (let i = skipFirst ? 1 : 0; i <= steps; i++) {
    const t = i / steps;
    pts.push(round(a[0] + (b[0] - a[0]) * t), round(a[1] + (b[1] - a[1]) * t));
  }
  return pts;
}
function ray(deg, radius) {
  const a = deg * Math.PI / 180;
  return [P.PH[0] + Math.cos(a) * radius, P.PH[1] + Math.sin(a) * radius];
}
// 8–12 segments. Straight to the label; a turn leaves the petal ray then ends on the side label;
// out-and-back ends on the centre label so resolve keeps kind «b».
function cleanPoints(p, kind, nSeg) {
  const end = P.spot(p, kind);
  if (kind === 'c') return lerp(P.PH, end, nSeg, []);
  if (kind === 'b') {
    const outN = nSeg - 3;
    return lerp(ray(p * 45, 64), end, 3, lerp(P.PH, ray(p * 45, 64), outN, []), true);
  }
  const outN = Math.max(4, Math.round(nSeg * 0.62));
  const turnN = nSeg - outN;
  if (turnN < 1) throw new Error('no turn segments for ' + kind);
  return lerp(ray(p * 45, 70), end, turnN, lerp(P.PH, ray(p * 45, 70), outN, []), true);
}
function jitterPoints(pts) {
  const out = pts.slice();
  for (let i = 2; i < out.length; i++) out[i] = round(out[i] + (rng() * 2 - 1) * 3);
  return out;
}
function shortTap(x, y) {
  return [round(x), round(y), round(x + 1), round(y - 0.5), round(x - 0.5), round(y + 1)];
}
function add(id, table, points) {
  const hit = P.resolve(D[table], points);
  cases.push({ id: id, table: table, points: points, expect: pack(hit) });
  return cases[cases.length - 1];
}

for (const table of TABLES) {
  for (let p = 0; p < 8; p++) {
    for (let ki = 0; ki < KINDS.length; ki++) {
      const kind = KINDS[ki];
      const nSeg = 8 + ((p + ki * 3) % 5);
      const slot = D[table][p][ki];
      const id = table + '-p' + p + '-' + kind;
      const clean = add(id + '-clean', table, cleanPoints(p, kind, nSeg));
      const steps = clean.points.length / 2 - 1;
      if (steps < 8 || steps > 12) throw new Error(clean.id + ' has ' + steps + ' steps');
      if (typeof slot === 'string' && slot.length && (clean.expect.v !== slot || clean.expect.kind !== kind || clean.expect.petal !== p)) {
        throw new Error(clean.id + ' got ' + JSON.stringify(clean.expect) + ', slot «' + slot + '»');
      }
      add(id + '-jitter', table, jitterPoints(clean.points));
      add(id + '-tap', table, shortTap(P.spot(p, kind)[0], P.spot(p, kind)[1]));
    }
  }
  const centre = add(table + '-centre-tap', table, shortTap(P.PH[0], P.PH[1]));
  const far = add(table + '-far-tap', table, shortTap(P.PH[0] + 180, P.PH[1]));
  const twoStraight = add(table + '-2pt-straight', table, lerp(P.PH, ray(0, 48), 1, []));
  const twoShort = add(table + '-2pt-short', table, lerp(P.PH, ray(45, 8), 1, []));
  const twoLabel = add(table + '-2pt-label', table, lerp(P.PH, P.spot(2, 'c'), 1, []));
  const oneCentre = add(table + '-1pt-centre', table, [round(P.PH[0]), round(P.PH[1])]);
  const oneLabel = add(table + '-1pt-label', table, [round(P.spot(4, 'c')[0]), round(P.spot(4, 'c')[1])]);
  const oneFar = add(table + '-1pt-far', table, [round(P.PH[0] + 180), round(P.PH[1])]);
  for (const c of [centre, far, twoShort, oneCentre, oneFar]) {
    if (c.expect.v !== P.SPACE.v || c.expect.petal !== -1) throw new Error(c.id + ' is not SPACE: ' + JSON.stringify(c.expect));
  }
  if (twoStraight.points.length !== 4 || twoLabel.points.length !== 4) throw new Error(table + ' 2-point stroke has the wrong length');
  if (oneLabel.points.length !== 2 || oneCentre.points.length !== 2) throw new Error(table + ' 1-point input has the wrong length');
  if (twoStraight.expect.petal !== 0 || twoStraight.expect.kind !== 'c') throw new Error(twoStraight.id + ' ' + JSON.stringify(twoStraight.expect));
}

// EN petals 6 and 7 have an empty centre-letter slot. A return that is not the 8–12 step label stroke.
for (const p of [6, 7]) {
  const back = lerp(ray(p * 45, 8), P.PH, 2, lerp(P.PH, ray(p * 45, 50), 4, []), true);
  const c = add('EN-p' + p + '-b-empty', 'EN', back);
  if (D.EN[p][3] !== '') throw new Error('EN petal ' + p + ' kind b is not empty');
  if (c.expect.petal !== p) throw new Error(c.id + ' left its petal: ' + JSON.stringify(c.expect));
}

if (cases.length < 300) throw new Error('only ' + cases.length + ' cases');
const ids = new Set(cases.map(c => c.id));
if (ids.size !== cases.length) throw new Error('duplicate case id');

const doc = { version: 1, seed: SEED, core: 'web/core.js', cases: cases };
const out = path.join(root, 'shared', 'strokes.golden.json');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify(doc, null, 2) + '\n', 'utf8');
console.log('wrote shared/strokes.golden.json: ' + cases.length + ' cases, seed ' + SEED);
