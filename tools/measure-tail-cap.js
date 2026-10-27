// Offline: does capping the proposed tail (2 vs 4 words) change precision and words saved? Held-out phrases, as measure-complete.js.
const fs = require('fs'), path = require('path'), vm = require('vm');
const root = path.join(__dirname, '..'), box = { window: {} };
vm.createContext(box);
for (const f of ['data.js', 'stopwords.js', 'core.js']) vm.runInContext(fs.readFileSync(path.join(root, 'web', f), 'utf8'), box);
const D = box.window.PULK, P = box.window.PulkCore;
const fold = s => P.fold(s.toLowerCase());
const bad = P.Stop(box.window.PULK_STOP);
function tokens(phrase) {
  const out = phrase.trim().replace(/[.!?…]+$/u, '').split(/\s+/u).map(w => w.replace(/^[,;:«»“”"()]+|[,;:«»“”"()]+$/gu, '').toLowerCase());
  return out.every(w => /^\p{L}+(?:['’-]\p{L}+)*$/u.test(w)) && !out.some(bad) ? out : null;
}
const unique = new Map();
for (const phrase of D.quick.concat(D.situations.flatMap(s => s[1]), D.day.flatMap(d => d[2]))) {
  const ws = tokens(phrase);
  if (ws) unique.set(JSON.stringify(ws.map(fold)), phrase);
}
const phrases = Array.from(unique.values()), models = {}, corpusRows = {};
for (const lang of ['ru', 'en']) {
  const lines = fs.readFileSync(path.join(root, 'app/src/main/assets', 'next_' + lang + '.txt'), 'utf8').split(/\r?\n/).filter(Boolean);
  corpusRows[lang] = lines.length;
  models[lang] = new P.Words([], lang === 'ru' ? D.RU : D.EN, P.Stop(box.window.PULK_STOP));
  models[lang].loadNext(lines);
}
const percent = (n, d) => d ? +(100 * n / d).toFixed(2) : null;
for (const profile of ['write', 'speak']) {
  const byK = { 1: { p: 0, c: 0, saved: 0 }, 2: { p: 0, c: 0, saved: 0 }, 3: { p: 0, c: 0, saved: 0 }, 4: { p: 0, c: 0, saved: 0 } };
  let ev = 0, prop = 0, lens = 0;
  for (const target of phrases) {
    const ws = tokens(target), tk = JSON.stringify(ws.map(fold));
    const source = phrases.filter(p => JSON.stringify(tokens(p).map(fold)) !== tk);
    const c = new P.PhraseCompleter(models.ru, profile, true, () => [], source);
    for (let i = D.completion.MIN_CONTEXT; i < ws.length; i++) {
      ev++; const comp = c.suggest(ws.slice(0, i)); if (!comp) continue;
      prop++; lens += comp.tail.length;
      const real = ws.slice(i).map(fold), tail = comp.tail.map(fold);
      for (let k = 1; k <= 4; k++) {
        const kk = Math.min(k, tail.length); byK[k].p++;
        if (tail.slice(0, kk).every((w, j) => w === real[j])) { byK[k].c++; byK[k].saved += kk; }
      }
    }
  }
  console.log(profile, JSON.stringify({ evaluated: ev, proposed: prop, meanTail: +(lens / prop).toFixed(2) }));
  for (const k of [1, 2, 3, 4]) console.log('  cap ' + k + ': precision ' + (100 * byK[k].c / byK[k].p).toFixed(1) + '%, words saved per 100 evaluated prefixes ' + (100 * byK[k].saved / ev).toFixed(1));
}
