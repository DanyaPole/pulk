// Tests for Пульк в браузере (web/). The pure core (web/core.js) is checked against the same expectations as
// the Android app's tests (PulseTest, WordsTest); the data shared with the app (web/data.js) is checked for
// completeness; the page script is checked to parse. No libraries: node tools/test-web.js
// The site (DanyaPole/duga, tools/build-pages.js) runs this before publishing. The browser version ships the
// app's own dictionaries (web/dict_*.txt, copied by tools/build-web.js); PULK_DICT_DIR can point elsewhere.
const fs = require('fs'), path = require('path'), vm = require('vm');
const root = path.join(__dirname, '..');
const dictDir = process.env.PULK_DICT_DIR || path.join(root, 'web');
// git on Windows may check files out with CRLF: the tests look for text, so line ends are made the same
const read = f => fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
const box = { window: {}, Math, Object, Array, JSON, isNaN };
box.globalThis = box.window;
vm.createContext(box);
for (const f of ['web/data.js', 'web/stopwords.js', 'web/core.js']) vm.runInContext(read(path.join(root, f)), box, { filename: f });
const D = box.window.PULK, P = box.window.PulkCore, bad = P.Stop(box.window.PULK_STOP);

let failed = 0, passed = 0;
function test(name, fn) {
  try { fn(); passed++; } catch (e) { failed++; console.log('  ✗ ' + name + ': ' + (e && e.message || e)); }
}
function eq(a, b, what) { if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error((what ? what + ': ' : '') + 'got ' + JSON.stringify(a) + ', want ' + JSON.stringify(b)); }
function ok(c, what) { if (!c) throw new Error(what || 'not true'); }

// strokes in base units from the star's centre, as the Android tests draw them
const at = (pts) => { const out = []; pts.forEach(p => out.push(P.PH[0] + p[0], P.PH[1] + p[1])); return out; };
const line = (deg, len) => { const a = deg * P.DEG, o = []; for (let i = 0; i <= 6; i++) o.push([Math.cos(a) * len * i / 6, Math.sin(a) * len * i / 6]); return at(o); };
const turn = (deg, sign) => {
  const a = deg * P.DEG, b = (deg + sign * 90) * P.DEG, o = [];
  for (let i = 0; i <= 5; i++) o.push([Math.cos(a) * 50 * i / 5, Math.sin(a) * 50 * i / 5]);
  for (let i = 1; i <= 4; i++) o.push([Math.cos(a) * 50 + Math.cos(b) * 30 * i / 4, Math.sin(a) * 50 + Math.sin(b) * 30 * i / 4]);
  return at(o);
};
const outBack = (deg) => { const a = deg * P.DEG, o = []; for (const r of [0, 20, 40, 55, 40, 20, 5]) o.push([Math.cos(a) * r, Math.sin(a) * r]); return at(o); };

test('a rude word has a kind twin that is itself allowed', () => {
  const soft = box.window.PULK_STOP.soft; ok(soft && soft['дурак'] && soft['дура'], '«дурак», «дура»');
  for (const k in soft) { eq(k, P.fold(k.toLowerCase()), 'key «' + k + '»'); ok(!bad(soft[k]), '«' + soft[k] + '» is not on the stop list'); ok(soft[k] !== k, k); }
});

test('the data shared with the app is whole', () => {
  eq(D.RU.length, 8, 'RU petals'); D.RU.forEach((p, i) => eq(p.length, 4, 'petal ' + i));
  const letters = [].concat(...D.RU).filter(Boolean).join('');
  for (const c of 'абвгдежзийклмнопрстуфхцчшщыьэюя') ok(letters.includes(c), 'missing «' + c + '»');
  ok(D.quick.length >= 6, 'quick replies'); eq(D.day.length, 4, 'parts of the day');
  ok(D.situations.some(s => s[0] === 'Срочно'), '«Срочно»'); ok(D.situations.some(s => s[0] === 'Близким'), '«Близким»');
  ok(D.themes.some(t => t.title === 'Разминка'), '«Разминка»'); ok(D.pond.length >= 4, 'pond');
});

