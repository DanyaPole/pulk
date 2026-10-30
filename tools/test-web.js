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

// Same scenarios as PhraseCompleterTest.kt: proposals never change input or apply punctuation.
const completePrefix = ['мне', 'пожалуйста'];
function completeWords(lines = []) { const w = new P.Words([], D.RU, bad); w.loadNext(lines); return w; }
function completer(own = [], ready = [], profile = 'speak') { return new P.PhraseCompleter(completeWords(), profile, true, () => own, ready); }
test('completion: own phrase is a proposal without a sign', () => {
  const input = ['Мне,', 'пожалуйста,'], c = completer(['Мне, пожалуйста, стакан воды?!…']);
  const result = c.suggest(input); eq(result, { tail: ['стакан', 'воды'], text: 'стакан воды' });
  eq(input, ['Мне,', 'пожалуйста,']); eq(c.suggest(input), result);
  result.tail.push('changed'); eq(c.suggest(input).tail, ['стакан', 'воды']);
});
test('completion: little or ambiguous data stays quiet', () => {
  const c = completer(['мне пожалуйста воды', 'мне пожалуйста чаю']);
  eq(c.suggest(completePrefix), null); eq(c.suggest(['мне']), null); eq(c.suggest([]), null);
  eq(completer().suggest(completePrefix), null);
  eq(completer(['мне пожалуйста воды']).suggest(['пожалуйста', 'воды']), null);
});
test('completion: shared tail stops at doubt and four words', () => {
  eq(completer(['мне пожалуйста стакан воды', 'мне пожалуйста стакан чая']).suggest(completePrefix).tail, ['стакан']);
  eq(completer(['мне пожалуйста один два три четыре пять']).suggest(completePrefix), null);
  const input = completePrefix.concat('один', 'два', 'три');
  eq(completer(['мне пожалуйста один два три четыре пять шесть семь восемь']).suggest(input).tail, ['четыре', 'пять', 'шесть', 'семь']);
});
test('completion: phrase end is evidence and never falls back to pairs', () => {
  eq(completer(['мне пожалуйста', 'мне пожалуйста воды']).suggest(completePrefix), null);
  const c = new P.PhraseCompleter(completeWords(['пожалуйста\tводы', 'воды\tещё']), 'speak', true, () => ['мне пожалуйста воды'], []);
  eq(c.suggest(completePrefix).tail, ['воды']); eq(c.suggest(completePrefix.concat('воды')), null);
});
test('completion: profile threshold and deduplication', () => {
  const ready = ['мне пожалуйста стакан воды'], input = completePrefix.concat('стакан');
  ok(completer([], ready, 'write').suggest(input)); eq(completer([], ready).suggest(input), null);
  eq(completer([], ready.concat(ready)).suggest(input), null); ok(completer(ready, ready).suggest(input));
});
test('completion: ready prefix needs three words and half rounded up', () => {
  const c = completer([], ['один два три четыре пять шесть семь'], 'write');
  eq(c.suggest(['один', 'два', 'три']), null);
  eq(c.suggest(['один', 'два', 'три', 'четыре']).text, 'пять шесть семь');
  const even = completer([], ['один два три четыре пять шесть'], 'write');
  eq(even.suggest(['один', 'два']), null);
  eq(even.suggest(['один', 'два', 'три']).text, 'четыре пять шесть');
});
test('completion: own prefix is softer but still needs half and two words', () => {
  for (const profile of ['write', 'speak']) {
    const phrase = ['мне пожалуйста стакан воды'];
    eq(completer(phrase, [], profile).suggest(completePrefix).text, 'стакан воды');
    eq(completer([], phrase, profile).suggest(completePrefix), null);
    eq(completer(phrase, [], profile).suggest(['мне']), null);
    eq(completer(['мне пожалуйста принеси стакан воды'], [], profile).suggest(completePrefix), null);
    eq(completer(phrase, phrase, profile).suggest(completePrefix).text, 'стакан воды');
  }
});
test('completion: every matching source must pass length without chain fallback', () => {
  const short = 'один два три четыре пять шесть', long = short + ' семь', input = ['один', 'два', 'три'];
  for (const profile of ['write', 'speak']) {
    eq(completer([short], [long], profile).suggest(input), null);
    eq(completer([long], [short], profile).suggest(input), null);
  }
});
test('completion: weighted majority cannot hide a branch or phrase end', () => {
  const endings = ['воды', 'чая', 'сока', 'молока', 'кефира', 'какао', 'компота', 'морса'];
  for (const profile of ['write', 'speak']) {
    const majority = endings.map(w => 'мне пожалуйста стакан ' + w);
    eq(completer(majority.concat('мне пожалуйста кружку кофе'), [], profile).suggest(completePrefix), null);
    const withEnd = endings.map(w => 'мне пожалуйста принеси стакан воды ' + w).concat('мне пожалуйста принеси стакан');
    const input = completePrefix.concat('принеси', 'стакан');
    eq(completer(withEnd.slice(0, -1), [], profile).suggest(input).text, 'воды');
    eq(completer(withEnd, [], profile).suggest(input), null);
    eq(completer(withEnd, [], profile).suggest(completePrefix.concat('принеси')).text, 'стакан');
  }
});
test('completion: passwords inspect neither words nor sources nor input', () => {
  const secret = new Proxy({}, { get() { throw Error('password inspected'); } });
  const c = new P.PhraseCompleter(secret, 'speak', false, () => { throw Error('private source'); });
  eq(c.suggest(secret), null); eq(c.punctuationHints(secret), []);
  const active = completer(['мне пожалуйста воды']); ok(active.suggest(completePrefix));
  active.enabled = false; eq(active.suggest(completePrefix), null); eq(active.punctuationHints(completePrefix), []);
  active.enabled = true; eq(active.suggest(['другой', 'текст']), null);
});
test('completion: punctuation is separate and markers are whole words', () => {
  const c = completer();
  for (const marker of D.completion.QUESTION_MARKERS) eq(c.punctuationHints([marker.toUpperCase(), 'ты'])[0], '?');
  eq(c.punctuationHints(['whoever', 'dish']), ['.', '?', '!']); eq(c.punctuationHints([]), []);
  eq(c.punctuationHints(['как', 'дела?']), []);
});
test('completion: new prefixes stay quiet in both profiles even with unique pairs', () => {
  const w = completeWords(['^\tснова', 'мы\tпочти', 'почти\tуже', 'уже\tдома', 'дома\tвечером ночью']);
  for (const profile of ['write', 'speak']) {
    const c = new P.PhraseCompleter(w, profile, true, () => ['вот мы почти уже дома'], ['сегодня мы почти уже дома']);
    for (const input of [['мы', 'почти'], ['не', 'почти'], ['совсем', 'неизвестно']]) eq(c.suggest(input), null);
  }
});
test('completion: repeated learned pairs do not authorize a new phrase', () => {
  const w = completeWords(['мы\tпочти']); w.pairs['почти'] = { 'дома': 1 };
  for (let count = 1; count <= 3; count++) {
    w.pairs['почти']['дома'] = count;
    for (const profile of ['write', 'speak']) eq(new P.PhraseCompleter(w, profile, true, () => [], []).suggest(['мы', 'почти']), null);
  }
});
test('completion: cycles and pair punctuation cannot extend known phrases', () => {
  const w = completeWords(['мы\tпочти', 'почти\tдома', 'дома\tпочти']);
  for (const profile of ['write', 'speak']) {
    const c = new P.PhraseCompleter(w, profile, true, () => ['мы почти дома!'], []);
    eq(c.suggest(['мы', 'почти']).tail, ['дома']); eq(c.suggest(['мы', 'почти', 'дома']), null);
    eq(new P.PhraseCompleter(w, profile, true, () => [], []).suggest(['мы', 'почти']), null);
    w.loadNext(['дома\tготово!']); eq(c.suggest(['мы', 'почти']).tail, ['дома']);
  }
});
test('completion: unsafe sources and sentence boundaries are not rewritten', () => {
  for (const phrase of ['мне пожалуйста воды. Потом чаю', 'мне пожалуйста 2 таблетки', 'мне пожалуйста сука'])
    eq(completer([phrase]).suggest(completePrefix), null);
  eq(completer(['мне пожалуйста воды. Потом чаю']).suggest(completePrefix.concat('воды')), null);
  const c = completer(['мне пожалуйста воды']);
  eq(c.suggest(['мне.', 'пожалуйста']), null); eq(c.suggest(['', 'пожалуйста']), null);
});
test('completion: fresh own provider, yo and English', () => {
  let own = ['у ёлки тихо', 'could you help me?'];
  const c = new P.PhraseCompleter(completeWords(), 'speak', true, () => own, []);
  eq(c.suggest(['У', 'елки']).text, 'тихо'); eq(c.suggest(['Could', 'you']).text, 'help me');
  own = []; eq(c.suggest(['could', 'you']), null);
});
test('completion: default ready phrases and real corpora', () => {
  for (const lang of ['ru', 'en']) {
    const w = new P.Words([], lang === 'ru' ? D.RU : D.EN, bad);
    w.loadNext(read(path.join(root, 'app/src/main/assets', 'next_' + lang + '.txt')).split('\n'));
    const c = new P.PhraseCompleter(w, 'write'); eq(c.suggest(['zzunknown', 'zzunknown']), null);
    if (lang === 'ru') {
      eq(c.suggest(['подожди', 'я']), null);
      eq(c.suggest(['мне', 'нужно', 'в']).text, 'туалет');
    }
  }
});
test('completion: shared thresholds and question markers come from Kotlin', () => {
  const src = read(path.join(root, 'app/src/main/java/app/pulk/PhraseCompleter.kt'));
  for (const m of src.matchAll(/const val (\w+) = (true|false|[\d.]+)/g)) eq(D.completion[m[1]], JSON.parse(m[2]), m[1]);
  const markers = [...src.match(/val QUESTION_MARKERS = setOf\(([\s\S]*?)\)/)[1].matchAll(/"([^"]+)"/g)].map(m => m[1]);
  eq(D.completion.QUESTION_MARKERS, markers);
});
test('completion: held-out benchmark separates phrase prefixes and diagnostic chains', () => {
  const output = require('child_process').execFileSync(process.execPath, [path.join(root, 'tools/measure-complete.js'), '--with-chain'], { encoding: 'utf8' });
  const report = JSON.parse(output);
  eq(report.shippedChainTails, false); eq(report.results.length, 8);
  for (const row of report.results) {
    const routes = Object.values(row.routes);
    for (const key of ['evaluated', 'proposed', 'correct', 'false', 'exactRemainder'])
      eq(row[key], routes.reduce((sum, r) => sum + r[key], 0), key);
    if (!row.chainTails) eq(row.routes.chain.proposed, 0);
    const other = report.results.find(r => r.mode === row.mode && r.profile === row.profile && r.chainTails !== row.chainTails);
    eq(row.routes.phrasePrefix, other.routes.phrasePrefix);
    if (row.mode === 'heldOut') ok(row.routes.chain.evaluated > 0, 'excluded targets must leave prefixes without a phrase source');
    else eq(row.routes.chain.evaluated, 0);
  }
});
test('completion: diagnostic chain switch still enforces context, cycles and punctuation', () => {
  const shipped = D.completion.CHAIN_TAILS;
  try {
    D.completion.CHAIN_TAILS = true;
    const w = completeWords(['мы\tпочти', 'почти\tдома', 'дома\tпочти']);
    const c = new P.PhraseCompleter(w, 'write', true, () => [], []);
    eq(c.suggest(['мы', 'почти']).tail, ['дома', 'почти']); eq(c.suggest(['не', 'почти']), null);
    w.loadNext(['дома\tготово!']); eq(c.suggest(['мы', 'почти']).tail, ['дома']);
    const speak = new P.PhraseCompleter(w, 'speak', true, () => [], []);
    w.pairs['почти'] = { 'дома': 1 }; eq(speak.suggest(['мы', 'почти']), null);
    w.pairs['почти']['дома'] = 2; eq(speak.suggest(['мы', 'почти']).tail, ['дома']);
  } finally { D.completion.CHAIN_TAILS = shipped; }
});

