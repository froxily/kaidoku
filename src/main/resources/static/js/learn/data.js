import { romajiVariants } from './romaji.js';

export const LEVELS = ['N5', 'N4', 'N3'];
export const KINDS = ['kanji', 'vocab'];

const requests = new Map();
const datasets = new Map();

function fetchJson(url) {
  if (!requests.has(url)) {
    const request = fetch(url, { credentials: 'same-origin' }).then(response => {
      if (!response.ok) {
        throw new Error(`${url} returned ${response.status}`);
      }
      return response.json();
    });
    request.catch(() => requests.delete(url));
    requests.set(url, request);
  }
  return requests.get(url);
}

export function toHiragana(text) {
  return text.replace(/[ァ-ヶ]/g, char => String.fromCharCode(char.charCodeAt(0) - 0x60));
}

export function normalize(text) {
  return toHiragana(text.normalize('NFKC').trim().toLowerCase());
}

function buildIndex(kind, items) {
  items.forEach((item, order) => {
    item.order = order;
    const keys = kind === 'kanji'
        ? [item.character, ...item.readings_search.map(reading => reading.replace(/[-.]/g, ''))]
        : [item.word, ...(item.other_forms ?? []), item.reading_hiragana, ...(item.other_readings ?? []), item.romaji];
    item.keys = [...new Set(keys.map(normalize))];
    item.glosses = item.meanings.map(meaning => meaning.toLowerCase());
  });
  return items;
}

export async function dataset(kind) {
  if (!datasets.has(kind)) {
    const promise = fetchJson(`/learning/${kind}.json`).then(document => {
      const items = buildIndex(kind, document.items);
      return { items, byId: new Map(items.map(item => [item.id, item])), metadata: document.metadata };
    });
    promise.catch(() => datasets.delete(kind));
    datasets.set(kind, promise);
  }
  return datasets.get(kind);
}

export function links() {
  return fetchJson('/learning/links.json');
}

export async function lessons(kind) {
  const list = await fetchJson(`/api/learning/${kind}/lessons`);
  const byLevel = new Map(LEVELS.map(level => [level, list.filter(lesson => lesson.level === level)]));
  return { list, byId: new Map(list.map(lesson => [lesson.id, lesson])), byLevel };
}

function scoreKeys(item, query) {
  let best = Infinity;
  for (const key of item.keys) {
    if (key === query) {
      return 0;
    }
    if (key.startsWith(query)) {
      best = Math.min(best, 2);
    } else if (key.includes(query)) {
      best = Math.min(best, 4);
    }
  }
  return best;
}

function score(item, query, kanaQueries) {
  let best = Math.min(scoreKeys(item, query), ...kanaQueries.map(kana => scoreKeys(item, kana)));
  for (const gloss of item.glosses) {
    if (gloss === query || gloss === `to ${query}`) {
      best = Math.min(best, 1);
    } else if (best > 3) {
      const at = gloss.indexOf(query);
      if (at === 0 || (at > 0 && !/[a-z]/.test(gloss[at - 1]))) {
        best = Math.min(best, 3);
      } else if (at > 0) {
        best = Math.min(best, 5);
      }
    }
  }
  return best;
}

export function search(items, rawQuery, level) {
  const query = normalize(rawQuery);
  const pool = level ? items.filter(item => item.level === level) : items;
  if (!query) {
    return pool;
  }
  const kanaQueries = /^[a-zāīūēōâîûêô'\s-]+$/.test(query) ? romajiVariants(rawQuery) : [];
  return pool
      .map(item => ({ item, score: score(item, query, kanaQueries) }))
      .filter(result => result.score !== Infinity)
      .sort((a, b) => a.score - b.score
          || LEVELS.indexOf(a.item.level) - LEVELS.indexOf(b.item.level)
          || a.item.order - b.item.order)
      .map(result => result.item);
}