test('the drop leans after the finger a little, never further than its limit', () => {
  eq(P.lean(0, 0), [0, 0], 'no finger, no lean');
  const near = P.lean(20, 0); ok(Math.abs(near[0] - 20 * P.LEAN) < 1e-9 && near[1] === 0, 'a near finger: its share');
  const far = P.lean(300, -400); ok(Math.abs(Math.hypot(far[0], far[1]) - P.LEAN_MAX) < 1e-9, 'a far finger: the limit');
  ok(far[0] > 0 && far[1] < 0, 'towards the finger');
});
test('a straight stroke is the big letter of its petal', () => {
  for (let p = 0; p < 8; p++) eq(P.resolve(D.RU, line(p * 45, 60)).v, D.RU[p][0], 'petal ' + p);
});
test('a stroke with a turn is the small letter on that side', () => {
  for (let p = 0; p < 8; p++) {
    eq(P.resolve(D.RU, turn(p * 45, 1)).v, D.RU[p][1] || D.RU[p][0], 'left of ' + p);
    eq(P.resolve(D.RU, turn(p * 45, -1)).v, D.RU[p][2] || D.RU[p][0], 'right of ' + p);
  }
});
test('out and back is the letter at the centre', () => {
  for (let p = 0; p < 8; p++) eq(P.resolve(D.RU, outBack(p * 45)).v, D.RU[p][3] || D.RU[p][0], 'petal ' + p);
});
test('a touch in the middle is a space, a touch on a letter is that letter', () => {
  eq(P.resolve(D.RU, at([[0, 0]])).petal, -1);
  const s = P.spot(2, 'c'); eq(P.resolve(D.RU, [s[0], s[1]]).v, D.RU[2][0]);
});
test('the service keys are where the thumb expects them', () => {
  const k = (r, a) => (P.hit(r * Math.cos(a * P.DEG), r * Math.sin(a * P.DEG)) || {}).fn;
  eq(k(90, 120), 'space'); eq(k(90, 165), 'enter'); eq(k(230, 100), 'bksp'); eq(k(283, 170), 'say'); eq(k(176, 170), 'shift');
  eq(P.hit(P.PH[0], P.PH[1]), null, 'the star is not a key');
});

test('«Толчки»: each jab out and back is a letter, a short one the centre letter', () => {
  const j = new P.Jabs(0, 0), got = [];
  const push = (deg, len) => { const a = deg * P.DEG; for (let i = 1; i <= 6; i++) { const r = j.add(Math.cos(a) * len * i / 6, Math.sin(a) * len * i / 6); if (r) got.push(P.jab(D.RU, r).v); } for (let i = 5; i >= 0; i--) { const r = j.add(Math.cos(a) * len * i / 6, Math.sin(a) * len * i / 6); if (r) got.push(P.jab(D.RU, r).v); } };
  push(0, 50); push(45, 50); push(90, 22);
  eq(got, [D.RU[0][0], D.RU[1][0], D.RU[2][3]]);
});

