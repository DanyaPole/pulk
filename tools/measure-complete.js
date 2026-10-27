// Offline benchmark. Usage: node tools/measure-complete.js [report.json]
// next_*.txt contain ranked bigrams, NOT sentences or counts. Use them as the frozen model;
// accuracy requires actual continuations, so evaluate every >=2-word prefix of real ready phrases.
// Replay measures lookup coverage (optimistic); leave-one-phrase-out excludes the target from sources.
const fs = require('fs'), path = require('path'), vm = require('vm');
const root = path.join(__dirname, '..'), box = { window: {} };
vm.createContext(box);
for (const f of ['data.js', 'stopwords.js', 'core.js']) vm.runInContext(fs.readFileSync(path.join(root, 'web', f), 'utf8'), box);
const D = box.window.PULK, P = box.window.PulkCore;
const fold = s => P.fold(s.toLowerCase());
function tokens(phrase) {
  const out = phrase.trim().replace(/[.!?…]+$/u, '').split(/\s+/u).map(w => w.replace(/^[,;:«»“”"()]+|[,;:«»“”"()]+$/gu, '').toLowerCase());
  return out.every(w => /^\p{L}+(?:['’-]\p{L}+)*$/u.test(w)) ? out : null;
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
function measure(mode, profile) {
  let evaluated = 0, proposed = 0, correct = 0, exactRemainder = 0, elapsedMs = 0;
  const falseExamples = [];
  for (const target of phrases) {
    const ws = tokens(target);
    const source = mode === 'heldOut' ? phrases.filter(p => p !== target) : phrases;
    // No personal phrases in this repository: do not fabricate personal history or train on test prefixes.
    const c = new P.PhraseCompleter(models.ru, profile, true, () => [], source);
    for (let i = D.completion.MIN_CONTEXT; i < ws.length; i++) {
      evaluated++;
      const start = process.hrtime.bigint(), completion = c.suggest(ws.slice(0, i));
      elapsedMs += Number(process.hrtime.bigint() - start) / 1e6;
      if (!completion) continue;
      proposed++;
      const expected = ws.slice(i, i + completion.tail.length).map(fold);
      if (JSON.stringify(completion.tail.map(fold)) === JSON.stringify(expected)) {
        correct++;
        if (completion.tail.length === ws.length - i) exactRemainder++;
      } else if (falseExamples.length < 5) falseExamples.push({ prefix: ws.slice(0, i).join(' '), proposed: completion.text, real: ws.slice(i).join(' ') });
    }
  }
  return { mode, profile, evaluated, proposed, correct, false: proposed - correct,
    coveragePercent: percent(proposed, evaluated), precisionPercent: percent(correct, proposed),
    falseAmongProposalsPercent: percent(proposed - correct, proposed), falseAmongAllPercent: percent(proposed - correct, evaluated),
    exactRemainder, meanSuggestMs: +(elapsedMs / evaluated).toFixed(3), falseExamples };
}
const result = {
  method: 'Real ready-phrase prefixes, >=2 completed words; correct = the entire proposed 1–4 word tail matches the next real words. Replay includes target; heldOut removes it. Unweighted by real usage. Ranked bigrams are model data, not invented reference sentences.',
  limitations: 'Only Russian full phrases exist in this source set; English model loaded but accuracy not measured. No personal history available. Shared prefixes may have multiple valid meanings: another valid continuation is still a mismatch to this target. No independent natural-language test corpus or phone timing.',
  corpusRows, uniqueReadyPhrases: phrases.length,
  results: ['replay', 'heldOut'].flatMap(mode => ['write', 'speak'].map(profile => measure(mode, profile)))
};
const output = JSON.stringify(result, null, 2) + '\n';
if (process.argv[2]) fs.writeFileSync(path.resolve(process.argv[2]), output);
process.stdout.write(output);
