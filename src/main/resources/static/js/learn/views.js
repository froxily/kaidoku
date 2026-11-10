import { badge, h, icon, ICONS, jp } from './dom.js';
import { dataset, LEVELS, lessons, links, search, toHiragana } from './data.js';
import { count, isKanaOnly, itemNoun, kindName, posLabels } from './format.js';
import { QUIZ_SIZE, readingQuestions } from './quiz.js';
import { checkContentVersion, describeWhen, dueEntries, itemTracks, learnedCount, loadProgress, MASTERY_LABELS, nextDue, trackRecord } from './progress.js';
import { mastery } from './srs.js';
import { progressStore } from './store.js';

const PAGE = 60;
const LESSON_WORDS = 4;
const DESCRIPTIONS = {
  kanji: 'Study sets of ten, starting from the most common kanji. Learn with cards, then test readings and meanings.',
  vocab: 'Study sets of about fifteen words per level. Learn with cards, then test readings and meanings.'
};

function storedLevel(kind) {
  try {
    const level = localStorage.getItem(`kaidoku.level.${kind}`);
    return LEVELS.includes(level) ? level : 'N5';
  } catch {
    return 'N5';
  }
}

function rememberLevel(kind, level) {
  try {
    localStorage.setItem(`kaidoku.level.${kind}`, level);
  } catch {
  }
}

function heading(text, props = {}) {
  return h('h1', { tabindex: '-1', ...props, text });
}

function pageHead(eyebrow, title, description, ...extra) {
  return h('div', { class: 'page-head' },
      h('p', { class: 'eyebrow', text: eyebrow }),
      heading(title),
      description ? h('p', { text: description }) : null,
      ...extra);
}

function subnav(kind, active) {
  return h('nav', { class: 'segmented subnav', 'aria-label': `${kindName(kind)} sections` },
      h('a', { href: `/${kind}`, 'aria-current': active === 'lessons' ? 'page' : null, text: 'Study sets' }),
      h('a', { href: `/${kind}/library`, 'aria-current': active === 'library' ? 'page' : null, text: 'Library' }));
}

export function crumb(href, text) {
  return h('a', { class: 'crumb', href }, icon(ICONS.back), text);
}

function highlighted(form, positions, character) {
  const marks = new Set(positions);
  return h('span', { lang: 'ja' }, [...form].map((char, index) =>
      marks.has(index) && char === character ? h('b', { class: 'hl', text: char }) : char));
}

function wordsForKanji(character, vocab, link) {
  const byWord = new Map();
  for (const entry of link.kanji_to_vocab[character] ?? []) {
    const word = vocab.byId.get(entry.vocab_id);
    if (word && (!byWord.has(word.id) || entry.relation === 'headword')) {
      byWord.set(word.id, { word, entry });
    }
  }
  return [...byWord.values()].sort((a, b) =>
      LEVELS.indexOf(a.word.level) - LEVELS.indexOf(b.word.level) || a.word.order - b.word.order);
}

function kanjiInWord(word, kanji, link) {
  const seen = new Set();
  return (link.vocab_to_kanji[word.id] ?? [])
      .slice()
      .sort((a, b) => Math.min(...a.positions) - Math.min(...b.positions))
      .map(entry => kanji.byId.get(entry.kanji))
      .filter(item => item && !seen.has(item.id) && seen.add(item.id));
}

export function wordTitle(word, tag = 'h1') {
  const props = { lang: 'ja', class: 'word-title', tabindex: '-1' };
  if (word.word === word.reading || isKanaOnly(word.word)) {
    return h(tag, { ...props, text: word.word });
  }
  const [before, base, ruby, after] = splitRuby(word.word, word.reading_hiragana);
  return h(tag, props, before, h('ruby', {}, base, h('rt', { text: ruby })), after);
}

function splitRuby(word, reading) {
  const chars = [...word];
  const kana = [...reading];
  const same = (char, sound) => isKanaOnly(char) && toHiragana(char) === sound;
  let start = 0;
  while (start < chars.length - 1 && start < kana.length - 1 && same(chars[start], kana[start])) {
    start++;
  }
  let end = 0;
  while (end < chars.length - start - 1 && end < kana.length - start - 1
      && same(chars[chars.length - 1 - end], kana[kana.length - 1 - end])) {
    end++;
  }
  return [chars.slice(0, start).join(''), chars.slice(start, chars.length - end).join(''),
    kana.slice(start, kana.length - end).join(''), chars.slice(chars.length - end).join('')];
}

function chips(values, className = 'chip') {
  return h('ul', { class: 'chips' }, values.map(value => h('li', { class: className, text: value })));
}