// Same scenarios as ReplacementBankTest.kt; indices refer to phrase words, not queue positions.
const bankKey = new P.PetalIndex([], D.RU).key('дом');
function bank(lines = ['том\tстоит'], profile = 'speak', enabled = true) {
  const w = new P.Words(['дом', 'том'], D.RU, bad); w.loadNext(lines);
  return new P.ReplacementBank(w, profile, enabled);
}
function bankPair(b) { b.addWord('дом', bankKey); b.addWord('стоит'); }
test('replacement bank: pending is silent and indices are stable', () => {
  const b = bank(); b.addWord('вот'); b.addWord('дом', bankKey); eq(b.count(), 0); b.addWord('стоит');
  const change = { index: 1, before: 'дом', after: 'том', context: 'вот том стоит' };
  eq(b.pending(), [change]); eq(b.listenText(1), 'дом → том; вот том стоит'); eq(b.textWords(), ['вот', 'дом', 'стоит']);
  eq(b.accept(0), null); eq(b.keep(99), false); eq(b.listenText(-1), null);
  const snapshot = b.pending(); snapshot[0].after = 'changed'; snapshot.pop();
  eq(b.pending(), [change]); eq(b.count(), 1); eq(b.accept('1'), null); eq(b.accept(.5), null);
});
test('replacement bank: accept and undo preserve later input', () => {
  const b = bank(); bankPair(b); eq(b.accept(0).after, 'том'); eq(b.count(), 0); eq(b.textWords(), ['том', 'стоит']);
  b.addWord('тут'); eq(b.undo(), true); eq(b.textWords(), ['дом', 'стоит', 'тут']); eq(b.count(), 1); eq(b.undo(), false);
});
test('replacement bank: keep suppresses the pair and undo restores it', () => {
  const b = bank(); bankPair(b); eq(b.keep(0), true); bankPair(b); eq(b.count(), 0);
  eq(b.textWords(), ['дом', 'стоит', 'дом', 'стоит']); eq(b.undo(), true); eq(b.pending().map(p => p.index), [0, 2]);
  ok(b.accept(2)); ok(b.accept(0)); eq(b.undo(), true); eq(b.textWords(), ['дом', 'стоит', 'том', 'стоит']);
  eq(b.undo(), true); eq(b.count(), 2);
});
test('replacement bank: keepAll is one undoable action', () => {
  const b = bank(); bankPair(b); bankPair(b); b.keepAll(); eq(b.count(), 0); b.keepAll();
  eq(b.undo(), true); eq(b.count(), 2); eq(b.undo(), false);
});
test('replacement bank: lock also prevents undoing spoken words', () => {
  const b = bank(); bankPair(b); b.lock(0); eq(b.count(), 0); eq(b.accept(0), null); b.lock(-1); eq(b.count(), 0);
  bankPair(b); eq(b.pending().map(p => p.index), [2]); ok(b.accept(2)); b.lock(2); eq(b.undo(), false); eq(b.textWords()[2], 'том');
  const future = bank(); future.lock(4); bankPair(future); eq(future.count(), 0);
});
test('replacement bank: passwords store nothing and clear history on disable', () => {
  const b = bank(undefined, undefined, false); bankPair(b); eq(b.addWord('secret', bankKey), -1); eq(b.textWords(), []);
  eq(b.count(), 0); eq(b.listenText(0), null); eq(b.accept(0), null); eq(b.keep(0), false); b.keepAll(); eq(b.undo(), false); b.lock(10);
  b.enabled = true; bankPair(b); eq(b.count(), 1); b.accept(0); b.enabled = false; eq(b.textWords(), []); eq(b.undo(), false);
  b.enabled = true; bankPair(b); eq(b.count(), 1);
  const noLookup = new P.ReplacementBank({ petalCandidates() { throw Error('password lookup'); }, next() { throw Error('password pairs'); } }, 'speak', false);
  bankPair(noLookup); eq(noLookup.count(), 0);
});
test('replacement bank: profile threshold, weak evidence and ties', () => {
  const lines = ['том\tсейчас стоит'], write = bank(lines, 'write'), speak = bank(lines); bankPair(write); bankPair(speak);
  eq(write.count(), 1); eq(speak.count(), 0);
  for (const profile of ['write', 'speak']) {
    const small = bank(['том\tсейчас тут стоит'], profile); bankPair(small); eq(small.count(), 0);
    const tie = bank(['дом\tстоит', 'том\tстоит'], profile); bankPair(tie); eq(tie.count(), 0);
  }
});
test('replacement bank: start fallback is not evidence; left context works', () => {
  const w = new P.Words(['дом', 'том'], D.RU, bad); w.loadNext(['^\tстоит']);
  eq(w.next('дом'), ['стоит']); eq(w.next('дом', 3, false), []);
  const b = new P.ReplacementBank(w); bankPair(b); eq(b.count(), 0);
  const left = bank(['вот\tтом']); left.addWord('вот'); left.addWord('дом', bankKey); eq(left.count(), 1); eq(left.pending()[0].context, 'вот том');
});
test('replacement bank: exact words, explicit alternatives and case', () => {
  const b = bank(); b.addWord('дом'); b.addWord('стоит'); eq(b.count(), 0);
  const explicit = bank(['дом\tстоит']); explicit.addWord('том', bankKey); explicit.addWord('стоит'); eq(explicit.count(), 0);
  const caps = bank(); caps.addWord('ДОМ', bankKey); caps.addWord('стоит'); eq(caps.accept(0).after, 'ТОМ'); eq(caps.undo(), true); eq(caps.textWords()[0], 'ДОМ');
  const w = new P.Words(['ёлка', 'елка'], D.RU, bad); w.loadNext(['елка\tстоит']); const m = new P.ReplacementBank(w);
  m.addWord('ёлка', new P.PetalIndex([], D.RU).key('ёлка')); m.addWord('стоит'); eq(m.count(), 0);
});
test('replacement bank: at most three backups and no stop words', () => {
  const ix = new P.PetalIndex([], D.RU), dict = ['что', 'сто', 'стю', 'зто', 'што'];
  eq(new Set(dict.map(w => ix.key(w))).size, 1);
  const w = new P.Words(dict, D.RU, bad); w.loadNext(['што\tлет']); const b = new P.ReplacementBank(w);
  b.addWord('что', ix.key('что')); b.addWord('лет'); eq(b.count(), 0);
  const safe = new P.Words(['сука'], D.RU, bad); safe.loadNext(['сука\tстоит']); const s = new P.ReplacementBank(safe);
  s.addWord('слово', ix.key('сука'), { сука: 100 }); s.addWord('стоит'); eq(s.count(), 0);
});
test('replacement bank: phrase end clears all phrase state', () => {
  const b = bank(); bankPair(b); b.keep(0); b.lock(20); b.endPhrase(); eq(b.textWords(), []); eq(b.count(), 0); eq(b.undo(), false);
  bankPair(b); eq(b.count(), 1); b.accept(0); b.endPhrase(); eq(b.undo(), false);
});
test('replacement bank: real corpora in both languages and personal pairs', () => {
  for (const [lang, before, after, right] of [['ru', 'что', 'сто', 'лет'], ['en', 'you', 'loo', 'paper']]) {
    const w = new P.Words(read(path.join(dictDir, 'dict_' + lang + '.txt')).trim().split('\n'), D[lang.toUpperCase()], bad);
    w.loadNext(read(path.join(dictDir, 'next_' + lang + '.txt')).split('\n'));
    const b = new P.ReplacementBank(w); b.addWord(before, new P.PetalIndex([], D[lang.toUpperCase()]).key(before)); b.addWord(right);
    eq(b.pending().map(p => p.after), [after]);
  }
  const w = new P.Words(['дом', 'том'], D.RU, bad); w.pairs['том'] = { стоит: 2 };
  const b = new P.ReplacementBank(w); bankPair(b); eq(b.count(), 1);
  w.pairs['том']['быстро'] = 2; eq(w.next('ТОМ', 3, false), ['быстро', 'стоит']); eq(b.count(), 0);
});