test('«Пузыри» for «Толчки»: every word is typed by one touch of jabs, each level has enough words', () => {
  const where = {}; D.RU.forEach((p, i) => p.forEach((c, k) => { if (c) where[c] = [i, k]; }));
  for (const w of P.JAB_WORDS) {
    const j = new P.Jabs(0, 0), got = [], pts = [];
    for (const c of w) {
      ok(where[c], 'no stroke for «' + c + '» in «' + w + '»');
      const [p, k] = where[c], a = p * 45 * P.DEG, len = k === 3 ? 22 : 50, o = [];
      for (let i = 1; i <= 6; i++) o.push([Math.cos(a) * len * i / 6, Math.sin(a) * len * i / 6]);
      if (k === 1 || k === 2) { const b = a + (k === 1 ? 1 : -1) * Math.PI / 2; for (let i = 1; i <= 4; i++) o.push([Math.cos(a) * 50 + Math.cos(b) * 30 * i / 4, Math.sin(a) * 50 + Math.sin(b) * 30 * i / 4]); }
      const [ex, ey] = o[o.length - 1]; for (let i = 5; i >= 0; i--) o.push([ex * i / 6, ey * i / 6]);
      pts.push(...o);
    }
    for (const [x, y] of pts) { const r = j.add(x, y); if (r) got.push(P.jab(D.RU, r).v); }
    const last = j.finish(); if (last) got.push(P.jab(D.RU, last).v);
    eq(got.join(''), w, 'jabs for «' + w + '»');
    ok(!bad(w), '«' + w + '» is on the stop list');
  }
  const n = [0, 1, 2].map(l => P.jabWords(D.RU, l).length);
  ok(n[0] >= 8 && n[1] > n[0] && n[2] === P.JAB_WORDS.length, 'words by level: ' + n);
  ok(P.jabWords(D.RU, 0).every(w => [...w].every(c => D.RU.some(p => p[0] === c))), 'level 0 has only big letters');
});

test('the 1€ filter calms a shaking hand and lets a quick stroke through', () => {
  const f = new P.OneEuro(0.8, 0.02); let maxDev = 0;
  f.f(10, 0);   // the first point passes as it is
  for (let t = 16; t < 1000; t += 16) maxDev = Math.max(maxDev, Math.abs(f.f(10 + (t / 16 % 2 ? 3 : -3), t) - 10));
  ok(maxDev < 3, 'jitter of ±3 kept within ' + maxDev.toFixed(2));
  const g = new P.OneEuro(3, 0.02); let x = 0; for (let t = 0; t <= 200; t += 16) x = g.f(t * 0.5, t);
  ok(x > 70, 'a quick stroke lags too much: ' + x.toFixed(1));
});

// words, with the real dictionary
const dict = read(path.join(dictDir, 'dict_ru.txt')).split(/\r?\n/);
const W = new P.Words(dict, D.RU);
test('a stroke slip in a word is corrected', () => {
  eq(W.correction('маьа', {}), 'мама'); eq(W.correction('нит', {}), 'нет'); eq(W.correction('Маьа', {}), 'Мама');
  eq(W.correction('мама', {}), null, 'a real word stays');
});
test('suggestions: frequent words first, the person\'s own words before them', () => {
  eq(W.suggest('прив', 3, {})[0], 'привет');
  eq(W.suggest('ми', 3, { 'миша': 3 })[0], 'миша');
  eq(W.correction('ьиша', { 'миша': 2 }), 'миша', 'own words are known to the correction');
});
test('words keep the case that was typed', () => { eq(P.shape('привет', 'Пр'), 'Привет'); eq(P.shape('привет', 'ПР'), 'ПРИВЕТ'); });
test('suggestions handle words with «ё» through core Words', () => {
  const words = new P.Words(['ёлка', 'ёлочка', 'ель'], D.RU);
  eq(words.suggest('ёл', 3, {}), ['ель', 'ёлка', 'ёлочка']);
});
test('suggestions handle hyphenated words through core Words', () => {
  const words = new P.Words(['по-русски', 'по-доброму', 'пока'], D.RU);
  eq(words.suggest('по-', 3, {}), ['по-русски', 'по-доброму']);
});
test('suggestions preserve an initial capital through core Words', () => {
  const words = new P.Words(['привет', 'привести'], D.RU);
  eq(words.suggest('При', 2, {}), ['Привет', 'Привести']);
});
test('suggestions handle a one-letter word through core Words', () => {
  const words = new P.Words(['я', 'яблоко'], D.RU);
  eq(words.suggest('я', 2, {}), ['я', 'яблоко']);
});

const appSource = read(path.join(root, 'web/app.js'));