export function readingRows(item) {
  return h('dl', { class: 'readings' },
      h('div', {}, h('dt', { lang: 'ja', title: "On'yomi", text: '音' }),
          h('dd', { lang: 'ja', text: item.onyomi_display.join('、') || '—' })),
      h('div', {}, h('dt', { lang: 'ja', title: "Kun'yomi", text: '訓' }),
          h('dd', { lang: 'ja', text: item.kunyomi_display.join('、') || '—' })));
}

function gradeLabel(grade) {
  if (grade === null || grade === undefined) {
    return '—';
  }
  if (grade <= 6) {
    return `Elementary grade ${grade}`;
  }
  return grade === 8 ? 'Secondary school' : 'Name kanji';
}

function kanjiTile(item) {
  return h('li', {}, h('a', { class: 'kanji-tile', href: `/kanji/${encodeURIComponent(item.id)}` },
      jp('span', item.character, { class: 'tile-glyph' }),
      h('span', { class: 'tile-meaning', text: item.meanings[0] }),
      h('span', { class: `level-dot ${item.level}`, title: item.level },
          h('span', { class: 'visually-hidden', text: item.level }))));
}

function wordRow(item) {
  const sameAsWord = item.word === item.reading;
  return h('li', {}, h('a', { class: 'word-row', href: `/vocab/${item.id}` },
      h('span', { class: 'word-main' },
          jp('span', item.word, { class: 'word-text' }),
          sameAsWord ? null : jp('span', item.reading, { class: 'word-reading' })),
      h('span', { class: 'word-meaning', text: item.meanings.slice(0, 3).join('; ') }),
      badge(item.level)));
}

function kanjiCard(item) {
  return h('li', {}, h('a', { class: 'kanji-card', href: `/kanji/${encodeURIComponent(item.id)}` },
      jp('span', item.character, { class: 'card-glyph' }),
      h('span', { class: 'card-body' },
          h('span', { class: 'card-meaning', text: item.meanings.slice(0, 3).join(', ') }),
          jp('span', [...item.onyomi_display, ...item.kunyomi_display].slice(0, 4).join('、'), { class: 'card-readings' })),
      badge(item.level)));
}

function linkedWordRow({ word, entry }, character) {
  const form = entry.relation === 'other_form' ? entry.source_form : word.word;
  return h('li', {}, h('a', { class: 'word-row', href: `/vocab/${word.id}` },
      h('span', { class: 'word-main' },
          h('span', { class: 'word-text' }, highlighted(form, entry.positions, character)),
          form === word.reading ? null : jp('span', word.reading, { class: 'word-reading' })),
      h('span', { class: 'word-meaning', text: word.meanings.slice(0, 3).join('; ') }),
      badge(word.level)));
}

function levelTabs(kind, level) {
  return h('nav', { class: 'segmented', 'aria-label': 'Level' },
      LEVELS.map(each => h('a', { href: `/${kind}?level=${each}`, 'aria-current': each === level ? 'true' : null, text: each })));
}

function meter(learned, total, label) {
  const fill = h('span', {});
  fill.style.width = `${total ? (learned / total) * 100 : 0}%`;
  return h('span', {
    class: 'meter', role: 'meter', 'aria-label': label,
    'aria-valuemin': '0', 'aria-valuemax': String(total), 'aria-valuenow': String(learned)
  }, fill);
}

function lessonCard(kind, lesson, data, progress, readingIds) {
  const items = lesson.itemIds.map(id => data.byId.get(id));
  const preview = kind === 'kanji'
      ? jp('span', items.map(item => item.character).join(''), { class: 'lesson-glyphs' })
      : jp('span', items.slice(0, 5).map(item => item.word).join('・') + (items.length > 5 ? '…' : ''), { class: 'lesson-words' });
  const { learned, total } = learnedCount(progress, kind, lesson.itemIds, readingIds);
  const set = progress.sets.get(lesson.id);
  const started = learned > 0 || set;
  return h('li', {}, h('a', { class: 'lesson-card', href: `/${kind}/lesson/${lesson.id}` },
      h('span', { class: 'lesson-title' }, lesson.title,
          set?.cardsCompletedAt ? h('span', { class: 'set-flag', title: 'Cards studied' }, icon(ICONS.check), h('span', { class: 'visually-hidden', text: ' (cards studied)' })) : null),
      preview,
      h('span', { class: 'lesson-count', text: started ? `${learned} of ${total} learned · ${itemNoun(kind, items.length)}` : itemNoun(kind, items.length) }),
      meter(learned, total, `${lesson.title}: ${learned} of ${total} learned`)));
}

function storageNotices(store, versionChanged) {
  return [
    store.persistent ? null : h('p', { class: 'notice error', role: 'status',
      text: 'Progress can’t be saved in this browser (storage is blocked or unavailable, for example in a private window). You can still study, but answers are kept only until you close this tab.' }),
    store.skipped ? h('p', { class: 'notice', role: 'status',
      text: 'Some saved progress could not be read and was ignored. The rest of your progress is intact.' }) : null,
    versionChanged ? h('p', { class: 'notice', role: 'status',
      text: 'The study content was updated. Your progress is kept; entries that no longer exist are hidden.' }) : null
  ];
}

