import { dataset } from './data.js';
import { readingQuestions } from './quiz.js';
import { isDue, mastery, recordKey } from './srs.js';
import { progressStore } from './store.js';

const VERSION_KEY = 'datasetVersion';
let versionCheck = null;
let persistenceRequest;

export async function loadProgress(kind) {
  const store = await progressStore();
  const [records, sets] = await Promise.all([store.records(kind), store.sets(kind)]);
  return {
    store,
    records: new Map(records.map(record => [record.key, record])),
    sets: new Map(sets.map(set => [set.setId, set]))
  };
}

export function checkContentVersion() {
  versionCheck ??= (async () => {
    try {
      const store = await progressStore();
      const response = await fetch('/learning/manifest.json', { credentials: 'same-origin' });
      if (!response.ok) {
        return { changed: false, current: null };
      }
      const current = (await response.json()).dataset_version;
      if (typeof current !== 'string' || !current) {
        return { changed: false, current: null };
      }
      const saved = await store.getMeta(VERSION_KEY);
      await store.setMeta(VERSION_KEY, current);
      return { changed: Boolean(saved) && saved !== current, current };
    } catch {
      return { changed: false, current: null };
    }
  })();
  return versionCheck;
}

export async function reconcile(kind, data, readingIds) {
  const [store, version] = await Promise.all([progressStore(), checkContentVersion()]);
  const records = await store.records(kind);
  const hidden = {
    datasetVersion: version.current,
    keys: records.filter(record => !data.byId.has(record.id) || (record.track === 'reading' && !readingIds.has(record.id)))
        .map(record => record.key)
  };
  await store.setMeta(`hidden:${kind}`, hidden);
  return hidden;
}

export async function dashboardDueCount(kind, now) {
  try {
    let current = null;
    try {
      const response = await fetch('/learning/manifest.json', { credentials: 'same-origin' });
      if (response.ok) {
        current = (await response.json()).dataset_version;
      }
    } catch {
    }
    const store = await progressStore();
    let hidden = await store.getMeta(`hidden:${kind}`);
    if (typeof current !== 'string' || !current || hidden?.datasetVersion !== current || !Array.isArray(hidden.keys)) {
      const data = await dataset(kind);
      const questions = await readingQuestions(kind, data.items);
      hidden = await reconcile(kind, data, new Set(questions.keys()));
    }
    const keys = new Set(hidden.keys);
    return (await store.records(kind))
        .filter(record => record.state !== 'new' && isDue(record, now) && !keys.has(record.key)).length;
  } catch {
    return 0;
  }
}

export function requestPersistence() {
  persistenceRequest ??= (async () => {
    try {
      const storage = globalThis.navigator?.storage;
      if (typeof storage?.persisted === 'function' && typeof storage?.persist === 'function' && !await storage.persisted()) {
        await storage.persist();
      }
    } catch {
    }
  })();
}

export function trackRecord(progress, kind, track, id) {
  return progress.records.get(recordKey(kind, track, id)) ?? null;
}

export function itemTracks(id, readingIds) {
  return readingIds.has(id) ? ['reading', 'meaning'] : ['meaning'];
}

export function learnedCount(progress, kind, ids, readingIds) {
  let learned = 0;
  let total = 0;
  for (const id of ids) {
    for (const track of itemTracks(id, readingIds)) {
      total++;
      const state = mastery(trackRecord(progress, kind, track, id));
      if (state === 'review' || state === 'mastered') {
        learned++;
      }
    }
  }
  return { learned, total };
}

export function dueEntries(progress, data, readingIds, now, level = null) {
  return [...progress.records.values()]
      .filter(record => isDue(record, now) && record.state !== 'new')
      .filter(record => data.byId.has(record.id) && (!level || data.byId.get(record.id).level === level))
      .filter(record => record.track === 'meaning' || readingIds.has(record.id))
      .sort((a, b) => a.dueAt - b.dueAt || a.key.localeCompare(b.key));
}

export function nextDue(progress, data, now) {
  return [...progress.records.values()]
      .filter(record => !isDue(record, now) && data.byId.has(record.id))
      .reduce((soonest, record) => (soonest === null || record.dueAt < soonest ? record.dueAt : soonest), null);
}

export function describeWhen(time, now) {
  if (time <= now) {
    return 'now';
  }
  const minutes = Math.round((time - now) / 60000);
  if (minutes < 60) {
    return `in ${Math.max(1, minutes)} min`;
  }
  const today = new Date(now);
  const target = new Date(time);
  const days = Math.round((new Date(target.getFullYear(), target.getMonth(), target.getDate())
      - new Date(today.getFullYear(), today.getMonth(), today.getDate())) / 86400000);
  if (days <= 0) {
    return `today at ${target.toLocaleTimeString('en', { hour: 'numeric', minute: '2-digit' })}`;
  }
  if (days === 1) {
    return 'tomorrow';
  }
  return `in ${days} days`;
}

export const MASTERY_LABELS = { new: 'Not started', learning: 'Learning', review: 'Learned', mastered: 'Mastered' };