function speechFrom(available, chosen = '', busy = false, supported = true) {
  const spoken = [], timers = [], hint = { hidden: true };
  const ss = { getVoices: () => available, speaking: busy, cancel() {}, speak: u => spoken.push(u) };
  const start = appSource.indexOf("  let lastSaid = ''"), end = appSource.indexOf('  // «Говорить по словам»', start);
  ok(start >= 0 && end > start, 'speech source is present');
  const api = new Function('window', 'speechSynthesis', 'SpeechSynthesisUtterance', 'store', '$', 'setTimeout',
    appSource.slice(start, end) + '\nreturn {say, voices, last: () => lastSaid};')(
    { speechSynthesis: supported ? ss : null }, ss, function (text) { this.text = text; },
    { get: (k, def) => k === 'voice' ? chosen : def }, () => hint, fn => timers.push(fn));
  return { ...api, spoken, hint, flush: () => timers.splice(0).forEach(fn => fn()) };
}

test('speech rejects network and unknown voices, even when saved', () => {
  const local = { name: 'local', lang: 'ru-RU', localService: true };
  for (const localService of [false, undefined, null, 'true']) {
    const network = { name: 'saved', lang: 'ru-RU', localService };
    const s = speechFrom([network, local], network.name);
    s.say('Привет'); eq(s.spoken.length, 1); eq(s.spoken[0].voice, local);
    eq(s.voices(true), [local]);
  }
});

test('without a local voice speech stays silent and keeps the phrase for showing', () => {
  for (const available of [[], [{ name: 'online', lang: 'ru-RU', localService: false }], [{ name: 'english', lang: 'en-US', localService: true }]]) {
    const s = speechFrom(available, 'online');
    s.say('  Хочу пить  '); s.flush();
    eq(s.spoken, []); eq(s.last(), 'Хочу пить'); eq(s.hint.hidden, false);
  }
  const unsupported = speechFrom([], '', false, false);
  unsupported.say('Да'); eq(unsupported.last(), 'Да'); eq(unsupported.spoken, []);
});