function trackProgress(progress, kind, items, track) {
  const relevant = items.filter(item => track === 'meaning' || item.hasReading);
  const states = relevant.map(item => mastery(trackRecord(progress, kind, track, item.id)));
  return {
    learned: states.filter(state => state === 'review' || state === 'mastered').length,
    learning: states.filter(state => state === 'learning').length,
    total: relevant.length
  };
}

function reviewPanel(kind, level, progress, data, readingIds, now) {
  const all = dueEntries(progress, data, readingIds, now);
  const here = dueEntries(progress, data, readingIds, now, level);
  const soonest = nextDue(progress, data, now);
  const text = all.length
      ? `${count(all.length, 'review')} due now${here.length !== all.length ? `, ${here.length} in ${level}` : ''}.`
      : soonest !== null ? `Nothing due. Next review ${describeWhen(soonest, now)}.`
          : 'Take a reading quiz or meaning check. Everything you answer is scheduled here for review.';
  return h('section', { class: 'review-panel', 'aria-labelledby': 'review-title' },
      h('div', {},
          h('h2', { id: 'review-title', class: 'card-kicker', text: 'Review' }),
          h('p', { class: 'muted', role: 'status', text })),
      h('div', { class: 'level-quiz-actions' },
          here.length ? h('a', { class: 'btn', href: `/${kind}/review?level=${level}` }, `Review ${level} (${here.length})`) : null,
          all.length > here.length ? h('a', { class: `btn ${here.length ? 'btn-secondary' : ''}`, href: `/${kind}/review` }, `Review all (${all.length})`) : null));
}

function dataPanel(store) {
  const status = h('p', { class: 'muted data-status', role: 'status' });
  const file = h('input', { type: 'file', accept: 'application/json,.json', class: 'visually-hidden', id: 'import-file', tabindex: '-1' });
  const refresh = () => window.dispatchEvent(new PopStateEvent('popstate', { state: history.state }));
  file.addEventListener('change', async () => {
    const chosen = file.files?.[0];
    file.value = '';
    if (!chosen) {
      return;
    }
    try {
      const data = JSON.parse(await chosen.text());
      const result = await store.importData(data, Date.now());
      status.textContent = `Restored ${count(result.imported, 'record')}${result.skipped ? `; ${result.skipped} invalid ${result.skipped === 1 ? 'entry was' : 'entries were'} skipped` : ''}. Newer progress already in this browser was kept.`;
      setTimeout(refresh, 1200);
    } catch (error) {
      status.textContent = error instanceof SyntaxError || /Kaidoku progress file/.test(error.message)
          ? 'That file is not a Kaidoku progress backup. Nothing was changed.'
          : 'The backup could not be restored. Nothing was changed.';
    }
  });
  return h('section', { class: 'data-panel detail-section', 'aria-labelledby': 'data-title' },
      h('div', { class: 'section-head' },
          h('h2', { id: 'data-title', text: 'Your progress data' }),
          h('p', { text: 'Stored only in this browser, for kanji and vocabulary together. Back it up to a file to keep it safe or move it to another device.' })),
      h('div', { class: 'lesson-actions' },
          h('button', { type: 'button', class: 'btn btn-secondary', dataset: { role: 'export' }, on: { click: async () => {
            const data = await store.exportData(Date.now());
            const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const link = h('a', { href: url, download: `kaidoku-progress-${new Date().toISOString().slice(0, 10)}.json` });
            document.body.append(link);
            link.click();
            link.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
            status.textContent = `Saved a backup with ${count(data.progress.length, 'review record')}.`;
          } } }, 'Back up to file'),
          h('button', { type: 'button', class: 'btn btn-secondary', dataset: { role: 'import' }, on: { click: () => file.click() } }, 'Restore from file'),
          h('button', { type: 'button', class: 'btn btn-quiet', dataset: { role: 'reset' }, on: { click: async () => {
            if (!window.confirm('Delete all saved kanji and vocabulary progress in this browser? Back it up first if you might want it later. This cannot be undone.')) {
              return;
            }
            await store.reset();
            status.textContent = 'Progress cleared.';
            setTimeout(refresh, 800);
          } } }, 'Reset progress')),
      file,
      status);
}

