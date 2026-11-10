import { TRACKS, isValidRecord, isValidTime, newRecord, recordKey, schedule } from './srs.js';

export const DB_NAME = 'kaidoku';
export const DB_VERSION = 1;
export const EXPORT_FORMAT = 'kaidoku-progress';
export const EXPORT_VERSION = 1;
export const MAX_SESSIONS = 200;

const TABLES = ['progress', 'sets', 'sessions', 'meta'];
const DATA_TABLES = ['progress', 'sets', 'sessions'];
const KEY_PATHS = { progress: 'key', sets: 'key', sessions: 'id', meta: 'key' };
const KINDS = ['kanji', 'vocab'];
const LEVELS = ['N5', 'N4', 'N3'];

function plain(value) {
  return value !== null && typeof value === 'object'
      && (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null);
}

function text(value) {
  return typeof value === 'string' && value.length > 0;
}

function count(value) {
  return Number.isInteger(value) && value >= 0;
}

function timestamp(value) {
  return value === null || isValidTime(value);
}

function validScore(value) {
  return value === null || (plain(value) && count(value.correct) && count(value.total)
      && value.correct <= value.total && isValidTime(value.at));
}

function validTrack(value) {
  return plain(value) && count(value.attempts) && validScore(value.last) && validScore(value.best)
      && (value.last === null) === (value.best === null) && (value.last === null || value.attempts > 0);
}

function validSet(value) {
  return plain(value) && KINDS.includes(value.kind) && text(value.setId)
      && value.key === `${value.kind}:${value.setId}` && LEVELS.includes(value.level)
      && timestamp(value.cardsCompletedAt) && TRACKS.every(track => validTrack(value[track]))
      && isValidTime(value.updatedAt);
}

function validSession(value) {
  return plain(value) && text(value.id) && KINDS.includes(value.kind)
      && text(value.scope) && text(value.mode)
      && isValidTime(value.startedAt) && isValidTime(value.finishedAt) && value.startedAt <= value.finishedAt
      && count(value.total) && count(value.correct) && value.correct <= value.total
      && Array.isArray(value.missed)
      && value.missed.every(item => plain(item) && text(item.id) && TRACKS.includes(item.track));
}

const VALIDATORS = { progress: isValidRecord, sets: validSet, sessions: validSession };

function memoryBackend() {
  const tables = new Map(TABLES.map(name => [name, new Map()]));
  let pending = Promise.resolve();
  return {
    persistent: false,
    run(names, mode, work) {
      const operation = pending.then(() => {
        const working = new Map(names.map(name => [name, new Map(tables.get(name))]));
        let result;
        const writable = name => {
          if (mode !== 'readwrite') {
            throw new Error('Readonly transaction');
          }
          return working.get(name);
        };
        work({
          get: (name, key, callback) => callback(structuredClone(working.get(name).get(key))),
          all: (name, callback) => callback(structuredClone([...working.get(name).values()])),
          put: (name, value) => writable(name).set(value[KEY_PATHS[name]], structuredClone(value)),
          delete: (name, key) => writable(name).delete(key),
          clear: name => writable(name).clear(),
          result: value => { result = structuredClone(value); }
        });
        if (mode === 'readwrite') {
          for (const [name, table] of working) {
            tables.set(name, table);
          }
        }
        return result;
      });
      pending = operation.catch(() => {});
      return operation;
    }
  };
}

function indexedBackend(db, onClosed) {
  db.onversionchange = () => {
    db.close();
    onClosed();
  };
  db.onclose = onClosed;
  return {
    persistent: true,
    run(names, mode, work) {
      return new Promise((resolve, reject) => {
        let transaction;
        let result;
        let failure;
        const guard = callback => {
          try {
            callback();
          } catch (error) {
            failure = error;
            try {
              transaction.abort();
            } catch {
              reject(error);
            }
          }
        };
        try {
          transaction = db.transaction(names, mode);
          transaction.oncomplete = () => resolve(result);
          transaction.onabort = () => reject(failure ?? transaction.error ?? new Error('Transaction aborted'));
          transaction.onerror = () => { failure ??= transaction.error; };
          const read = (name, method, key, callback) => {
            const store = transaction.objectStore(name);
            const request = method === 'get' ? store.get(key) : store.getAll();
            request.onsuccess = () => guard(() => callback(request.result));
          };
          guard(() => work({
            get: (name, key, callback) => read(name, 'get', key, callback),
            all: (name, callback) => read(name, 'getAll', undefined, callback),
            put: (name, value) => transaction.objectStore(name).put(value),
            delete: (name, key) => transaction.objectStore(name).delete(key),
            clear: name => transaction.objectStore(name).clear(),
            result: value => { result = structuredClone(value); }
          }));
        } catch (error) {
          reject(error);
        }
      });
    }
  };
}

