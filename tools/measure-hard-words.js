// Offline measure for part 7 (synonyms): which dictionary words are "expensive" to type.
// A word is hard when its petal path ranks 4th or lower among the words sharing that path (the human has to press
// "Другой" more than twice), so no top-3 candidate gives it. Usage: node tools/measure-hard-words.js [lang] [topN]
const fs = require('fs'), path = require('path'), vm = require('vm');
const root = path.join(__dirname, '..'), box = { window: {} };
vm.createContext(box);
for (const f of ['data.js', 'stopwords.js', 'core.js']) vm.runInContext(fs.readFileSync(path.join(root, 'web', f), 'utf8'), box);
const D = box.window.PULK, P = box.window.PulkCore;
const lang = process.argv[2] || "ru", top = +process.argv[3] || 5000, minRank = +process.argv[4] || 3;
const t = lang === 'ru' ? D.RU : D.EN;
const list = fs.readFileSync(path.join(root, 'web', 'dict_' + lang + '.txt'), 'utf8').split(/\r?\n/).filter(Boolean);
const words = new P.Words(list, t, P.Stop(box.window.PULK_STOP));
const seen = new Set(), rows = [];
let weight = 0, hardWeight = 0, hard = 0, n = 0, extra = 0;
// petalCandidates needs the key: build it the same way PetalIndex does
const letters = Object.create(null);
t.forEach((a, p) => a.forEach(c => { if (c.length === 1) letters[c] = p; }));
if ('е' in letters) letters['ё'] = letters['е'];
if ('ь' in letters) letters['ъ'] = letters['ь'];
const keyOf = w => { let k = ''; for (const c of w) { if (!(c in letters)) return null; k += letters[c]; } return k; };
list.slice(0, top).forEach((w, i) => {
  w = w.trim().toLowerCase(); if (!w || seen.has(w)) return; seen.add(w);
  const k = keyOf(w); if (!k) return;
  const cands = words.petalCandidates(k), rank = cands.indexOf(w);
  if (rank < 0) return;
  const z = 1 / (i + 1); weight += z; n++;
  if (rank >= minRank) { hard++; hardWeight += z; extra += rank - 2; rows.push({ w, rank: rank + 1, pos: i + 1, same: cands.length }); }
});
const pct = (a, b) => +(100 * a / b).toFixed(2);
console.log(JSON.stringify({ lang, top, words: n, hardWords: hard, hardShareOfWords: pct(hard, n), hardShareOfZipfMass: pct(hardWeight, weight), extraPressesPerHardWord: hard ? +(extra / hard).toFixed(2) : 0 }));
rows.sort((a, b) => a.pos - b.pos);
console.log('first 40 hard words (by frequency): ' + rows.slice(0, 40).map(r => r.w + '(' + r.rank + ')').join(' '));
fs.writeFileSync(path.join(root, 'docs', 'hard-words-' + lang + '.json'), JSON.stringify(rows.slice(0, 1500)));