export async function homeView({ kind, query }) {
  const level = LEVELS.includes(query.get('level')) ? query.get('level') : storedLevel(kind);
  rememberLevel(kind, level);
  const [data, plan, progress, version] = await Promise.all([dataset(kind), lessons(kind), loadProgress(kind), checkContentVersion()]);
  const readingIds = new Set((await readingQuestions(kind, data.items)).keys());
  const levelLessons = plan.byLevel.get(level);
  const total = levelLessons.reduce((sum, lesson) => sum + lesson.itemIds.length, 0);
  const now = Date.now();
  const levelItems = data.items.filter(item => item.level === level).map(item => ({ id: item.id, hasReading: readingIds.has(item.id) }));
  const reading = trackProgress(progress, kind, levelItems, 'reading');
  const meaning = trackProgress(progress, kind, levelItems, 'meaning');
  const levelProgress = h('section', { class: 'level-progress', 'aria-labelledby': 'level-progress-title' },
      h('h2', { id: 'level-progress-title', class: 'card-kicker', text: `${level} progress` }),
      h('div', { class: 'progress-row' },
          h('span', { class: 'progress-label', text: 'Reading' }),
          meter(reading.learned, reading.total, `${level} reading: ${reading.learned} of ${reading.total} learned`),
          h('span', { class: 'progress-value', text: `${reading.learned} / ${reading.total}${reading.learning ? ` · ${reading.learning} in progress` : ''}` })),
      h('div', { class: 'progress-row' },
          h('span', { class: 'progress-label', text: 'Meaning' }),
          meter(meaning.learned, meaning.total, `${level} meaning: ${meaning.learned} of ${meaning.total} learned`),
          h('span', { class: 'progress-value', text: `${meaning.learned} / ${meaning.total}${meaning.learning ? ` · ${meaning.learning} in progress` : ''}` })),
      h('p', { class: 'muted progress-help', text: 'An item counts as learned after two successful reviews on different days.' }));

  const scope = level.toLowerCase();
  const levelQuiz = h('section', { class: 'level-quiz', 'aria-labelledby': 'level-quiz-title' },
      h('div', {},
          h('h2', { id: 'level-quiz-title', text: `Quiz the whole of ${level}` }),
          h('p', { class: 'muted', text: `${QUIZ_SIZE} random items from all ${itemNoun(kind, total)}.` })),
      h('div', { class: 'level-quiz-actions' },
          h('a', { class: 'btn btn-secondary', href: `/${kind}/quiz/${scope}/reading` }, 'Reading quiz'),
          h('a', { class: 'btn btn-secondary', href: `/${kind}/quiz/${scope}/meaning` }, 'Meaning check')));
  const body = h('div', {},
      ...storageNotices(progress.store, version.changed),
      h('div', { class: 'status-grid' }, reviewPanel(kind, level, progress, data, readingIds, now), levelProgress),
      levelQuiz,
      h('h2', { class: 'sets-title', text: `${level} study sets` }),
      h('ol', { class: 'lesson-grid' }, levelLessons.map(lesson => lessonCard(kind, lesson, data, progress, readingIds))),
      dataPanel(progress.store));

  return {
    title: `${kindName(kind)} study sets`,
    node: h('div', {},
        pageHead(kind === 'kanji' ? 'Module 02' : 'Module 03', `${kindName(kind)} Learning`, DESCRIPTIONS[kind],
            subnav(kind, 'lessons')),
        h('div', { class: 'toolbar' },
            levelTabs(kind, level),
            h('p', { class: 'toolbar-note', text: `${itemNoun(kind, total)} · ${count(levelLessons.length, 'set')}` })),
        body)
  };
}

