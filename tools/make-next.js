// Builds the next-word table for suggestions from the Tatoeba sentences (CC BY 2.0 FR, tatoeba.org).
// node tools/make-next.js <lang> <sentences.tsv> <dict.txt> <out.txt>
// Out: one line per word — "word<TAB>next1 next2 …", the words that most often follow it; "^" is the start of a sentence.
const fs = require('fs'), readline = require('readline');
const [lang, tsv, dictFile, out] = process.argv.slice(2);
const dict = new Set(fs.readFileSync(dictFile, 'utf8').split(/\r?\n/).map(w => w.trim().toLowerCase()).filter(Boolean));
const letters = lang === 'ru' ? /[а-яё]+(?:-[а-яё]+)?/g : /[a-z]+(?:'[a-z]+)?/g;
// Tatoeba is full of the same few example names; they are not what people type next
const NAMES = new Set(['том', 'тома', 'тому', 'томом', 'мэри', 'джон', 'сами', 'лейла', 'джим', 'кен', 'tom', 'mary', 'john', 'sami', 'layla', 'ken', 'jim']);
const counts = new Map(); // prev → Map(next → n)
const seen = new Map();   // prev → how often it is followed by anything
function add(a, b) {
  let m = counts.get(a); if (!m) counts.set(a, m = new Map());
  m.set(b, (m.get(b) || 0) + 1); seen.set(a, (seen.get(a) || 0) + 1);
}
let n = 0;
const rl = readline.createInterface({ input: fs.createReadStream(tsv, 'utf8'), crlfDelay: Infinity });
rl.on('line', line => {
  const text = line.split('\t')[2]; if (!text) return;
  n++;
  // a sentence end starts again; inside a sentence commas and dashes do not break the chain
  for (const part of text.toLowerCase().split(/[.!?…;:"«»()]+/)) {
    const words = part.match(letters) || [];
    let prev = '^';
    for (const w of words) {
      if (!dict.has(w) || NAMES.has(w)) { prev = null; continue; }
      if (prev) add(prev, w);
      prev = w;
    }
  }
});
rl.on('close', () => {
  const lines = [];
  for (const [a, m] of counts) {
    if ((seen.get(a) || 0) < 12) continue; // too rare to say what follows it
    const top = [...m].filter(([, c]) => c >= 3).sort((x, y) => y[1] - x[1]).slice(0, 6).map(([w]) => w);
    if (top.length) lines.push(a + '\t' + top.join(' '));
  }
  lines.sort();
  fs.writeFileSync(out, lines.join('\n') + '\n');
  console.log(lang, 'sentences', n, 'words with next', lines.length, 'bytes', fs.statSync(out).size);
});
