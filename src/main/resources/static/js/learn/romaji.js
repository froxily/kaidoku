const KANA = {
  a: 'あ', i: 'い', u: 'う', e: 'え', o: 'お',
  ka: 'か', ki: 'き', ku: 'く', ke: 'け', ko: 'こ',
  ga: 'が', gi: 'ぎ', gu: 'ぐ', ge: 'げ', go: 'ご',
  sa: 'さ', shi: 'し', si: 'し', su: 'す', se: 'せ', so: 'そ',
  za: 'ざ', ji: 'じ', zi: 'じ', zu: 'ず', ze: 'ぜ', zo: 'ぞ',
  ta: 'た', chi: 'ち', ti: 'ち', tsu: 'つ', tu: 'つ', te: 'て', to: 'と',
  da: 'だ', di: 'ぢ', du: 'づ', de: 'で', do: 'ど',
  na: 'な', ni: 'に', nu: 'ぬ', ne: 'ね', no: 'の',
  ha: 'は', hi: 'ひ', fu: 'ふ', hu: 'ふ', he: 'へ', ho: 'ほ',
  ba: 'ば', bi: 'び', bu: 'ぶ', be: 'べ', bo: 'ぼ',
  pa: 'ぱ', pi: 'ぴ', pu: 'ぷ', pe: 'ぺ', po: 'ぽ',
  ma: 'ま', mi: 'み', mu: 'む', me: 'め', mo: 'も',
  ya: 'や', yu: 'ゆ', yo: 'よ',
  ra: 'ら', ri: 'り', ru: 'る', re: 'れ', ro: 'ろ',
  wa: 'わ', wo: 'を',
  kya: 'きゃ', kyu: 'きゅ', kyo: 'きょ',
  gya: 'ぎゃ', gyu: 'ぎゅ', gyo: 'ぎょ',
  sha: 'しゃ', shu: 'しゅ', sho: 'しょ',
  sya: 'しゃ', syu: 'しゅ', syo: 'しょ',
  ja: 'じゃ', ju: 'じゅ', jo: 'じょ',
  jya: 'じゃ', jyu: 'じゅ', jyo: 'じょ',
  zya: 'じゃ', zyu: 'じゅ', zyo: 'じょ',
  cha: 'ちゃ', chu: 'ちゅ', cho: 'ちょ',
  tya: 'ちゃ', tyu: 'ちゅ', tyo: 'ちょ',
  cya: 'ちゃ', cyu: 'ちゅ', cyo: 'ちょ',
  dya: 'ぢゃ', dyu: 'ぢゅ', dyo: 'ぢょ',
  nya: 'にゃ', nyu: 'にゅ', nyo: 'にょ',
  hya: 'ひゃ', hyu: 'ひゅ', hyo: 'ひょ',
  bya: 'びゃ', byu: 'びゅ', byo: 'びょ',
  pya: 'ぴゃ', pyu: 'ぴゅ', pyo: 'ぴょ',
  mya: 'みゃ', myu: 'みゅ', myo: 'みょ',
  rya: 'りゃ', ryu: 'りゅ', ryo: 'りょ',
  la: 'ぁ', li: 'ぃ', lu: 'ぅ', le: 'ぇ', lo: 'ぉ',
  xa: 'ぁ', xi: 'ぃ', xu: 'ぅ', xe: 'ぇ', xo: 'ぉ',
  lya: 'ゃ', lyu: 'ゅ', lyo: 'ょ', ltu: 'っ', ltsu: 'っ',
  xya: 'ゃ', xyu: 'ゅ', xyo: 'ょ', xtu: 'っ', xtsu: 'っ',
  va: 'ゔぁ', vi: 'ゔぃ', vu: 'ゔ', ve: 'ゔぇ', vo: 'ゔぉ',
  vya: 'ゔゃ', vyu: 'ゔゅ', vyo: 'ゔょ'
};

const LONG_VOWELS = {
  ā: 'aa', ī: 'ii', ū: 'uu', ē: 'ee', ō: 'ou',
  â: 'aa', î: 'ii', û: 'uu', ê: 'ee', ô: 'ou'
};

const MACRON_CHOICES = { ō: ['ou', 'oo'], ô: ['ou', 'oo'], ē: ['ee', 'ei'], ê: ['ee', 'ei'] };

export function romajiVariants(text) {
  let spellings = [text.trim().normalize('NFKC').toLowerCase()];
  for (const [mark, choices] of Object.entries(MACRON_CHOICES)) {
    spellings = spellings.flatMap(spelling => spelling.includes(mark)
        ? choices.map(choice => spelling.replaceAll(mark, choice))
        : [spelling]);
  }
  return [...new Set(spellings.slice(0, 8).map(romajiToKana).filter(Boolean))];
}

export function romajiToKana(text) {
  const input = text.trim().normalize('NFKC').toLowerCase()
      .replace(/[āīūēōâîûêô]/g, vowel => LONG_VOWELS[vowel])
      .replace(/\s+/g, '');
  if (!/^[a-z'-]+$/.test(input) || !/[a-z]/.test(input)) {
    return null;
  }
  let result = '';
  let index = 0;
  while (index < input.length) {
    const char = input[index];
    const next = input[index + 1];
    if (char === '-') {
      result += 'ー';
      index++;
      continue;
    }
    if (char === 'n') {
      if (next === "'" || next === '-') {
        result += 'ん';
        index += 2;
        continue;
      }
      if (next === 'n' && !/[aeiouy]/.test(input[index + 2] ?? '')) {
        result += 'ん';
        index += 2;
        continue;
      }
      if (!next || /[bcdfghjklmnpqrstvwxz]/.test(next)) {
        result += 'ん';
        index++;
        continue;
      }
    }
    if (char === 'm' && /[bpm]/.test(next ?? '')) {
      result += 'ん';
      index++;
      continue;
    }
    if ((char === next && /[bcdfghjklmpqrstvwxyz]/.test(char)) || input.startsWith('tch', index)) {
      result += 'っ';
      index++;
      continue;
    }
    let matched = false;
    for (let length = Math.min(4, input.length - index); length > 0; length--) {
      const kana = KANA[input.slice(index, index + length)];
      if (kana) {
        result += kana;
        index += length;
        matched = true;
        break;
      }
    }
    if (!matched) {
      return null;
    }
  }
  return result;
}
