// How much heap the word engine keeps, for a keyboard extension capped near 30–48 MB.
// node --expose-gc tools/measure-dict-mem.js
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');
if (typeof global.gc !== 'function') {
  console.error('run with node --expose-gc');
  process.exit(1);
}
const root = path.join(__dirname, '..');
const read = f => fs.readFileSync(f, 'utf8');
const r3 = x => Math.round(x * 1000) / 1000;

function retained() {
  global.gc();
  global.gc();
  const m = process.memoryUsage();
  return { heapMB: m.heapUsed / 1048576, rssMB: m.rss / 1048576 };
}
function medianMs(fn) {
  for (let i = 0; i < 10; i++) fn();
  const xs = new Array(200);
  for (let i = 0; i < 200; i++) {
    const t0 = process.hrtime.bigint();
    fn();
    xs[i] = Number(process.hrtime.bigint() - t0) / 1e6;
  }
  xs.sort((a, b) => a - b);
  return r3((xs[99] + xs[100]) / 2);
}

const marks = [];
function mark(name) {
  const s = retained();
  const prev = marks.length ? marks[marks.length - 1] : s;
  marks.push({ name: name, heapMB: s.heapMB, rssMB: s.rssMB, dHeapMB: s.heapMB - prev.heapMB, dRssMB: s.rssMB - prev.rssMB });
}

mark('baseline');
const box = { window: {}, Math, Object, Array, JSON, isNaN };
box.globalThis = box.window;
vm.createContext(box);
for (const f of ['web/data.js', 'web/stopwords.js', 'web/core.js']) vm.runInContext(read(path.join(root, f)), box, { filename: f });
mark('core+data');

const P = box.window.PulkCore, D = box.window.PULK;
const bad = P.Stop(box.window.PULK_STOP);
let ruList = read(path.join(root, 'web/dict_ru.txt')).split(/\r?\n/);
const wordsRu = new P.Words(ruList, D.RU, bad);
ruList = null;
mark('wordsRu');
let ruNext = read(path.join(root, 'web/next_ru.txt')).split(/\r?\n/);
wordsRu.loadNext(ruNext);
ruNext = null;
mark('nextRu');
let enList = read(path.join(root, 'web/dict_en.txt')).split(/\r?\n/);
const wordsEn = new P.Words(enList, D.EN, bad);
enList = null;
mark('wordsEn');
let enNext = read(path.join(root, 'web/next_en.txt')).split(/\r?\n/);
wordsEn.loadNext(enNext);
enNext = null;
mark('nextEn');
// Words builds PetalIndex on the first petalCandidates call, not in the constructor.
wordsRu.petalCandidates('0123', {});
mark('petalRu');

const prefixes = ['м', 'ма', 'при', 'стол', 'котор'];
const keys = ['0', '01', '012', '0123', '45670'];
const suggestMs = {};
for (const prefix of prefixes) suggestMs[prefix] = medianMs(() => wordsRu.suggest(prefix, 4, {}));
const petalMs = {};
for (const key of keys) petalMs[key] = medianMs(() => wordsRu.petalCandidates(key, {}));

const pad = (s, n) => String(s).padStart(n);
console.log('step         heapMB    rssMB   dHeapMB    dRssMB');
for (const m of marks) {
  console.log(
    m.name.padEnd(12) +
    pad(r3(m.heapMB).toFixed(3), 8) +
    pad(r3(m.rssMB).toFixed(3), 9) +
    pad(r3(m.dHeapMB).toFixed(3), 10) +
    pad(r3(m.dRssMB).toFixed(3), 10)
  );
}
console.log('suggest ms (median of 200, n=4)');
for (const prefix of prefixes) console.log('  ' + prefix + '  ' + suggestMs[prefix].toFixed(3));
console.log('petalCandidates ms (median of 200)');
for (const key of keys) console.log('  ' + key + '  ' + petalMs[key].toFixed(3));
const mem = {
  heapMB: {}, rssMB: {}, deltaHeapMB: {},
  suggestMs: suggestMs, petalMs: petalMs
};
for (const m of marks) {
  mem.heapMB[m.name] = r3(m.heapMB);
  mem.rssMB[m.name] = r3(m.rssMB);
  mem.deltaHeapMB[m.name] = r3(m.dHeapMB);
}
console.log('MEM ' + JSON.stringify(mem));