export async function libraryView({ kind, query, signal }) {
  const data = await dataset(kind);
  let text = query.get('q') ?? '';
  let level = LEVELS.includes(query.get('level')) ? query.get('level') : '';
  let shown = Math.max(PAGE, Number(history.state?.shown) || PAGE);
  let timer = 0;
  signal.addEventListener('abort', () => clearTimeout(timer));

  const input = h('input', {
    type: 'search', id: 'library-search', class: 'search-input', value: text, autocomplete: 'off',
    spellcheck: 'false', enterkeyhint: 'search',
    placeholder: kind === 'kanji' ? 'Kanji, reading (kana or romaji) or meaning' : 'Word, reading, romaji or meaning'
  });
  const status = h('p', { class: 'result-count', role: 'status' });
  const results = h('ul', { class: kind === 'kanji' ? 'kanji-grid' : 'word-list' });
  const more = h('button', { type: 'button', class: 'btn btn-secondary more' });
  const filters = ['', ...LEVELS].map(each => h('button', {
    type: 'button', 'aria-pressed': String(each === level), text: each || 'All', dataset: { level: each }
  }));

  function update() {
    if (signal.aborted) {
      return;
    }
    const found = search(data.items, text, level);
    status.textContent = text.trim() ? `${itemNoun(kind, found.length)} found` : itemNoun(kind, found.length);
    results.replaceChildren(...(found.length === 0
        ? [h('li', { class: 'empty-state', text: `Nothing matches “${text.trim()}”${level ? ` in ${level}` : ''}.` })]
        : found.slice(0, shown).map(kind === 'kanji' ? kanjiTile : wordRow)));
    more.hidden = found.length <= shown;
    more.textContent = `Show more (${(found.length - shown).toLocaleString('en')} left)`;
    const params = new URLSearchParams();
    if (text.trim()) {
      params.set('q', text.trim());
    }
    if (level) {
      params.set('level', level);
    }
    const next = `/${kind}/library${params.size ? `?${params}` : ''}`;
    history.replaceState({ ...history.state, shown }, '', next);
  }

  input.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      text = input.value;
      shown = PAGE;
      update();
    }, 90);
  }, { signal });
  filters.forEach(button => button.addEventListener('click', () => {
    level = button.dataset.level;
    filters.forEach(other => other.setAttribute('aria-pressed', String(other === button)));
    shown = PAGE;
    update();
  }, { signal }));
  more.addEventListener('click', () => {
    shown += PAGE;
    update();
  }, { signal });
  update();

  return {
    title: `${kindName(kind)} library`,
    node: h('div', {},
        pageHead(kind === 'kanji' ? 'Module 02' : 'Module 03', `${kindName(kind)} library`,
            `Every ${kind === 'kanji' ? 'kanji' : 'word'} from N5 to N3. Search in Japanese or English.`,
            subnav(kind, 'library')),
        h('div', { class: 'library-controls' },
            h('label', { class: 'search-field', for: 'library-search' },
                icon(ICONS.search),
                h('span', { class: 'visually-hidden', text: `Search ${kind === 'kanji' ? 'kanji' : 'vocabulary'}` }),
                input),
            h('div', { class: 'segmented', role: 'group', 'aria-label': 'Filter by level' }, filters)),
        status,
        results,
        h('div', { class: 'more-row' }, more))
  };
}

async function progressSection(kind, item) {
  const [progress, questions] = await Promise.all([loadProgress(kind), readingQuestions(kind, [item])]);
  const now = Date.now();
  const rows = itemTracks(item.id, new Set(questions.keys())).map(track => {
    const record = trackRecord(progress, kind, track, item.id);
    const state = mastery(record);
    const when = record && state !== 'new' ? ` · next review ${describeWhen(record.dueAt, now)}` : '';
    return h('div', {}, h('dt', { text: track === 'reading' ? 'Reading' : 'Meaning' }),
        h('dd', { text: `${MASTERY_LABELS[state]}${when}${record?.reps ? ` · ${record.correct} of ${record.reps} right` : ''}` }));
  });
  return h('section', { class: 'detail-section', 'aria-labelledby': 'progress-title' },
      h('div', { class: 'section-head' }, h('h2', { id: 'progress-title', text: 'Your progress' })),
      h('dl', { class: 'facts progress-facts' }, rows));
}

export async function kanjiDetailView({ params }) {
  const [kanji, vocab, link] = await Promise.all([dataset('kanji'), dataset('vocab'), links()]);
  const item = kanji.byId.get(params.id);
  if (!item) {
    return notFoundView({ kind: 'kanji' });
  }
  const words = wordsForKanji(item.id, vocab, link);
  const external = link.kanji_external_words[item.id] ?? [];
  const yours = await progressSection('kanji', item);
  const facts = [
    ['Strokes', String(item.strokes)],
    ['School grade', gradeLabel(item.grade)],
    ['Radical', `${item.radical} · No. ${item.radical_number}`],
    ['Frequency', item.freq ? `#${item.freq} in newspapers` : 'Not ranked']
  ];

  return {
    title: `${item.character} · Kanji`,
    node: h('article', { class: 'detail' },
        crumb('/kanji/library', 'Kanji library'),
        h('div', { class: 'detail-hero panel' },
            h('div', { class: 'hero-glyph' }, heading(item.character, { lang: 'ja', class: 'glyph-title' })),
            h('div', { class: 'hero-info' },
                h('div', { class: 'hero-tags' }, badge(item.level), h('span', { class: 'muted', text: 'Kanji' })),
                h('p', { class: 'hero-meanings', text: item.meanings.join(', ') }),
                readingRows(item),
                h('dl', { class: 'facts' }, facts.map(([term, value]) =>
                    h('div', {}, h('dt', { text: term }), h('dd', { lang: term === 'Radical' ? 'ja' : null, text: value })))),
                item.nanori?.length ? h('details', { class: 'nanori' },
                    h('summary', { text: 'Name readings (nanori)' }), jp('p', item.nanori.join('、'))) : null)),
        yours,
        h('section', { class: 'detail-section', 'aria-labelledby': 'words-title' },
            h('div', { class: 'section-head' },
                h('h2', { id: 'words-title' }, 'Words with ', jp('span', item.character)),
                h('p', { text: words.length ? itemNoun('vocab', words.length) : 'No N5–N3 words use this kanji yet.' })),
            words.length ? h('ul', { class: 'word-list' }, words.map(entry => linkedWordRow(entry, item.id))) : null),
        external.length ? h('section', { class: 'detail-section', 'aria-labelledby': 'external-title' },
            h('div', { class: 'section-head' },
                h('h2', { id: 'external-title', text: 'Higher-level words' }),
                h('p', { text: 'N2–N1 words with this kanji, for reference. They are not part of the N5–N3 study set.' })),
            h('ul', { class: 'chips' }, external.map(word =>
                h('li', { class: 'chip chip-ext' }, jp('span', word.word), h('span', { class: 'chip-level', text: word.level }))))) : null)
  };
}