function readTables(transaction, names, callback) {
  const rows = {};
  let remaining = names.length;
  for (const name of names) {
    transaction.all(name, values => {
      rows[name] = values;
      if (--remaining === 0) {
        callback(rows);
      }
    });
  }
}

function newerScore(current, incoming) {
  return current === null || (incoming !== null && incoming.at > current.at) ? incoming : current;
}

function betterScore(current, incoming) {
  if (current === null || incoming === null) {
    return current ?? incoming;
  }
  const currentRatio = current.total === 0 ? 0 : current.correct / current.total;
  const incomingRatio = incoming.total === 0 ? 0 : incoming.correct / incoming.total;
  return incomingRatio > currentRatio ? incoming
      : incomingRatio === currentRatio ? newerScore(current, incoming) : current;
}

function mergeSet(current, incoming) {
  const merged = { ...(incoming.updatedAt > current.updatedAt ? incoming : current) };
  merged.cardsCompletedAt = current.cardsCompletedAt === null ? incoming.cardsCompletedAt
      : incoming.cardsCompletedAt === null ? current.cardsCompletedAt
      : Math.min(current.cardsCompletedAt, incoming.cardsCompletedAt);
  for (const track of TRACKS) {
    merged[track] = {
      attempts: Math.max(current[track].attempts, incoming[track].attempts),
      last: newerScore(current[track].last, incoming[track].last),
      best: betterScore(current[track].best, incoming[track].best)
    };
  }
  merged.updatedAt = Math.max(current.updatedAt, incoming.updatedAt);
  return merged;
}

function mergeProgress(current, incoming) {
  if (incoming.reps !== current.reps) {
    return incoming.reps > current.reps ? incoming : current;
  }
  return (incoming.lastReviewedAt ?? -1) > (current.lastReviewedAt ?? -1) ? incoming : current;
}

function mergeRows(current, incoming, key, merge) {
  const rows = new Map(current.map(value => [value[key], value]));
  for (const value of incoming) {
    const previous = rows.get(value[key]);
    rows.set(value[key], previous === undefined ? value : merge(previous, value));
  }
  return [...rows.values()];
}

