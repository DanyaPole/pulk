// Words Пульк never offers: rude, obscene and sexual words are kept out of the suggestions, the «next word» chips
// and the corrections, because children use the keyboard too. They can still be typed letter by letter, and a word
// typed on purpose is never "corrected" into another. Edit by hand; ё and capitals do not matter.
//   words — whole words only;
//   roots — the start of a word, also after a prefix from «prefixes» (на-хуй, за-сранец, отъ-ебись);
//   parts — anywhere inside a word (English only: Russian roots hide inside harmless words, as «ёб» in «ребёнок»).
// tools/test-pulk-web.js checks that harmless look-alikes (ребёнок, небо, команда, страх, cocktail) stay allowed.
window.PULK_STOP = {
  words: [
    // ru: insults
    'сука', 'суки', 'суке', 'суку', 'сукой', 'сукин', 'сукина', 'сукины', 'сучка', 'сучки', 'сучке', 'сучкой', 'сучку', 'сучара',
    'тварь', 'твари', 'мразь', 'мрази', 'гнида', 'гниды', 'шалава', 'шалавы', 'падла', 'падлы',
    'ублюдок', 'ублюдка', 'ублюдку', 'ублюдки', 'ублюдков', 'ублюдком', 'сволочь', 'сволочи', 'сволочью',
    'дебил', 'дебила', 'дебилу', 'дебилы', 'дебилов', 'дебилом', 'придурок', 'придурка', 'придурку', 'придурки', 'придурков', 'придурком',
    'урод', 'урода', 'уроду', 'уроды', 'уродов', 'уродом', 'уродина', 'уродины',
    'манда', 'манды', 'херня', 'херни', 'нахер', 'похер',
    // ru: sexual
    'минет', 'пенис', 'пениса', 'пенисы', 'вагина', 'вагины', 'порно', 'порнуха', 'порнухи', 'порнография', 'порнографии',
    // en
    'fag', 'fags', 'dick', 'dicks', 'pussy', 'cock', 'cocks', 'tits', 'boob', 'boobs', 'booby', 'boobies', 'penis', 'penises',
    'vagina', 'vaginal', 'sex', 'sexy', 'sexier', 'sexiest', 'sexual', 'sexually', 'bastard', 'bastards',
    'piss', 'pissed', 'pisses', 'pissing', 'damn', 'damned', 'damnit', 'bullshit',
  ],
  roots: [
    // ru: obscene
    'хуй', 'хуе', 'хуя', 'хую', 'хуи', 'пизд', 'бля', 'еба', 'ебл', 'ебу', 'ебн', 'ебет', 'ебис', 'еби',
    'мудак', 'мудач', 'мудил', 'пидор', 'пидар', 'педик', 'педрил', 'залуп', 'гандон', 'шлюх', 'дроч',
    // ru: rude
    'жоп', 'говн', 'сран', 'срат', 'сдох',
    // ru: sexual
    'секс', 'траха', 'трахн', 'сись', 'оргазм',
  ],
  prefixes: ['на', 'по', 'за', 'от', 'отъ', 'вы', 'у', 'при', 'раз', 'разъ', 'съ', 'под', 'подъ', 'до', 'о', 'об', 'объ',
    'ни', 'недо', 'пере', 'въ', 'изъ'],
  parts: [
    // en
    'fuck', 'shit', 'bitch', 'cunt', 'whore', 'slut', 'nigg', 'fagg', 'porn', 'wank', 'asshole', 'dickhead', 'cocksuck', 'goddamn',
  ],  // rude words that people do say: a finished one is replaced by a kinder word of the same meaning, lit up like a
  // correction; ⌫ right after brings the person's own word back, and then Пульк leaves that word alone
  soft: {
    'дурак': 'глупый', 'дура': 'глупая', 'дураки': 'глупые', 'дурака': 'глупого', 'дурой': 'глупой',
    'идиот': 'глупый', 'идиотка': 'глупая', 'идиоты': 'глупые', 'дебил': 'глупый', 'дебилы': 'глупые',
    'придурок': 'чудак', 'придурки': 'чудаки', 'тупой': 'глупый', 'тупая': 'глупая', 'тупые': 'глупые',
    'урод': 'плохой', 'уроды': 'плохие', 'сволочь': 'плохой', 'гад': 'вредный', 'гадина': 'вредная',
  },
};