export async function vocabDetailView({ params }) {
  const [vocab, kanji, link] = await Promise.all([dataset('vocab'), dataset('kanji'), links()]);
  const item = vocab.byId.get(params.id);
  if (!item) {
    return notFoundView({ kind: 'vocab' });
  }
  const parts = kanjiInWord(item, kanji, link);
  const yours = await progressSection('vocab', item);
  const alternatives = [
    item.other_forms?.length ? ['Also written', item.other_forms.join('、')] : null,
    item.other_readings?.length ? ['Also read', item.other_readings.join('、')] : null
  ].filter(Boolean);

  return {
    title: `${item.word} · Vocabulary`,
    node: h('article', { class: 'detail' },
        crumb('/vocab/library', 'Vocabulary library'),
        h('div', { class: 'detail-hero panel word-hero' },
            h('div', { class: 'hero-word' }, wordTitle(item), h('p', { class: 'romaji', text: item.romaji })),
            h('div', { class: 'hero-info' },
                h('div', { class: 'hero-tags' }, badge(item.level), chips(posLabels(item.pos))),
                h('ol', { class: 'meanings' }, item.meanings.map(meaning => h('li', { text: meaning }))),
                alternatives.length ? h('dl', { class: 'facts' }, alternatives.map(([term, value]) =>
                    h('div', {}, h('dt', { text: term }), jp('dd', value)))) : null)),
        yours,
        h('section', { class: 'detail-section', 'aria-labelledby': 'kanji-title' },
            h('div', { class: 'section-head' },
                h('h2', { id: 'kanji-title', text: 'Kanji in this word' }),
                h('p', { text: parts.length ? itemNoun('kanji', parts.length) : 'This word is written in kana only.' })),
            parts.length ? h('ul', { class: 'kanji-cards' }, parts.map(kanjiCard)) : null))
  };
}

function kanjiStudy(item, vocab, link) {
  const words = wordsForKanji(item.id, vocab, link).slice(0, LESSON_WORDS);
  return h('div', { class: 'study study-kanji' },
      h('div', { class: 'study-glyph' }, jp('h2', item.character, { tabindex: '-1', class: 'glyph-title' })),
      h('div', { class: 'study-info' },
          h('p', { class: 'hero-meanings', text: item.meanings.join(', ') }),
          readingRows(item),
          h('p', { class: 'muted', text: `${count(item.strokes, 'stroke')} · ${item.level}` }),
          words.length ? h('div', { class: 'study-words' },
              h('h3', { text: 'Common words' }),
              h('ul', { class: 'word-list compact' }, words.map(entry => linkedWordRow(entry, item.id)))) : null,
          h('a', { class: 'text-link', href: `/kanji/${encodeURIComponent(item.id)}` }, 'Full entry', icon(ICONS.arrow))));
}

function vocabStudy(item, kanji, link) {
  const parts = kanjiInWord(item, kanji, link);
  return h('div', { class: 'study study-vocab' },
      h('div', { class: 'study-glyph' }, wordTitle(item, 'h2'), h('p', { class: 'romaji', text: item.romaji })),
      h('div', { class: 'study-info' },
          h('ol', { class: 'meanings' }, item.meanings.map(meaning => h('li', { text: meaning }))),
          chips(posLabels(item.pos)),
          parts.length ? h('div', { class: 'study-words' },
              h('h3', { text: 'Kanji' }),
              h('ul', { class: 'kanji-cards compact' }, parts.map(kanjiCard))) : null,
          h('a', { class: 'text-link', href: `/vocab/${item.id}` }, 'Full entry', icon(ICONS.arrow))));
}

