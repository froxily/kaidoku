const POS = {
  n: 'Noun',
  pn: 'Pronoun',
  num: 'Number',
  ctr: 'Counter',
  'n-suf': 'Noun suffix',
  'n-pref': 'Noun prefix',
  'n-adv': 'Adverbial noun',
  'n-t': 'Temporal noun',
  vs: 'Takes する',
  'vs-s': 'する verb',
  'vs-i': 'する verb',
  vk: 'くる verb',
  vt: 'Transitive',
  vi: 'Intransitive',
  v1: 'Ichidan verb',
  'v1-s': 'Ichidan verb (くれる)',
  v5u: 'Godan verb',
  v5k: 'Godan verb',
  'v5k-s': 'Godan verb (行く)',
  v5g: 'Godan verb',
  v5s: 'Godan verb',
  v5t: 'Godan verb',
  v5n: 'Godan verb',
  v5b: 'Godan verb',
  v5m: 'Godan verb',
  v5r: 'Godan verb',
  'v5r-i': 'Godan verb (irregular)',
  v5aru: 'Godan verb (ある)',
  'adj-i': 'い-adjective',
  'adj-ix': 'い-adjective (いい/よい)',
  'adj-na': 'な-adjective',
  'adj-no': 'Takes の',
  'adj-pn': 'Pre-noun adjectival',
  'adj-f': 'Pre-noun',
  adv: 'Adverb',
  'adv-to': 'Adverb (takes と)',
  exp: 'Expression',
  conj: 'Conjunction',
  int: 'Interjection',
  prt: 'Particle',
  pref: 'Prefix',
  suf: 'Suffix',
  aux: 'Auxiliary',
  'aux-v': 'Auxiliary verb',
  'aux-adj': 'Auxiliary adjective'
};

export function posLabels(tags) {
  return [...new Set(tags.map(tag => POS[tag] ?? tag))];
}

export function isKanaOnly(word) {
  return !/[㐀-鿿豈-﫿]/u.test(word);
}

export function count(n, singular, plural = `${singular}s`) {
  return `${n.toLocaleString('en')} ${n === 1 ? singular : plural}`;
}

export function kindName(kind) {
  return kind === 'kanji' ? 'Kanji' : 'Vocabulary';
}

export function itemNoun(kind, n) {
  return kind === 'kanji' ? count(n, 'kanji', 'kanji') : count(n, 'word');
}
