export const SRS = Object.freeze({
  INITIAL_EASE: 2.5,
  MIN_EASE: 1.3,
  EASY_BONUS: 0.15,
  AGAIN_PENALTY: 0.2,
  AGAIN_DELAY_MS: 600000,
  EASY_MULTIPLIER: 1.3,
  MAX_INTERVAL_DAYS: 365,
  DAY_START_HOUR: 4,
  MASTERED_DAYS: 21
});

export const TRACKS = Object.freeze(['reading', 'meaning']);
export const GRADES = Object.freeze(['again', 'good', 'easy']);

export function recordKey(kind, track, id) {
  return `${kind}:${track}:${id}`;
}

export function newRecord({ kind, track, id, level }, now) {
  const record = {
    key: recordKey(kind, track, id), kind, track, id, level,
    state: 'new', ease: SRS.INITIAL_EASE, intervalDays: 0, dueAt: now,
    lastReviewedAt: null, reps: 0, lapses: 0, streak: 0, correct: 0, incorrect: 0,
    createdAt: now, updatedAt: now
  };
  if (!isValidRecord(record)) {
    throw new Error('Invalid progress record');
  }
  return record;
}

export function dayStartAfter(now, days) {
  if (!Number.isFinite(now) || !Number.isInteger(days) || days < 0) {
    throw new Error('Invalid schedule time');
  }
  if (days === 0) {
    return now;
  }
  const date = new Date(now);
  date.setDate(date.getDate() + days);
  date.setHours(SRS.DAY_START_HOUR, 0, 0, 0);
  const dueAt = date.getTime();
  if (!Number.isFinite(dueAt)) {
    throw new Error('Invalid schedule time');
  }
  return dueAt;
}

export function schedule(record, grade, now) {
  if (!GRADES.includes(grade)) {
    throw new Error('Unknown grade');
  }
  if (!isValidRecord(record) || !Number.isFinite(now)) {
    throw new Error('Invalid progress record');
  }
  const next = { ...record, reps: record.reps + 1, lastReviewedAt: now, updatedAt: now };
  if (grade === 'again') {
    next.ease = Math.max(SRS.MIN_EASE, record.ease - SRS.AGAIN_PENALTY);
    next.intervalDays = 0;
    next.dueAt = now + SRS.AGAIN_DELAY_MS;
    next.streak = 0;
    next.incorrect = record.incorrect + 1;
    next.lapses = record.lapses + (record.state === 'review' ? 1 : 0);
    next.state = 'learning';
  } else {
    next.streak = record.streak + 1;
    next.correct = record.correct + 1;
    if (grade === 'easy') {
      next.ease = record.ease + SRS.EASY_BONUS;
      next.intervalDays = Math.min(SRS.MAX_INTERVAL_DAYS,
          Math.round(Math.max(record.intervalDays, 1) * next.ease * SRS.EASY_MULTIPLIER));
      next.state = 'review';
    } else {
      next.intervalDays = record.intervalDays === 0 ? 1
          : Math.min(SRS.MAX_INTERVAL_DAYS, Math.round(record.intervalDays * record.ease));
      next.state = next.streak >= 2 ? 'review' : 'learning';
    }
    next.dueAt = dayStartAfter(now, next.intervalDays);
  }
  if (!isValidRecord(next)) {
    throw new Error('Invalid progress record');
  }
  return next;
}

export function isDue(record, now) {
  return record.dueAt <= now;
}

export function mastery(record) {
  if (record == null) {
    return 'new';
  }
  return record.state === 'review' && record.intervalDays >= SRS.MASTERED_DAYS ? 'mastered' : record.state;
}

const MAX_TIME = 8.64e15;

export function isValidTime(value) {
  return Number.isFinite(value) && value >= 0 && value <= MAX_TIME;
}

export function isValidRecord(value) {
  if (value === null || typeof value !== 'object'
      || (Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null)) {
    return false;
  }
  return ['kanji', 'vocab'].includes(value.kind)
      && TRACKS.includes(value.track)
      && typeof value.id === 'string' && value.id.length > 0
      && ['N5', 'N4', 'N3'].includes(value.level)
      && value.key === recordKey(value.kind, value.track, value.id)
      && ['new', 'learning', 'review'].includes(value.state)
      && Number.isFinite(value.ease) && value.ease >= SRS.MIN_EASE
      && Number.isInteger(value.intervalDays) && value.intervalDays >= 0 && value.intervalDays <= SRS.MAX_INTERVAL_DAYS
      && ['dueAt', 'createdAt', 'updatedAt'].every(key => isValidTime(value[key]))
      && (value.lastReviewedAt === null || isValidTime(value.lastReviewedAt))
      && ['reps', 'lapses', 'streak', 'correct', 'incorrect']
          .every(key => Number.isInteger(value[key]) && value[key] >= 0);
}