export async function lessonView({ kind, params, query, signal }) {
  const otherKind = kind === 'kanji' ? 'vocab' : 'kanji';
  const [plan, data, other, link] = await Promise.all([lessons(kind), dataset(kind), dataset(otherKind), links()]);
  const lesson = plan.byId.get(params.id);
  if (!lesson) {
    return notFoundView({ kind });
  }
  const items = lesson.itemIds.map(id => data.byId.get(id));
  const [questions, progress] = await Promise.all([readingQuestions(kind, items), loadProgress(kind)]);
  const questionCount = questions.size;
  let setRecord = progress.sets.get(lesson.id) ?? null;
  const setLearned = learnedCount(progress, kind, lesson.itemIds, new Set(questions.keys()));
  const siblings = plan.byLevel.get(lesson.level);
  const index = siblings.indexOf(lesson);
  const nextLesson = siblings[index + 1];
  const label = `${kind === 'kanji' ? 'Kanji' : 'Vocabulary'} ${lesson.title.toLowerCase()}`;
  const lessonsHref = `/${kind}?level=${lesson.level}`;
  const quizHref = mode => `/${kind}/quiz/${lesson.id}/${mode}`;
  const without = items.length - questionCount;
  const card = query.get('card');
  const requested = Number.parseInt(card ?? '', 10);
  let step = card === 'done' ? items.length
      : Number.isInteger(requested) && requested >= 1 ? Math.min(requested, items.length) - 1 : -1;
  const content = h('div', {});
  const live = h('p', { class: 'visually-hidden', 'aria-live': 'polite' });
  const root = h('div', { class: 'lesson' }, content, live);

  function sync() {
    const next = step < 0 ? location.pathname : `${location.pathname}?card=${step >= items.length ? 'done' : step + 1}`;
    if (next !== location.pathname + location.search) {
      history.replaceState(history.state, '', next);
    }
  }

  function phase() {
    return step < 0 ? 'intro' : step >= items.length ? 'done' : 'card';
  }

  function go(to) {
    const role = document.activeElement?.dataset?.role;
    const before = phase();
    step = Math.max(-1, Math.min(to, items.length));
    render();
    live.replaceChildren(...(phase() === 'card' ? [`${step + 1} of ${items.length}: `, jp('span', itemLabel(items[step]))] : []));
    const same = role && phase() === before ? content.querySelector(`[data-role="${role}"]`) : null;
    (same ?? content.querySelector('[tabindex="-1"]'))?.focus({ preventScroll: true });
    window.scrollTo({ top: 0 });
  }

  function itemLabel(item) {
    return kind === 'kanji' ? item.character : item.word;
  }

  function scoreNote(track) {
    const result = setRecord?.[track];
    return result?.last && result?.best ? `Last ${result.last.correct}/${result.last.total} · best ${result.best.correct}/${result.best.total}` : null;
  }

  function activities() {
    return h('ul', { class: 'activities' },
        h('li', {}, h('button', { type: 'button', class: 'activity', on: { click: () => go(0) } },
            h('span', { class: 'activity-name', text: 'Study cards' }),
            h('span', { class: 'activity-note', text: setRecord?.cardsCompletedAt ? `Studied · ${itemNoun(kind, items.length)}` : `Go through ${itemNoun(kind, items.length)} at your own pace` }),
            icon(ICONS.arrow))),
        h('li', {}, questionCount
            ? h('a', { class: 'activity', href: quizHref('reading') },
                h('span', { class: 'activity-name', text: 'Reading quiz' }),
                h('span', { class: 'activity-note', text: scoreNote('reading') ?? `${count(questionCount, 'question')}, multiple choice` }),
                icon(ICONS.arrow))
            : h('div', { class: 'activity is-disabled' },
                h('span', { class: 'activity-name', text: 'Reading quiz' }),
                h('span', { class: 'activity-note', text: 'No approved reading questions in this set' }))),
        h('li', {}, h('a', { class: 'activity', href: quizHref('meaning') },
            h('span', { class: 'activity-name', text: 'Meaning check' }),
            h('span', { class: 'activity-note', text: scoreNote('meaning') ?? 'Recall each meaning, then grade yourself' }),
            icon(ICONS.arrow))));
  }

  function intro() {
    return h('div', { class: 'lesson-intro' },
        crumb(lessonsHref, `${lesson.level} study sets`),
        h('div', { class: 'page-head' },
            h('p', { class: 'eyebrow', text: `${lesson.level} · Set ${index + 1} of ${siblings.length}` }),
            heading(label),
            h('p', { text: without
                ? `${itemNoun(kind, items.length)}. ${itemNoun(kind, without)} ${without === 1 ? 'has' : 'have'} no approved reading question (${kind === 'vocab' ? 'kana-only or more than one accepted reading' : 'not covered by the release'}), so ${without === 1 ? 'it appears' : 'they appear'} only in the cards and meaning check.`
                : `${itemNoun(kind, items.length)}.` })),
        h('ul', { class: kind === 'kanji' ? 'preview-glyphs' : 'chips preview-words', lang: 'ja' },
            items.map(item => h('li', { class: kind === 'kanji' ? null : 'chip', text: itemLabel(item) }))),
        h('div', { class: 'progress-row set-progress' },
            h('span', { class: 'progress-label', text: 'Learned' }),
            meter(setLearned.learned, setLearned.total, `${label}: ${setLearned.learned} of ${setLearned.total} learned`),
            h('span', { class: 'progress-value', text: `${setLearned.learned} / ${setLearned.total}` })),
        activities());
  }

  function studyCard() {
    const item = items[step];
    return h('div', { class: 'lesson-card-view' },
        h('div', { class: 'lesson-top' },
            crumb(lessonsHref, label),
            h('p', { class: 'lesson-counter', text: `${step + 1} / ${items.length}` })),
        progressBar(),
        h('div', { class: 'panel study-panel' },
            kind === 'kanji' ? kanjiStudy(item, other, link) : vocabStudy(item, other, link)),
        h('div', { class: 'lesson-nav' },
            h('button', { type: 'button', class: 'btn btn-secondary', dataset: { role: 'prev' }, on: { click: () => go(step - 1) } }, icon(ICONS.back), step === 0 ? 'Overview' : 'Previous'),
            h('p', { class: 'muted key-hint', text: 'Use ← and → to move' }),
            h('button', { type: 'button', class: 'btn', dataset: { role: 'next' }, on: { click: () => go(step + 1) } }, step === items.length - 1 ? 'Finish' : 'Next', icon(ICONS.next))));
  }

  function progressBar() {
    const fill = h('span', {});
    fill.style.width = `${((step + 1) / items.length) * 100}%`;
    return h('div', {
      class: 'progress', role: 'progressbar', 'aria-label': 'Card progress',
      'aria-valuemin': '0', 'aria-valuemax': String(items.length), 'aria-valuenow': String(step + 1)
    }, fill);
  }

  function markCardsDone() {
    if (setRecord?.cardsCompletedAt) {
      return;
    }
    const now = Date.now();
    progressStore()
        .then(store => store.updateSet(kind, lesson.id, lesson.level, set => ({ ...set, cardsCompletedAt: set.cardsCompletedAt ?? now }), now))
        .then(saved => { setRecord = saved; })
        .catch(() => {});
  }

  function done() {
    markCardsDone();
    return h('div', { class: 'lesson-done' },
        h('div', { class: 'done-mark', 'aria-hidden': 'true' }, icon(ICONS.check)),
        h('div', { class: 'page-head centered' },
            h('p', { class: 'eyebrow', text: `${lesson.level} · Set ${index + 1} of ${siblings.length}` }),
            heading('Cards complete'),
            h('p', { text: `You went through ${itemNoun(kind, items.length)}. Test yourself next.` })),
        h('ul', { class: kind === 'kanji' ? 'preview-glyphs' : 'chips preview-words' },
            items.map(item => h('li', { class: kind === 'kanji' ? null : 'chip' },
                h('a', { href: kind === 'kanji' ? `/kanji/${encodeURIComponent(item.id)}` : `/vocab/${item.id}`, lang: 'ja', text: itemLabel(item) })))),
        h('div', { class: 'lesson-actions centered' },
            questionCount ? h('a', { class: 'btn', href: quizHref('reading') }, 'Reading quiz', icon(ICONS.arrow)) : null,
            h('a', { class: `btn ${questionCount ? 'btn-secondary' : ''}`, href: quizHref('meaning') }, 'Meaning check'),
            h('button', { type: 'button', class: 'btn btn-secondary', on: { click: () => go(0) } }, icon(ICONS.replay), 'Study again'),
            nextLesson ? h('a', { class: 'btn btn-quiet', href: `/${kind}/lesson/${nextLesson.id}` }, 'Next set', icon(ICONS.next)) : null));
  }

  function render() {
    content.replaceChildren(step < 0 ? intro() : step >= items.length ? done() : studyCard());
    sync();
  }

  document.addEventListener('keydown', event => {
    if (event.altKey || event.ctrlKey || event.metaKey || event.target.closest?.('input, textarea, select')) {
      return;
    }
    if (event.key === 'ArrowRight' && step < items.length) {
      event.preventDefault();
      go(step + 1);
    } else if (event.key === 'ArrowLeft' && step >= 0) {
      event.preventDefault();
      go(step - 1);
    }
  }, { signal });
  render();

  return { title: `${label} · ${lesson.level}`, node: root };
}

export function notFoundView({ kind }) {
  return {
    title: 'Not found',
    node: h('div', { class: 'empty-state not-found' },
        heading('Not found'),
        h('p', { text: 'That page does not exist, or the entry is not part of the N5–N3 set.' }),
        h('a', { class: 'btn', href: `/${kind}` }, `Back to ${kindName(kind).toLowerCase()}`))
  };
}

export function errorView(retry) {
  return h('div', { class: 'empty-state' },
      heading('Could not load the learning data'),
      h('p', { text: 'Check your connection and try again.' }),
      h('button', { type: 'button', class: 'btn', on: { click: retry }, text: 'Try again' }));
}