test('petal index: frequency, personal words, normalisation and English punctuation', () => {
  const ix = new P.PetalIndex(['дом', 'том', 'дам'], D.RU), k = ix.key('дом');
  eq(ix.candidates(k), ['дом', 'том']); eq(ix.candidates(k, { том: 5 }), ['том', 'дом']); eq(ix.candidates(k), ['дом', 'том']);
  eq(ix.candidates(k, { дом: 1, том: 5 }), ['том', 'дом']);
  eq(ix.candidates(k, { дом: 10, том: 5 }), ['дом', 'том'], 'counts update without rebuilding directions');
  eq(ix.candidates(k, { дом: 0, том: 5 }), ['том', 'дом']);
  const custom = new P.PetalIndex([], D.RU);
  eq(custom.candidates(k, { том: 5 }), ['том']); eq(custom.candidates(k, {}), [], 'forget removes personal candidates');
  eq(ix.key('ЁЪ'), ix.key('еь')); eq(ix.key('hello'), null); eq(ix.candidates('08'), []); eq(ix.candidates(''), []);
  const en = new P.PetalIndex([], D.EN); ok(en.key("don't")); ok(en.key('well-known')); eq(en.key('ё'), null);
  const safe = new P.Words(['сука'], D.RU, bad); eq(safe.petalCandidates(ix.key('сука'), { сука: 10 }), []);
  for (let p = 0; p < 8; p++) for (const r of [15, 22, 60]) eq(P.jabPetal([0, 0, Math.cos(p * Math.PI / 4) * r, Math.sin(p * Math.PI / 4) * r, 0, 0]), p);
});