test('speech matches the language and the delayed path also uses a local voice', () => {
  const ru = { name: 'ru', lang: 'ru-RU', localService: true }, en = { name: 'en', lang: 'en-US', localService: true };
  const s = speechFrom([ru, en], ru.name, true);
  s.say('Hello'); eq(s.spoken.length, 0); s.flush();
  eq(s.spoken[0].voice, en); eq(s.hint.hidden, true);
  s.say('Ёж'); s.flush(); eq(s.spoken[1].voice, ru);
});
test('left-hand layout puts «Сказать» at the left edge', () => {
  const html = read(path.join(root, 'web/index.html'));
  ok(/body\.left-hand #chips button\.main[^}]*order:\s*-1[^}]*left:\s*0/.test(html), 'left-hand main action rule');
});

// the stop list: children use Пульк too, so rude, obscene and sexual words are never offered
test('the stop list catches rude words and lets harmless look-alikes through', () => {
  for (const w of ['сука', 'Хуй', 'нахуй', 'охуенно', 'пиздец', 'блядь', 'ёбаный', 'отъебись', 'засранец', 'говно', 'жопа', 'шлюха', 'секс', 'трахнуть', 'сдохни', 'порно', 'fuck', 'motherfucker', 'bullshit', 'sexy'])
    ok(bad(w), '«' + w + '» is not caught');
  for (const w of ['ребёнок', 'небо', 'тебе', 'себе', 'себастьян', 'команда', 'мандарин', 'страх', 'страховка', 'хлеб', 'художник', 'похуже', 'колебания', 'учебник', 'требовать', 'психушке', 'хулиган', 'марихуана', 'трахея', 'упорно', 'упорный', 'член', 'дурак', 'cocktail', 'scrap', 'peacock', 'essex', 'hitchcock', 'dickens'])
    ok(!bad(w), '«' + w + '» is caught by mistake');
});
const dictEn = read(path.join(dictDir, 'dict_en.txt')).split(/\r?\n/), WE = new P.Words(dictEn, D.EN, bad);
const Wc = new P.Words(dict, D.RU, bad);
test('no stop-list word is suggested or corrected into, whatever is typed', () => {
  const user = { 'сука': 5, 'жопа': 3, 'миша': 3 };
  let checked = 0;
  [[dict, Wc], [dictEn, WE]].forEach(([list, Ws]) => list.forEach(w => {
    w = w.trim(); if (!w || !bad(w)) return;
    for (let n = 1; n <= w.length; n++) { const got = Ws.suggest(w.slice(0, n), 4, user).filter(bad); eq(got, [], 'after «' + w.slice(0, n) + '»'); checked++; }
    eq(Ws.correction(w, user), null, '«' + w + '» typed on purpose stays');
  }));
  ok(checked > 500, 'the dictionaries hold rude words to check: ' + checked);
  eq(Wc.suggest('ми', 3, user)[0], 'миша', 'own harmless words are still offered');
  eq(Wc.suggest('прив', 3, {})[0], 'привет', 'ordinary words are still offered');
});
test('no stop-list word is offered as the next word', () => {
  const next = Object.create(null);
  read(path.join(root, 'web/next_ru.txt')).split(/\r?\n/).forEach(l => { const i = l.indexOf('\t'); if (i > 0) next[l.slice(0, i)] = l.slice(i + 1).split(' '); });
  const start = appSource.indexOf('  function nextWords(prev) {'), end = appSource.indexOf('  function currentWord', start);
  ok(start >= 0 && end > start, 'nextWords source is present');
  const pairs = { 'ты': { 'сука': 4, 'молодец': 3 } };
  const nextWords = new Function('fold', 'own', 'store', 'next', 'bad', appSource.slice(start, end) + 'return nextWords;')(P.fold, P.own, { get: k => k === 'pairs' ? pairs : {} }, next, bad);
  ok(Object.keys(next).some(k => next[k].some(bad)), 'next_ru.txt holds rude words to check');
  for (const k in next) eq(nextWords(k).filter(bad), [], 'after «' + k + '»');
  eq(nextWords('ты')[0], 'молодец', 'the person\'s harmless pairs still come first');
});

function storeFrom(value) {
  const storage = { getItem: function () { return value; }, setItem: function () {} };
  const start = appSource.indexOf('  const memo = Object.create(null);');
  const end = appSource.indexOf('\n\n  let lang =', start);
  ok(start >= 0 && end > start, 'store source is present');
  return new Function('localStorage', appSource.slice(start, end) + '\nreturn store;')(storage);
}

test('saved settings fall back when storage is damaged or empty', () => {
  for (const saved of ['{broken', '', null]) eq(storeFrom(saved).get('hand', 'right'), 'right', 'saved ' + JSON.stringify(saved));
});

test('empty input is handled', () => {
  eq(W.suggest('', 4, {}), []);
  eq(W.correction('', {}), null);
  eq(P.shape('привет', ''), 'привет');
});

test('very long input is handled', () => {
  const long = 'а'.repeat(100000);
  eq(W.suggest(long, 4, {}), []);
  eq(W.correction(long, {}), null);
  eq(P.shape('привет', long), 'привет');
});

test('the browser has the same dictionaries and next-word pairs as the Android app', () => {
  for (const f of ['dict_ru.txt', 'dict_en.txt', 'next_ru.txt'])
    ok(read(path.join(root, 'web', f)) === read(path.join(root, 'app/src/main/assets', f)), f + ' is the app\'s (run node tools/build-web.js)');
});

test('both dictionaries are kept for use without internet', () => {
  const html = read(path.join(root, 'web/index.html'));
  for (const f of ['dict_ru.txt', 'dict_en.txt', 'next_ru.txt']) ok(html.indexOf("'" + f + "'") >= 0, f + ' is in the offline list of web/index.html');
});

test('the page script parses', () => {
  for (const f of ['web/app.js']) new Function(read(path.join(root, f)));
});

console.log('Пульк в браузере: ' + passed + ' tests, ' + failed + ' failed');
if (failed) process.exit(1);
