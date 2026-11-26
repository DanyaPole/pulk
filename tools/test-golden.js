// Replay shared/strokes.golden.json through web/core.js. node tools/test-golden.js
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');
const root = path.join(__dirname, '..');
const read = f => fs.readFileSync(path.join(root, f), 'utf8').replace(/\r\n/g, '\n');
const box = { window: {}, Math, Object, Array, JSON, isNaN };
box.globalThis = box.window;
vm.createContext(box);
for (const f of ['web/data.js', 'web/stopwords.js', 'web/core.js']) vm.runInContext(read(f), box, { filename: f });
const D = box.window.PULK, P = box.window.PulkCore;

function pack(hit) {
  if (!hit || typeof hit.petal !== 'number' || typeof hit.kind !== 'string') return { petal: null, kind: null, v: null };
  return { petal: hit.petal, kind: hit.kind, v: typeof hit.v === 'string' ? hit.v : null };
}

const doc = JSON.parse(read('shared/strokes.golden.json'));
let failed = 0;
const fails = [];
const cases = Array.isArray(doc.cases) ? doc.cases : [];
for (const c of cases) {
  let got;
  try { got = pack(P.resolve(D[c.table], c.points)); }
  catch (e) { got = { error: String(e && e.message || e) }; }
  if (JSON.stringify(got) !== JSON.stringify(c.expect)) {
    failed++;
    if (fails.length < 8) fails.push(c.id + ' got ' + JSON.stringify(got) + ' want ' + JSON.stringify(c.expect));
  }
}
if (cases.length < 300) {
  failed += 300 - cases.length;
  fails.push('fewer than 300 cases (' + cases.length + ')');
}
for (const line of fails) console.log('  ' + line);
console.log('golden: ' + cases.length + ' cases, ' + failed + ' failed');
process.exit(failed ? 1 : 0);
