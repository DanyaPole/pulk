// Offline benchmark. Usage: node tools/measure-complete.js [report.json] [--with-chain]
// next_*.txt contain ranked bigrams, NOT sentences or counts. Use them as the frozen model;
// accuracy requires actual continuations, so evaluate every >=2-word prefix of real ready phrases.
// Replay measures lookup coverage (optimistic); leave-one-phrase-out excludes the normalized target.
// --with-chain also enables the fallback in this VM only; shipped files are never modified.
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
const tally = () => ({ evaluated: 0, proposed: 0, correct: 0, exactRemainder: 0, falseExamples: [] });
function summary(t) {
  return { ...t, false: t.proposed - t.correct,
    coveragePercent: percent(t.proposed, t.evaluated), precisionPercent: percent(t.correct, t.proposed),
    falseAmongProposalsPercent: percent(t.proposed - t.correct, t.proposed), falseAmongAllPercent: percent(t.proposed - t.correct, t.evaluated) };
}
function measure(mode, profile) {
  const total = tally(), routes = { phrasePrefix: tally(), chain: tally() };
  let elapsedMs = 0;
  for (const target of phrases) {
    const ws = tokens(target);
    const targetKey = JSON.stringify(ws.map(fold));
    const source = mode === 'heldOut' ? phrases.filter(p => JSON.stringify(tokens(p).map(fold)) !== targetKey) : phrases;
    const sourceTokens = source.map(p => tokens(p).map(fold));
    // No personal phrases in this repository: do not fabricate personal history or train on test prefixes.
    const c = new P.PhraseCompleter(models.ru, profile, true, () => [], source);
    for (let i = D.completion.MIN_CONTEXT; i < ws.length; i++) {
      const prefix = ws.slice(0, i), normalized = prefix.map(fold);
      // Same routing as the engine, including a phrase ending exactly at the prefix.
      const route = sourceTokens.some(s => s.length >= i && normalized.every((w, j) => s[j] === w)) ? 'phrasePrefix' : 'chain';
      const tallies = [total, routes[route]];
      for (const t of tallies) t.evaluated++;
      const start = process.hrtime.bigint(), completion = c.suggest(prefix);
      elapsedMs += Number(process.hrtime.bigint() - start) / 1e6;
      if (!completion) continue;
      const expected = ws.slice(i, i + completion.tail.length).map(fold);
      for (const t of tallies) {
        t.proposed++;
        if (JSON.stringify(completion.tail.map(fold)) === JSON.stringify(expected)) {
          t.correct++;
          if (completion.tail.length === ws.length - i) t.exactRemainder++;
        } else if (t.falseExamples.length < 5) t.falseExamples.push({ prefix: prefix.join(' '), proposed: completion.text, real: ws.slice(i).join(' ') });
      }
    }
  }
  return { mode, profile, chainTails: D.completion.CHAIN_TAILS, ...summary(total),
    meanSuggestMs: total.evaluated ? +(elapsedMs / total.evaluated).toFixed(3) : null,
    routes: Object.fromEntries(Object.entries(routes).map(([name, t]) => [name, summary(t)])) };
}
const shippedChainTails = D.completion.CHAIN_TAILS, results = [];
try {
  for (const chainTails of new Set(process.argv.includes('--with-chain') ? [shippedChainTails, true] : [shippedChainTails])) {
    D.completion.CHAIN_TAILS = chainTails;
    for (const mode of ['replay', 'heldOut']) for (const profile of ['write', 'speak']) results.push(measure(mode, profile));
  }
} finally { D.completion.CHAIN_TAILS = shippedChainTails; }
const result = {
  method: 'Real ready-phrase prefixes, >=2 completed words; correct = the entire proposed 1–4 word tail matches the next real words. Replay includes target; heldOut removes every normalized copy of the target from all phrase sources. Personal sources are empty, model bigrams are frozen. Phrase admission requires the fixed MIN_PREFIX (MIN_PREFIX_OWN for own sources), at least half of every matching source rounded up, and unanimous next words. All >=2-word prefixes stay in the denominator, even when admission rejects them. Routes are separate: phrasePrefix has a source starting with the whole input; chain has none (also counted when disabled). --with-chain repeats the measurement with fallback enabled in an isolated VM. Unweighted by real usage.',
  limitations: 'Only Russian full phrases exist in this source set; English model loaded but accuracy not measured. No personal history available. Shared prefixes may have multiple valid meanings: another valid continuation is still a mismatch to this target. No independent natural-language test corpus or phone timing.',
  corpusRows, uniqueReadyPhrases: phrases.length, shippedChainTails, results
};
const output = JSON.stringify(result, null, 2) + '\n';
const outputPath = process.argv.slice(2).find(arg => arg !== '--with-chain');
if (outputPath) fs.writeFileSync(path.resolve(outputPath), output);
process.stdout.write(output);