function newestSessions(rows) {
  return rows.sort((a, b) => b.finishedAt - a.finishedAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
}

function mergeSessions(current, incoming) {
  return newestSessions(mergeRows(current, incoming, 'id',
      (previous, next) => next.finishedAt > previous.finishedAt ? next : previous)).slice(0, MAX_SESSIONS);
}

function saveSessions(transaction, current, merged) {
  const previous = new Map(current.map(session => [session.id, session]));
  const retained = new Set(merged.map(session => session.id));
  for (const session of current) {
    if (!retained.has(session.id)) {
      transaction.delete('sessions', session.id);
    }
  }
  for (const session of merged) {
    if (previous.get(session.id) !== session) {
      transaction.put('sessions', session);
    }
  }
}

function createStore(backend, problem) {
  let skipped = 0;
  const validRows = (rows, validate) => {
    const valid = [];
    for (const row of rows) {
      try {
        if (validate(row)) {
          valid.push(structuredClone(row));
          continue;
        }
      } catch {
      }
      skipped++;
    }
    return valid;
  };
  const validRecord = row => row === undefined ? null : validRows([row], isValidRecord)[0] ?? null;
  const readKind = (name, kind) => backend.run([name], 'readonly', transaction => {
    transaction.all(name, rows => {
      const valid = validRows(rows, VALIDATORS[name]).filter(row => kind === undefined || row.kind === kind);
      transaction.result(name === 'sessions' ? newestSessions(valid) : valid);
    });
  });
  const metaValue = (row, key) => {
    if (row === undefined) {
      return null;
    }
    if (!plain(row) || row.key !== key || !Object.hasOwn(row, 'value')) {
      skipped++;
      return null;
    }
    return row.value ?? null;
  };
  return {
    persistent: backend.persistent,
    problem,
    get skipped() { return skipped; },
    records: kind => readKind('progress', kind),
    record(kind, track, id) {
      return backend.run(['progress'], 'readonly', transaction => {
        transaction.get('progress', recordKey(kind, track, id), row => transaction.result(validRecord(row)));
      });
    },
    async grade(specification, grade, now) {
      const initial = newRecord(specification, now);
      return backend.run(['progress'], 'readwrite', transaction => {
        transaction.get('progress', initial.key, row => {
          const saved = schedule(validRecord(row) ?? initial, grade, now);
          transaction.put('progress', saved);
          transaction.result(saved);
        });
      });
    },
    sets: kind => readKind('sets', kind),
    async updateSet(kind, setId, level, update, now) {
      const initial = {
        key: `${kind}:${setId}`, kind, setId, level, cardsCompletedAt: null,
        reading: { attempts: 0, last: null, best: null },
        meaning: { attempts: 0, last: null, best: null }, updatedAt: now
      };
      if (!validSet(initial) || typeof update !== 'function') {
        throw new Error('Invalid set record');
      }
      return backend.run(['sets'], 'readwrite', transaction => {
        transaction.get('sets', initial.key, row => {
          const current = row === undefined ? initial : validRows([row], validSet)[0] ?? initial;
          const updated = update(structuredClone(current));
          if (!plain(updated)) {
            throw new Error('Invalid set record');
          }
          const saved = { ...updated, updatedAt: now };
          if (!validSet(saved) || saved.key !== initial.key || saved.kind !== kind || saved.setId !== setId) {
            throw new Error('Invalid set record');
          }
          transaction.put('sets', saved);
          transaction.result(saved);
        });
      });
    },
    async addSession(session) {
      if (!validSession(session)) {
        throw new Error('Invalid session record');
      }
      const incoming = structuredClone(session);
      return backend.run(['sessions'], 'readwrite', transaction => {
        transaction.all('sessions', rows => {
          const current = validRows(rows, validSession);
          saveSessions(transaction, current, mergeSessions(current, [incoming]));
        });
      });
    },
    sessions: kind => readKind('sessions', kind),
    getMeta(key) {
      return backend.run(['meta'], 'readonly', transaction => {
        transaction.get('meta', key, row => transaction.result(metaValue(row, key)));
      });
    },
    async setMeta(key, value) {
      if (!text(key)) {
        throw new Error('Invalid metadata key');
      }
      const row = structuredClone({ key, value });
      return backend.run(['meta'], 'readwrite', transaction => transaction.put('meta', row));
    },
    exportData(now) {
      return backend.run(TABLES, 'readonly', transaction => {
        readTables(transaction, DATA_TABLES, rows => {
          const data = {
            format: EXPORT_FORMAT, version: EXPORT_VERSION, exportedAt: now,
            datasetVersion: null,
            progress: validRows(rows.progress, isValidRecord),
            sets: validRows(rows.sets, validSet),
            sessions: newestSessions(validRows(rows.sessions, validSession))
          };
          transaction.get('meta', 'datasetVersion', row => {
            data.datasetVersion = metaValue(row, 'datasetVersion');
            transaction.result(data);
          });
        });
      });
    },
    async importData(data, now) {
      if (!plain(data) || data.format !== EXPORT_FORMAT || data.version !== EXPORT_VERSION) {
        throw new Error('Not a Kaidoku progress file');
      }
      const before = skipped;
      const incoming = {};
      for (const name of DATA_TABLES) {
        if (data[name] === undefined) {
          incoming[name] = [];
        } else if (!Array.isArray(data[name])) {
          incoming[name] = [];
          skipped++;
        } else {
          incoming[name] = validRows(data[name], VALIDATORS[name]);
        }
      }
      const result = {
        imported: DATA_TABLES.reduce((total, name) => total + incoming[name].length, 0),
        skipped: skipped - before
      };
      return backend.run(DATA_TABLES, 'readwrite', transaction => {
        readTables(transaction, DATA_TABLES, rows => {
          const current = Object.fromEntries(DATA_TABLES.map(name => [name, validRows(rows[name], VALIDATORS[name])]));
          for (const row of mergeRows(current.progress, incoming.progress, 'key', mergeProgress)) {
            transaction.put('progress', row);
          }
          for (const row of mergeRows(current.sets, incoming.sets, 'key', mergeSet)) {
            transaction.put('sets', row);
          }
          saveSessions(transaction, current.sessions, mergeSessions(current.sessions, incoming.sessions));
          transaction.result(result);
        });
      });
    },
    reset() {
      return backend.run(DATA_TABLES, 'readwrite', transaction => {
        for (const name of DATA_TABLES) {
          transaction.clear(name);
        }
      });
    }
  };
}

function dataError(error) {
  return ['DataError', 'VersionError', 'ConstraintError', 'DataCloneError'].includes(error?.name);
}

function verifySchema(db) {
  const transaction = db.transaction(TABLES, 'readonly');
  for (const name of TABLES) {
    const store = transaction.objectStore(name);
    if (store.keyPath !== KEY_PATHS[name] || store.autoIncrement) {
      throw new Error('Invalid database schema');
    }
    if (name === 'progress' || name === 'sessions') {
      const index = store.index('kind');
      if (index.keyPath !== 'kind' || index.unique || index.multiEntry) {
        throw new Error('Invalid database schema');
      }
    }
  }
}

export function openStore(options = {}) {
  const onClosed = typeof options.onClosed === 'function' ? options.onClosed : () => {};
  return new Promise(resolve => {
    let settled = false;
    let timer;
    let upgradeFailed = false;
    const finish = (backend, problem) => {
      if (!settled) {
        settled = true;
        clearTimeout(timer);
        resolve(createStore(backend, problem));
      }
    };
    const fallback = problem => finish(memoryBackend(), problem);
    try {
      const idb = Object.hasOwn(options, 'idb') ? options.idb : globalThis.indexedDB;
      if (!idb || typeof idb.open !== 'function') {
        fallback('unavailable');
        return;
      }
      const timeoutMs = options.timeoutMs === undefined ? 3000 : options.timeoutMs;
      const request = idb.open(DB_NAME, DB_VERSION);
      timer = setTimeout(() => fallback('blocked'), Number.isFinite(timeoutMs) && timeoutMs >= 0 ? timeoutMs : 3000);
      request.onblocked = () => fallback('blocked');
      request.onerror = event => {
        event?.preventDefault?.();
        fallback(upgradeFailed || dataError(request.error) ? 'corrupt' : 'unavailable');
      };
      request.onupgradeneeded = () => {
        if (settled) {
          try {
            request.transaction.abort();
          } catch {
          }
          return;
        }
        try {
          const db = request.result;
          for (const name of TABLES) {
            const store = db.objectStoreNames.contains(name) ? request.transaction.objectStore(name)
                : db.createObjectStore(name, { keyPath: KEY_PATHS[name] });
            if ((name === 'progress' || name === 'sessions') && !store.indexNames.contains('kind')) {
              store.createIndex('kind', 'kind', { unique: false });
            }
          }
        } catch {
          upgradeFailed = true;
          try {
            request.transaction.abort();
          } catch {
          }
          fallback('corrupt');
        }
      };
      request.onsuccess = () => {
        const db = request.result;
        if (settled) {
          db.close();
          return;
        }
        try {
          verifySchema(db);
          finish(indexedBackend(db, onClosed), null);
        } catch {
          db.close();
          fallback('corrupt');
        }
      };
    } catch (error) {
      fallback(dataError(error) ? 'corrupt' : 'unavailable');
    }
  });
}

let storeRequest;

export function progressStore() {
  storeRequest ??= openStore({ onClosed: () => { storeRequest = undefined; } });
  return storeRequest;
}