test('petal dictionary Zipf coverage and lookup cost in both languages', () => {
  for (const lang of ['ru', 'en']) {
    const dict = read(path.join(dictDir, 'dict_' + lang + '.txt')).trim().split('\n'), t = D[lang.toUpperCase()], ix = new P.PetalIndex(dict, t);
    const cold = performance.now(); ix.candidates('0'); const buildMs = performance.now() - cold;
    let total = 0, first = 0, top3 = 0;
    dict.forEach((w, i) => { const weight = 1 / (i + 1); total += weight; const key = ix.key(w); if (!key) return; const got = ix.candidates(key); if (got[0] === w) first += weight; if (got.slice(0, 3).includes(w)) top3 += weight; });
    const safe = new P.Words(dict, t, bad), user = Object.fromEntries(dict.slice(0, 3000).map(w => [w, 2])), keys = dict.slice(0, 1000).map(w => ix.key(w));
    keys.forEach(k => safe.petalCandidates(k, user));
    const times = keys.map(k => { const start = performance.now(); safe.petalCandidates(k, user); return performance.now() - start; }).sort((a, b) => a - b), p95 = times[Math.floor(times.length * .95)];
    console.log(`  petals ${lang} first=${(first / total).toFixed(6)} top3=${(top3 / total).toFixed(6)} buildMs=${buildMs.toFixed(2)} lookupP95Ms=${p95.toFixed(3)} (Node desktop, 3000 user words)`);
    ok(first / total >= .88, lang + ' first'); ok(top3 / total >= .99, lang + ' top3'); ok(p95 < 5, lang + ' lookup ' + p95);
  }
});

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
test('start wobble cannot choose a petal or a turn in strokes and jabs', () => {
  for (let p = 0; p < 8; p++) for (const side of [-1, 1]) {
    const a = p * 45 * P.DEG, b = a + side * 40 * P.DEG;
    const prefix = [0, 0, 11.8 * Math.cos(b), 11.8 * Math.sin(b)];
    const ray = r => [r * Math.cos(a), r * Math.sin(a)];
    for (const len of [32, 45, 60]) {
      const pts = prefix.concat(...[12, 16, 24, len].map(ray));
      eq(P.resolve(D.RU, pts), P.slot(D.RU, p, 'c'), 'straight ' + p + '/' + side + '/' + len);
      eq(P.jab(D.RU, pts), P.slot(D.RU, p, 'c'), 'jab ' + p + '/' + side + '/' + len);
    }
    for (const sign of [-1, 1]) {
      const c = a + sign * 70 * P.DEG, end = ray(40);
      const pts = prefix.concat(...[12, 20, 30, 40].map(ray));
      for (const r of [6, 12, 18, 24]) pts.push(end[0] + r * Math.cos(c), end[1] + r * Math.sin(c));
      const want = P.slot(D.RU, p, sign > 0 ? 'l' : 'r');
      eq(P.resolve(D.RU, pts), want, 'turn ' + p + '/' + side + '/' + sign);
      eq(P.jab(D.RU, pts), want, 'turning jab ' + p + '/' + side + '/' + sign);
    }
  }
});
test('out and back is the letter at the centre', () => {
  for (let p = 0; p < 8; p++) eq(P.resolve(D.RU, outBack(p * 45)).v, D.RU[p][3] || D.RU[p][0], 'petal ' + p);
});
test('the start circle preserves short and sparse strokes and complete jabs', () => {
  for (let p = 0; p < 8; p++) {
    const a = p * 45 * P.DEG, ray = r => [r * Math.cos(a), r * Math.sin(a)];
    eq(P.resolve(D.RU, [0, 0, ...ray(P.P_MIN - 0.001)]).petal, -1, 'short tap ' + p);
    eq(P.resolve(D.RU, [0, 0, ...ray(P.P_MIN)]), P.slot(D.RU, p, 'c'), 'minimum stroke ' + p);
    for (const len of [14, 16, 22, 29, 30.01]) eq(P.jab(D.RU, [0, 0, ...ray(len)]), P.slot(D.RU, p, len < P.JAB_SHORT ? 'b' : 'c'), 'short jab ' + p + '/' + len);
    eq(P.resolve(D.RU, [0, 0, ...ray(24.01), 0, 0]), P.slot(D.RU, p, 'b'), 'short return ' + p);
    const j = new P.Jabs(0, 0), got = [], wobble = a + 40 * P.DEG;
    const pts = [[11.8 * Math.cos(wobble), 11.8 * Math.sin(wobble)], ...[12, 16, 24, 32, 24, 16, 8, 0].map(ray)];
    for (const [x, y] of pts) { const done = j.add(x, y); if (done) got.push(P.jab(D.RU, done)); }
    eq(got, [P.slot(D.RU, p, 'c')], 'complete jab ' + p);
  }
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

test('page startup supplies real word pairs and personal pairs to the bank in either load order and language', () => {
  const a = appSource.indexOf('  const dicts ='), b = appSource.indexOf('  // what this person says:', a);
  ok(a >= 0 && b > a);
  for (const corpusFirst of [false, true]) {
    const waiting = {}, personal = { вот: { том: 2 } };
    function fetch(url) {
      const callbacks = [], chain = { then(fn) { callbacks.push(fn); return chain; }, catch() { return chain; } };
      waiting[url] = () => {
        const data = { 'dict_ru.txt': 'дом\nтом\nстоит', 'next_ru.txt': 'том\tстоит', 'dict_en.txt': 'you\nloo\npaper', 'next_en.txt': 'loo\tpaper' };
        let value = { ok: true, text: () => data[url] }; callbacks.forEach(fn => { value = fn(value); });
      };
      return chain;
    }
    const api = new Function('P', 'D', 'window', 'store', 'fetch', 'paintChips',
      "let lang = 'ru', symbols = false; const Words = P.Words;\n" + appSource.slice(a, b) + '\nreturn {loadDict, words, setLang: l => {lang = l;}};')(
      P, D, box.window, { get: (k, d) => k === 'pairs' ? personal : d }, fetch, () => {});
    for (const lang of ['ru', 'en']) {
      api.setLang(lang); api.loadDict(lang);
      const order = ['dict_' + lang + '.txt', 'next_' + lang + '.txt']; if (corpusFirst) order.reverse();
      order.forEach(f => { ok(waiting[f], f + ' requested'); waiting[f](); });
      const w = api.words(), before = lang === 'ru' ? 'дом' : 'you', after = lang === 'ru' ? 'том' : 'loo', right = lang === 'ru' ? 'стоит' : 'paper';
      eq(w.next(after, 3, false), [right], lang + ' corpus reaches Words');
      eq(w.next('вот', 3, false), ['том'], 'personal pairs reach Words');
      const r = new P.ReplacementBank(w); r.addWord(before, new P.PetalIndex([], lang === 'ru' ? D.RU : D.EN).key(before)); r.addWord(right);
      eq(r.count(), 1, lang + ' bank receives evidence from the page loader');
    }
  }
});

// Run the page's real pointer handlers, typing and petal state with small DOM/voice substitutes.
function petalPage(settings = {}) {
  const values = { mode: 'petals', ...settings }, spoken = [], handlers = {}, chips = [];
  const store = { get(k, d) { return Object.hasOwn(values, k) ? values[k] : d; }, set(k, v) { values[k] = v; } };
  const elem = () => ({ attrs: {}, children: [], setAttribute(k, v) { this.attrs[k] = v; }, textContent: '', querySelector: () => null, open: false,
    focus() {}, replaceChildren() { this.children = []; }, appendChild(b) { this.children.push(b); },
    showModal() { this.open = true; }, close() { this.open = false; } });
  const nodes = Object.fromEntries(['text', 'replacementReview', 'replacementProgress', 'replacementPair', 'replacementContext', 'replacementListen', 'replacementAccept', 'replacementKeep', 'replacementKeepAll', 'replacementUndo', 'replacementBack', 'replacementNext', 'replacementClose', 'completionReview', 'completionTitle', 'completionProgress', 'completionPair', 'completionTailActions', 'completionSigns', 'completionListen', 'completionAccept', 'completionKeep', 'completionUndo', 'completionClose'].map(id => [id, elem()]));
  const svg = { addEventListener(k, f) { handlers[k] = f; }, setPointerCapture() {}, getBoundingClientRect: () => ({ left: 0, top: 0 }) };
  const documentHandlers = {};
  const document = { addEventListener(name, fn) { (documentHandlers[name] ||= []).push(fn); }, visibilityState: 'visible', createElement: elem };
  function block(start, end) { const a = appSource.indexOf(start), b = appSource.indexOf(end, a); ok(a >= 0 && b > a, start); return appSource.slice(a, b); }
  const code = `let text = '', shiftOnce = false, lastFix = null, lang = 'ru', symbols = false, game = null, guided = null, probe = null, onEnter = null, relayout = false, lastEnd = 0, tab = 'speak', talking = false;
    const $ = id => nodes[id];
    const FIX_GLOW_MS = 1500, P_MIN = P.P_MIN, HOLD_MS = P.HOLD_MS, ALT = P.ALT, Jabs = P.Jabs, jab = P.jab, resolve = P.resolve, fold = P.fold, own = P.own, shape = P.shape, soft = {};
    const bubble = elem(), bubbleText = elem(), trail = elem();
    const dict = new P.Words(store.get('dictionary', ['том', 'дом', 'мама', 'мыла', 'в', 'я', 'и']), D.RU, bad);
    dict.loadNext(store.get('pairs', []));
    function table() { return D.RU; } function words() { return store.get('unavailable', false) ? null : dict; } function userWords() { return store.get('user', {}); }
    function caps() { return shiftOnce || !text || /[.!?…]\\s$/.test(text); } function currentWord() { const m = /[\\p{L}']+$/u.exec(text); return m ? m[0] : ''; }
    function tick() {} function render() { syncReplacements(); } function paintChips() { chips.push(petalOptions.slice()); }
    function say(t) { spoken.push(t); } function remember() {} function talk() {} function talkScreen() {} function speakScreen() {}
    ${block('  let replacementInput =', '  // ---- typing display ----')}
    ${block('  function sayWord()', '  // the screen stays on')}
    ${block('  function sayAll()', '  function showBig(')}
    function guardMin() { return store.get('guard', 0); } function guardGap() { return 0; }
    function keepAwake() {} function light() {} function leanTo() {} function layout() {} function gameStroke() {} function guidedStroke() {}
    function Steady() { this.at = (x, y) => [x, y]; } function toBase(x, y) { return [x, y]; } function scr(x, y) { return [x, y]; } function xy(q) { return q.join(','); }
    function hit(x, y) { return y < -100 ? { fn: 'space' } : null; }
    function strokeFor(ch) { for (let p = 0; p < 8; p++) if (D.RU[p].includes(ch)) return { p }; return null; }
    ${block('  function typeLetter(v)', '  // a guided word:')}
    ${block('  // ---- «Толчки»: the cutting', '  // ---- a steadier hand')}
    ${block('  // ---- touch on the keyboard', '  // ---- a joystick,')}
    return { handlers, state: () => ({text, key: petalKey, options: petalOptions, exactWord, petalTyping, pending: petalPending(), replacements: replacementPending(), review: nodes.replacementReview.open,
      completion: nodes.completionReview.open, signStep: completion && completion.punctuation, proposal: nodes.completionPair.textContent}),
      review: openReplacementReview, sayAll, takeWord, accept: () => nodes.replacementAccept.onclick(),
      keepAll: () => nodes.replacementKeepAll.onclick(), undo: () => nodes.replacementUndo.onclick(), close: () => nodes.replacementClose.onclick(),
      listen: () => nodes.replacementListen.onclick(),
      complete: openCompletionReview, completionAccept: () => nodes.completionAccept.onclick(), completionKeep: () => nodes.completionKeep.onclick(),
      completionUndo: () => nodes.completionUndo.onclick(), completionListen: () => nodes.completionListen.onclick(), completionClose: closeCompletionReview,
      signs: () => nodes.completionSigns.children.map(b => ({text: b.textContent, label: b.attrs['aria-label']})),
      sign: s => nodes.completionSigns.children.find(b => b.textContent.startsWith(s || 'Без знака')).onclick(),
      bankWords: () => replacementInput ? replacementInput.bank.textWords() : [],
      setPassword: () => { nodes.text.querySelector = () => ({type: 'password'}); syncReplacements(); },
      externalEdit: v => { text = v; resetReplacements(); },
      next: nextPetal, exact: exactPetal, type: typeLetter, space, backspace, setGame: g => {game = g;},
      setSymbols: v => {symbols = v;}, setMode: v => {store.set('mode', v);}, clear: clearPetals,
      emptyOptions: () => {petalOptions = [];} };
  `;
  const api = new Function('P', 'D', 'bad', 'store', 'spoken', 'svg', 'document', 'elem', 'chips', 'handlers', 'nodes', code)(P, D, bad, store, spoken, svg, document, elem, chips, handlers, nodes);
  const event = (x, y) => ({ pointerId: 1, clientX: x, clientY: y, timeStamp: Date.now(), preventDefault() {} });
  api.down = (x = 0, y = 0) => handlers.pointerdown(event(x, y));
  api.move = (x, y) => handlers.pointermove(event(x, y));
  api.up = (cancel = false) => handlers[cancel ? 'pointercancel' : 'pointerup'](event(0, 0));
  api.push = p => { for (const r of [6, 12, 18, 24, 18, 12, 6, 0]) api.move(Math.cos(p * Math.PI / 4) * r, Math.sin(p * Math.PI / 4) * r); };
  api.word = w => { const key = new P.PetalIndex([], D.RU).key(w); api.down(); for (const p of key) api.push(+p); };
  api.tap = () => { api.down(); api.up(); };
  return { ...api, spoken, values, chips, documentEvent(name, event) { (documentHandlers[name] || []).forEach(fn => fn(event)); } };
}

test('page gestures outside the keyboard permit browser magnification without typing', () => {
  const page = petalPage(), before = page.state().text;
  for (const name of ['gesturestart', 'touchmove']) {
    let cancelled = false;
    page.documentEvent(name, { touches: [{}, {}], preventDefault() { cancelled = true; } });
    ok(!cancelled, name + ' must remain available for browser magnification');
    eq(page.state().text, before, 'magnification never types a letter');
  }
});

function replacementPage() {
  const a = petalPage({ dictionary: ['дом', 'том', 'стоит'], pairs: ['том\tстоит'] });
  a.word('дом'); a.up(); a.takeWord('стоит'); return a;
}
function completionPage(prefix = 'мне пожалуйста') {
  const a = petalPage({ mode: 'pulse', mine: ['мне пожалуйста воды', 'где моя чашка'], dictionary: ['мне', 'пожалуйста', 'воды', 'где', 'моя', 'чашка', 'хочу'] });
  for (const c of prefix) { if (c === ' ') a.space(); else a.type(c); }
  a.space(); return a;
}
test('completion UI: opening, listening, waiting and closing never edit the text', () => {
  const a = completionPage(), before = a.state().text;
  a.sayAll(); ok(a.state().completion); ok(!a.state().signStep); eq(a.state().proposal, 'Дописать: „воды“?'); eq(a.state().text, before); eq(a.spoken, []);
  a.completionListen(); eq(a.spoken, ['воды']); eq(a.state().text, before);
  a.completionClose(); eq(a.state().text, before); ok(!a.state().completion);
  a.sayAll(); eq(a.spoken, ['воды', before.trim()]);
});
test('completion UI: accept adds words only, undo restores spacing, signs require another press', () => {
  const a = completionPage(), before = a.state().text; a.complete(); a.completionAccept();
  eq(a.state().text, 'Мне пожалуйста воды '); ok(a.state().signStep); eq(a.spoken, []);
  a.completionAccept(); eq(a.state().text, 'Мне пожалуйста воды ');
  a.completionUndo(); eq(a.state().text, before); ok(!a.state().signStep);
  a.completionAccept(); a.sign('?'); eq(a.state().text, 'Мне пожалуйста воды? '); eq(a.spoken, []); ok(!a.state().completion);
  a.sayAll(); eq(a.spoken, ['Мне пожалуйста воды?']);
});
test('completion UI: keep goes to signs, first hint is labelled and never selected automatically', () => {
  const a = completionPage('где моя'), before = a.state().text; a.complete(); a.completionKeep();
  eq(a.state().text, before); ok(a.state().signStep); eq(a.signs().map(b => b.label), ['вопрос, предложен первым', 'точка', 'восклицание', 'Без знака']);
  a.sign(''); eq(a.state().text, before); eq(a.spoken, []);
  const b = completionPage(); b.complete(); b.completionKeep(); b.sign('!'); eq(b.state().text, 'Мне пожалуйста! '); eq(b.spoken, []);
});
test('completion UI: replacements are reviewed first, then completion uses accepted words', () => {
  const a = petalPage({ dictionary: ['дом', 'том', 'стоит'], pairs: ['том\tстоит'], mine: ['том стоит рядом'] });
  a.word('дом'); a.up(); a.takeWord('стоит'); a.sayAll(); ok(a.state().review); ok(!a.state().completion);
  a.accept(); a.close(); ok(a.state().completion); eq(a.state().proposal, 'Дописать: „рядом“?'); eq(a.state().text, 'Том стоит '); eq(a.spoken, []);
});
test('completion UI: password and stale editor actions cannot edit, speak or reopen', () => {
  const a = completionPage(); a.complete(); a.setPassword(); const before = a.state().text;
  a.completionAccept(); a.completionUndo(); a.completionListen(); a.sayAll(); ok(!a.complete()); eq(a.state().text, before); eq(a.spoken, []); ok(!a.state().completion);
  const b = completionPage(); b.complete(); b.externalEdit('Другой текст'); b.completionAccept(); b.completionListen(); eq(b.state().text, 'Другой текст'); eq(b.spoken, []);
});
test('completion UI: no tail still offers signs, existing final sign is preserved, editing permits a new review', () => {
  const a = completionPage('хочу'); a.sayAll(); ok(a.state().signStep); eq(a.spoken, []); a.completionClose();
  a.type('в'); a.sayAll(); ok(a.state().completion); a.completionClose();
  const b = completionPage(); b.type('?'); ok(!b.complete()); eq(b.state().text, 'Мне пожалуйста ?');
});
test('replacement UI: phrase end opens review, close and waiting never change text or speak', () => {
  const a = replacementPage(); eq(a.state().replacements.length, 1); eq(a.state().text, 'Дом стоит ');
  a.sayAll(); ok(a.state().review); eq(a.spoken, []); eq(a.state().text, 'Дом стоит ');
  a.close(); ok(!a.state().review); eq(a.state().text, 'Дом стоит '); eq(a.spoken, []);
});
test('replacement UI: accept edits the real text, never speaks, and undo restores it', () => {
  const a = replacementPage(); a.review(); a.accept(); eq(a.state().text, 'Том стоит '); eq(a.spoken, []);
  eq(a.state().replacements, []); a.undo(); eq(a.state().text, 'Дом стоит '); eq(a.state().replacements.length, 1); eq(a.spoken, []);
  a.accept(); a.close(); a.sayAll(); a.sign(''); a.sayAll(); eq(a.spoken, ['Том стоит']); eq(a.state().text, '');
});
test('replacement UI: listen reads the pair only after a press and leaves the text intact', () => {
  const a = replacementPage(); a.review(); eq(a.spoken, []); a.listen();
  eq(a.state().text, 'Дом стоит '); eq(a.spoken.length, 1); ok(a.spoken[0].includes('Дом') && a.spoken[0].includes('Том'));
});
test('replacement UI: keep all is silent, undoable and needs a separate say action', () => {
  const a = replacementPage(); a.review(); a.keepAll(); eq(a.state().text, 'Дом стоит '); eq(a.spoken, []); eq(a.state().replacements, []);
  a.undo(); eq(a.state().replacements.length, 1); a.keepAll(); a.close(); a.sayAll(); a.sign(''); a.sayAll(); eq(a.spoken, ['Дом стоит']);
});
test('replacement UI: external edits, deletion, manual alternatives and passwords retire proposals', () => {
  const a = replacementPage(); a.externalEdit('Другой текст'); eq(a.bankWords(), []); ok(!a.review());
  const b = replacementPage(); b.backspace(); eq(b.state().replacements, []);
  const c = replacementPage(); c.review(); c.setPassword(); eq(c.bankWords(), []); eq(c.state().replacements, []); ok(!c.state().review);
  c.accept(); c.undo(); c.sayAll(); eq(c.spoken, []); eq(c.bankWords(), []);
  const d = petalPage({ dictionary: ['дом', 'том', 'стоит'], pairs: ['дом\tстоит'] }); d.word('дом'); d.up(); d.next(); d.takeWord('стоит'); eq(d.state().replacements, []);
});
test('replacement input preserves punctuation, repeated words and locked speech', () => {
  const w = new P.Words(['что', 'сто'], D.RU, bad); w.loadNext(['сто\tлет']);
  const r = new P.ReplacementInput(w, 'speak', true), k = new P.PetalIndex([], D.RU).key('что');
  r.append('Что ', k); r.append('лет, '); r.append('что ', k); r.append('лет!'); r.finishWord(); eq(r.bank.pending().map(p => p.index), [0, 2]);
  ok(r.accept(2)); ok(r.accept(0)); eq(r.text, 'Сто лет, сто лет!'); ok(r.undo()); eq(r.text, 'Что лет, сто лет!');
  r.bank.lock(3); ok(!r.undo()); eq(r.text, 'Что лет, сто лет!');
});

test('petal page: preview, release, circular choices, buttons and precise word without autocorrection', () => {
  const a = petalPage(); a.word('дом'); eq(a.state().text, ''); eq(a.state().options, ['Том', 'Дом']); eq(a.spoken, []);
  a.up(); eq(a.state().text, 'Том ');
  a.tap(); eq(a.state().text, 'Дом '); a.tap(); eq(a.state().text, 'Том ');
  a.next(); eq(a.state().text, 'Дом ');
  a.down(); a.move(0, 35); a.up(); eq(a.state().text, ''); ok(a.state().exactWord);
  a.type('н'); a.type('а'); a.type('м'); a.type('а'); a.space();
  eq(a.state().text, 'Нама '); ok(!a.state().exactWord, 'space restores petals without correcting the exact word');
  a.word('мама'); a.up(); eq(a.state().text, 'Нама мама ');
});
test('petal page: cancellation, no candidate, explicit fallback, symbols and voice preference', () => {
  const a = petalPage(); a.word('мама'); a.up(true); eq(a.state().text, ''); eq(a.state().key, '');
  a.down(); for (let i = 0; i < 16; i++) a.push(0); a.up();
  ok(!a.state().pending); eq(a.state().key, ''); ok(!a.state().exactWord); eq(a.state().text, '');
  a.tap(); ok(!a.state().exactWord);
  a.exact(); ok(a.state().exactWord); a.space(); a.setSymbols(true); a.word('мама'); ok(!a.state().petalTyping); a.up(true);
  const voiced = petalPage({ words: true }); voiced.word('дом'); ok(voiced.spoken.includes('Том')); voiced.up(); voiced.next(); eq(voiced.spoken.at(-1), 'Дом');
});

test('petal page: first outward stroke commits a one-letter word from the real dictionary', () => {
  for (const w of ['в', 'я', 'и']) {
    ok(dict.includes(w), w + ' in dictionary');
    const a = petalPage({ dictionary: dict }), p = +new P.PetalIndex([], D.RU).key(w);
    a.down(); for (const r of [6, 12, 18, 24]) a.move(Math.cos(p * Math.PI / 4) * r, Math.sin(p * Math.PI / 4) * r);
    a.up();
    ok(a.state().options.map(v => v.toLowerCase()).includes(w), w + ' is offered');
    for (let i = 0; i < a.state().options.length && a.state().text.toLowerCase() !== w + ' '; i++) a.next();
    eq(a.state().text.toLowerCase(), w + ' ');
  }
});

test('petal page: unavailable dictionary and empty choices clear pending state', () => {
  const unloaded = petalPage({ unavailable: true }); unloaded.word('дом'); unloaded.up();
  eq(unloaded.state().text, ''); eq(unloaded.state().key, ''); ok(!unloaded.state().pending);
  const a = petalPage(); a.word('дом'); a.up(); const before = a.state().text;
  a.emptyOptions(); a.next(); eq(a.state().text, before); ok(!a.state().pending); eq(a.state().key, '');
});
test('petal word bubbles accept any letter in the target petal, keep progress and remember help refusal', () => {
  const b = bubbleScreen({ mode: 'petals', bubblesHelp: false }), g = b.game(), a = petalPage(); a.setGame(g);
  const target = g.word;
  for (const c of target) { a.down(); a.push(+new P.PetalIndex([], D.RU).key(c)); a.up(); }
  eq(g.n, 1); eq(g.progress.done, target.length); eq(b.values.bubblesHelp, false);
});

test('Android stop filter is generated from the same web stop list', () => {
  const kotlin = read(path.join(root, 'app/src/main/java/app/pulk/StopWords.kt'));
  for (const key of ['words', 'roots', 'prefixes', 'parts']) {
    const line = kotlin.split('\n').find(l => l.includes('val ' + key + ' ='));
    eq([...line.matchAll(/"([^"]*)"/g)].map(m => m[1]), box.window.PULK_STOP[key].map(w => P.fold(w.toLowerCase())), key);
  }
});

test('word bubbles use the same short words as Android and favour difficult letters', () => {
  const kt = read(path.join(root, 'app/src/main/java/app/pulk/BubbleWords.kt'));
  const list = [...kt.match(/val WORDS = listOf\(([\s\S]*?)\)/)[1].matchAll(/"([^"]+)"/g)].map(m => m[1]);
  eq(P.JAB_WORDS, list);
  ok(list.every(w => w.length >= 2 && w.length <= 4 && dict.includes(w) && !bad(w)));
  const samples = stats => Array.from({ length: 1000 }, (_, i) => P.nextJabWord(D.RU, 0, stats, '', i / 1000)).filter(w => w.includes('а')).length;
  ok(samples({ а: [0, 9] }) > samples({}), 'hard letters must occur more often');
});

function bubbleScreen(settings = {}) {
  const nodes = [], timers = [], spoken = [], hints = [], ticks = [];
  function node(tag, text = '', cls = '') {
    const n = { tag, textContent: text, className: cls, style: {}, children: [], attrs: {},
      appendChild(c) { this.children.push(c); return c; }, setAttribute(k, v) { this.attrs[k] = v; }, remove() {} };
    Object.defineProperty(n, 'innerHTML', { set() { this.children = []; this.textContent = ''; } });
    nodes.push(n); return n;
  }
  const values = { ...settings }, store = { get(k, d) { return Object.hasOwn(values, k) ? values[k] : d; }, set(k, v) { values[k] = v; } };
  const start = appSource.indexOf('  let game = null;'), end = appSource.indexOf('  // «Тренировки»: everyday', start);
  ok(start > 0 && end > start, 'bubble screen source');
  const factory = new Function('P', 'D', 'own', 'store', 'begin', 'screenAt', 'h', 'btn', 'document', 'showGuide', 'render', 'tick', 'say', 'setTimeout', 'addDrop', 'table', 'jab', 'resolve', 'missed',
    'let lang, symbols, shiftOnce, text; function drillsScreen() {}\n' + appSource.slice(start, end) + '\nreturn {start: gameScreen, game: () => game, stroke: gameStroke};');
  const api = factory(P, D, P.own, store, () => node('section'), () => {}, node,
    (text, cls, fn) => Object.assign(node('button', text, cls), { click: fn }),
    { createElement: node, createTextNode: t => node('text', t) }, ch => hints.push(ch), () => {}, () => ticks.push(1), t => spoken.push(t), fn => timers.push(fn), () => 1, () => D.RU, P.jab, P.resolve, () => {});
  api.start();
  return { ...api, nodes, timers, spoken, hints, ticks, values, button: label => nodes.find(n => n.tag === 'button' && n.textContent === label) };
}

test('word bubble accepts separate touches and keyboard letters, pops only at the complete word', () => {
  const s = bubbleScreen({ mode: 'jabs' }), g = s.game();
  const word = g.word; eq(s.spoken, [], 'voice off');
  for (let i = 0; i < word.length; i++) {
    g.start(); s.stroke({ slot: { petal: 0, v: word[i] } });
    s.stroke({ jabs: { finish: () => null } }); // lifting or a centre tap keeps progress
    eq(g.progress.done, i + 1); eq(g.n, i === word.length - 1 ? 1 : 0);
  }
  eq(g.busy, true); g.add('а'); eq(g.n, 1); eq(s.values.popped, 1);
  eq(s.ticks.length, word.length); eq(s.timers.length, 1);
  s.timers.shift()(); eq(s.game().progress.done, 0); ok(s.game().word !== word);
});

test('word bubble assistance resets for the next letter and remembers refusal', () => {
  const s = bubbleScreen({ mode: 'jabs', words: true }), g = s.game(), target = g.progress.next;
  eq(s.spoken, [g.word]); s.button('Повторить слово').click(); eq(s.spoken, [g.word, g.word]);
  const wrong = target === 'а' ? 'н' : 'а';
  g.add(wrong); eq(g.progress.misses, 1); eq(g.progress.done, 0); eq(s.hints.at(-1), target);
  g.add(wrong); eq(g.progress.misses, 2); eq(g.progress.done, 0);
  g.add(wrong); eq(g.progress.done, 1); eq(g.progress.misses, 0); eq(s.values.letters[target], [0, 3]);
  s.button('Помощь после промахов: включена').click(); eq(s.values.bubblesHelp, false);
  const next = g.progress.next, off = next === 'а' ? 'н' : 'а';
  for (let i = 0; i < 4; i++) g.add(off);
  eq(g.progress.done, 1); eq(s.hints.at(-1), null);
  const again = bubbleScreen(s.values); eq(again.values.bubblesHelp, false); eq(again.hints.at(-1), null);
});

test('pulse bubbles keep single-letter play', () => {
  const s = bubbleScreen({ mode: 'pulse' }), g = s.game();
  eq(!!g.words, false); eq(g.letter.length, 1);
  g.wrong('х'); eq(g.n, 0); eq(g.miss, 1);
  s.stroke({ slot: { petal: 0, v: g.letter } }); eq(g.n, 1); eq(s.values.popped, 1);
});

test('a success fades word bubble help at higher levels', () => {
  const s = bubbleScreen({ mode: 'jabs', popped: 15 }), g = s.game(), target = g.progress.next;
  eq(s.hints.at(-1), null);
  for (let i = 0; i < 2; i++) g.add(target === 'а' ? 'н' : 'а');
  eq(s.hints.at(-1), target); eq(g.progress.misses, 2);
  g.add(target); eq(s.hints.at(-1), null); eq(g.progress.misses, 0);
});

test('word bubbles finish one round, reject later input and restart cleanly', () => {
  const s = bubbleScreen({ mode: 'jabs' });
  for (let i = 0; i < 10; i++) {
    const g = s.game(); [...g.word].forEach(c => g.add(c));
    eq(g.n, i + 1); eq(s.values.popped, i + 1); s.timers.shift()();
  }
  eq(s.game(), null); const roundEnd = s.nodes.find(n => n.className === 'ok').textContent;
  ok(roundEnd.startsWith('10 слов! +1 капля'));
  s.button('Повторить слово').click(); s.button('Помощь после промахов: включена').click();
  s.button('Ещё раунд').click(); eq(s.game().n, 0); eq(s.game().progress.done, 0);
  eq(s.values.bubblesHelp, false); eq(s.hints.at(-1), null);
});

function speechFrom(available, chosen = '', busy = false, supported = true, lang = 'ru') {
  const spoken = [], timers = [], hint = { hidden: true }, listeners = {};
  let reads = 0;
  const ss = { getVoices: () => { reads++; return available; }, speaking: busy, cancel() {}, speak: u => spoken.push(u),
    addEventListener: (name, fn) => { listeners[name] = fn; } };
  const start = appSource.indexOf("  let lastSaid = ''"), end = appSource.indexOf('  // «Говорить по словам»', start);
  ok(start >= 0 && end > start, 'speech source is present');
  const api = new Function('window', 'speechSynthesis', 'SpeechSynthesisUtterance', 'store', '$', 'setTimeout', 'lang',
    'function replacementSecure() { return false; }\n' + appSource.slice(start, end) + '\nreturn {say, voices, last: () => lastSaid, hideVoiceHint: typeof hideVoiceHint === "function" ? hideVoiceHint : () => {}, refreshWith: fn => { if (typeof paintVoiceSettings !== "undefined") paintVoiceSettings = fn; }};')(
    { speechSynthesis: supported ? ss : null }, ss, function (text) { this.text = text; },
    { get: (k, def) => k === 'voice' ? chosen : def }, () => hint, fn => timers.push(fn), lang);
  return { ...api, spoken, hint, reads: () => reads, changed: vs => { available = vs; ok(listeners.voiceschanged, 'global voiceschanged listener'); listeners.voiceschanged(); },
    flush: () => timers.splice(0).forEach(fn => fn()) };
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
test('voice loading starts on page load and the first phrase is spoken once voices arrive', () => {
  const s = speechFrom([]), ru = { name: 'ru', lang: 'ru_RU', localService: true };
  ok(s.reads() > 0, 'getVoices called before the first phrase');
  s.say('Да'); eq(s.spoken, []); eq(s.hint.hidden, false);
  s.changed([ru]); eq(s.spoken.map(u => u.text), ['Да']); eq(s.hint.hidden, true);
  s.changed([ru]); eq(s.spoken.length, 1, 'no repeat on later events');
});
test('only the latest phrase is waiting for voices, and dismissing cancels it', () => {
  const ru = { name: 'ru', lang: 'ru', localService: true }, s = speechFrom([]);
  s.say('Да'); s.say('Нет'); s.changed([ru]); eq(s.spoken.map(u => u.text), ['Нет']);
  const closed = speechFrom([]); closed.say('Подожди'); closed.hideVoiceHint(); closed.changed([ru]);
  eq(closed.spoken, []); eq(closed.hint.hidden, true);
});
test('rebuilding the speaking screen keeps the first pending phrase', () => {
  const s = speechFrom([]), ru = { name: 'ru', lang: 'ru', localService: true };
  const start = appSource.indexOf('  function begin(mode)'), end = appSource.indexOf('  // «Говорить».', start);
  ok(start >= 0 && end > start, 'screen rebuild source is present');
  const begin = new Function('$', 'document', 'showGuide', 'hideVoiceHint',
    'let paintVoiceSettings, talking, game, probe, sysKbd = false, chipMode, chipAction, onEnter, guided, exactWord; function clearPetals() {} function resetReplacements() {}\n' + appSource.slice(start, end) + 'return begin;')(
    () => ({ innerHTML: '' }), { body: { classList: { remove() {} } } }, () => {}, s.hideVoiceHint);
  s.say('Хочу пить'); begin('speak'); s.changed([ru]);
  eq(s.spoken.map(u => u.text), ['Хочу пить']);
});
test('global voice updates refresh settings and hide the hint only for the needed language', () => {
  const s = speechFrom([]), ru = { name: 'ru', lang: 'ru', localService: true }, en = { name: 'en', lang: 'en-GB', localService: true };
  let refreshed = 0; s.refreshWith(() => refreshed++);
  s.say('Hello'); s.changed([ru]); eq(s.hint.hidden, false); eq(s.spoken, []);
  s.changed([ru, en]); eq(s.hint.hidden, true); eq(s.spoken[0].voice, en); eq(refreshed, 2);
  ok(!appSource.includes('speechSynthesis.onvoiceschanged ='), 'settings must not replace the global listener');
});
test('word speech keeps a nonempty phrase for showing', () => {
  const s = speechFrom([]); s.say('Привет', true); eq(s.last(), 'Привет');
  s.say('Другое', true); eq(s.last(), 'Привет'); s.say('Вся фраза'); eq(s.last(), 'Вся фраза');
});
test('voice language matching accepts regions and rejects missing or unrelated tags', () => {
  const ru = { name: 'ru', lang: 'RU_ru', localService: true }, en = { name: 'en', lang: 'en_GB', localService: true };
  const s = speechFrom([{ localService: true }, { lang: 12, localService: true }, { lang: 'rubbish', localService: true }, ru, en]);
  eq(s.voices(true), [ru]); eq(s.voices(false), [en]);
  s.say('5?'); eq(s.spoken[0].voice, ru);
  const english = speechFrom([en], '', false, true, 'en'); english.say('123'); eq(english.spoken[0].voice, en);
});
test('showing or closing the voice hint hides it, and changing tabs also clears it', () => {
  const nodes = { showSilent: {}, closeVoiceHint: {}, screen: { scrollTop: 4 } }, s = speechFrom([]);
  let shown;
  const start = appSource.indexOf("  $('showSilent').onclick"), end = appSource.indexOf("  $('back').onclick", start);
  new Function('$', 'showBig', 'text', 'lastSaid', 'hideVoiceHint', appSource.slice(start, end))(
    id => nodes[id], t => { shown = t; }, '', 'Хочу пить', s.hideVoiceHint);
  s.say('Хочу пить'); nodes.showSilent.onclick(); eq(shown, 'Хочу пить'); eq(s.hint.hidden, true);
  s.say('Да'); ok(nodes.closeVoiceHint.onclick, 'close action'); nodes.closeVoiceHint.onclick(); eq(s.hint.hidden, true);
  const tabButton = { dataset: { tab: 'pond' }, setAttribute() {} };
  const tabsStart = appSource.indexOf("  document.querySelectorAll('#tabs button').forEach(function (b) {"), tabsEnd = appSource.indexOf("  window.addEventListener('resize'", tabsStart);
  ok(tabsStart >= 0 && tabsEnd > tabsStart, 'tab handlers present');
  new Function('document', '$', 'hideVoiceHint', 'SCREENS', 'letSleep', 'let tab;\n' + appSource.slice(tabsStart, tabsEnd))(
    { querySelectorAll: () => [tabButton] }, id => nodes[id], s.hideVoiceHint, { pond() {} }, () => {});
  s.say('Нет'); tabButton.onclick(); eq(s.hint.hidden, true);
  s.changed([{ name: 'ru', lang: 'ru', localService: true }]); eq(s.spoken, [], 'no deferred speech after leaving the tab');
});
test('the voice hint announces text only and keeps both actions accessible', () => {
  const html = read(path.join(root, 'web/index.html'));
  const hint = html.slice(html.indexOf('<div id="voiceHint"'), html.indexOf('<div id="chips"'));
  ok(/<p[^>]*role="status"[^>]*>[^<]+<\/p>/.test(hint), 'status contains text only');
  ok(!/^<div[^>]*role="status"/.test(hint), 'buttons outside live status');
  ok(/id="closeVoiceHint"[^>]*aria-label="[^"]+"/.test(hint), 'close has a spoken label');
  ok(!/локальн/i.test(hint), 'plain language');
});
test('left-hand layout puts «Сказать» at the left edge', () => {
  const html = read(path.join(root, 'web/index.html'));
  ok(/body\.left-hand #chips\s*\{[^}]*flex-direction:\s*row\s*;/.test(html), 'left-hand starts at the left edge');
  ok(/#chips button\.main[^}]*order:\s*-1/.test(html), 'main action comes first');
  ok(/#chips\s*\{[^}]*flex-wrap:\s*wrap-reverse/.test(html), 'chips wrap with the first choice nearest the thumb');
  ok(/#chips > \*[^}]*background:\s*var\(--panel\)[^}]*border:\s*2px solid var\(--ink\)/.test(html), 'status and action chips have an opaque panel and contrasting outline');
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
  for (const f of ['dict_ru.txt', 'dict_en.txt', 'next_ru.txt', 'next_en.txt'])
    ok(read(path.join(root, 'web', f)) === read(path.join(root, 'app/src/main/assets', f)), f + ' is the app\'s (run node tools/build-web.js)');
});

test('both dictionaries are kept for use without internet', () => {
  const html = read(path.join(root, 'web/index.html'));
  for (const f of ['dict_ru.txt', 'dict_en.txt', 'next_ru.txt', 'next_en.txt']) ok(html.indexOf("'" + f + "'") >= 0, f + ' is in the offline list of web/index.html');
});

test('the page script parses', () => {
  for (const f of ['web/app.js']) new Function(read(path.join(root, f)));
});

console.log('Пульк в браузере: ' + passed + ' tests, ' + failed + ' failed');
if (failed) process.exit(1);
