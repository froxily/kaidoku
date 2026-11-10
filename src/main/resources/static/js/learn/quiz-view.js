import { h, icon, ICONS, jp } from './dom.js';
import { dataset, LEVELS, lessons } from './data.js';
import { kindName } from './format.js';
import { describeWhen, dueEntries, loadProgress, nextDue } from './progress.js';
import { isCorrect, prepare, QUIZ_SIZE, readingQuestions, sample, score, shuffle } from './quiz.js';
import { progressStore } from './store.js';
import { crumb, notFoundView, readingRows, wordTitle } from './views.js';

const MODE_NAMES = { reading: 'Reading quiz', meaning: 'Meaning check', review: 'Review' };
const ICON_CROSS = 'M6 6l12 12M18 6L6 18';
const REVIEW_SIZE = 20;

function itemLabel(kind, item) {
  return kind === 'kanji' ? item.character : item.word;
}

function entryHref(kind, item) {
  return kind === 'kanji' ? `/kanji/${encodeURIComponent(item.id)}` : `/vocab/${item.id}`;
}

function summary(kind, item, showWord = true) {
  if (kind === 'kanji') {
    return h('div', { class: 'quiz-summary' },
        h('p', { class: 'summary-meaning', text: item.meanings.join(', ') }),
        readingRows(item));
  }
  return h('div', { class: 'quiz-summary' },
      showWord ? h('p', { class: 'summary-word' }, wordTitle(item, 'span')) : null,
      h('p', { class: 'summary-meaning', text: item.meanings.join('; ') }),
      item.other_readings?.length ? h('p', { class: 'muted' }, 'Also read ', jp('span', item.other_readings.join('、'))) : null);
}

async function resolveScope(kind, scope) {
  const [data, plan] = await Promise.all([dataset(kind), lessons(kind)]);
  const level = LEVELS.find(each => each.toLowerCase() === scope);
  if (level) {
    return {
      data, items: data.items.filter(item => item.level === level), level, sampled: true, setId: null,
      title: `${level} ${kindName(kind).toLowerCase()}`, backHref: `/${kind}?level=${level}`, backLabel: `${level} sets`
    };
  }
  const lesson = plan.byId.get(scope);
  if (!lesson) {
    return null;
  }
  return {
    data, items: lesson.itemIds.map(id => data.byId.get(id)), level: lesson.level, sampled: false, setId: lesson.id,
    title: `${lesson.level} ${lesson.title}`, backHref: `/${kind}/lesson/${lesson.id}`, backLabel: `${lesson.level} ${lesson.title}`
  };
}

async function buildPool(kind, mode, scope) {
  const questions = await readingQuestions(kind, scope.items);
  if (mode === 'reading') {
    return scope.items.filter(item => questions.has(item.id))
        .map(item => ({ item, track: 'reading', question: questions.get(item.id) }));
  }
  return scope.items.map(item => ({ item, track: 'meaning' }));
}

export async function reviewView({ kind, query, signal }) {
  const data = await dataset(kind);
  const level = LEVELS.includes(query.get('level')) ? query.get('level') : null;
  const [progress, questions] = await Promise.all([loadProgress(kind), readingQuestions(kind, data.items)]);
  const now = Date.now();
  const due = dueEntries(progress, data, new Set(questions.keys()), now, level);
  const scope = {
    data, items: [], level, sampled: false, setId: null, review: true,
    title: `${level ?? 'All levels'} ${kindName(kind).toLowerCase()}`,
    backHref: `/${kind}${level ? `?level=${level}` : ''}`, backLabel: `${kindName(kind)} study sets`
  };
  const pool = due.slice(0, REVIEW_SIZE).map(record => ({
    item: data.byId.get(record.id), track: record.track,
    question: record.track === 'reading' ? questions.get(record.id) : undefined
  }));
  if (pool.length === 0) {
    const soonest = nextDue(progress, data, now);
    return {
      title: 'Nothing to review',
      node: h('div', { class: 'empty-state review-empty' },
          h('h1', { tabindex: '-1', text: 'Nothing to review right now' }),
          h('p', { text: soonest === null
              ? 'Take a reading quiz or meaning check in any study set. What you answer is scheduled here for review.'
              : `Your next review is due ${describeWhen(soonest, now)}.` }),
          h('a', { class: 'btn', href: scope.backHref, text: 'Back to study sets' }))
    };
  }
  return runQuiz({ kind, mode: 'review', scope, pool, total: due.length, signal });
}

export async function quizView({ kind, params, signal }) {
  const mode = params.mode;
  const scope = mode === 'reading' || mode === 'meaning' ? await resolveScope(kind, params.scope) : null;
  if (!scope) {
    return notFoundView({ kind });
  }
  const pool = await buildPool(kind, mode, scope);
  return runQuiz({ kind, mode, scope, pool, signal, scopeId: params.scope });
}

function runQuiz({ kind, mode, scope, pool, total, signal, scopeId }) {
  const quizName = MODE_NAMES[mode];
  const content = h('div', {});
  const live = h('p', { class: 'visually-hidden', 'aria-live': 'polite' });
  const notice = h('p', { class: 'notice error save-notice', role: 'alert', hidden: true });
  const root = h('div', { class: 'quiz' }, notice, content, live);
  let round;
  let saving = [];

  progressStore().then(store => {
    if (!store.persistent) {
      notice.textContent = 'Progress can’t be saved in this browser (storage is blocked or unavailable). You can still practise, but answers are kept only until you close this tab.';
      notice.hidden = false;
    }
  });

  function save(promise) {
    const tracked = promise.catch(() => {
      notice.textContent = 'Your answers could not be saved in this browser. You can keep studying, but progress will be lost on reload.';
      notice.hidden = false;
    });
    saving.push(tracked);
  }

  function grade(entry, correct) {
    save(progressStore().then(store => store.grade(
        { kind, track: entry.track, id: entry.item.id, level: entry.item.level }, correct ? 'good' : 'again', Date.now())));
  }

  function begin(entries) {
    round = {
      list: entries.map(entry => entry.question ? { ...entry, question: prepare(entry.question) } : { ...entry }),
      index: 0, results: [], phase: 'ask', choice: null, typed: '', startedAt: Date.now()
    };
    live.textContent = `Question 1 of ${round.list.length}`;
    show('#quiz-prompt');
  }

  function fresh() {
    begin(scope.sampled ? sample(pool, QUIZ_SIZE) : shuffle(pool));
  }

  function current() {
    return round.list[round.index];
  }

  function header() {
    const done = round.results.length;
    const fill = h('span', {});
    fill.style.width = `${(done / round.list.length) * 100}%`;
    const correct = round.results.filter(result => result.correct).length;
    return h('div', { class: 'quiz-head' },
        h('h1', { class: 'visually-hidden', text: `${quizName}: ${scope.title}` }),
        h('div', { class: 'lesson-top' },
            crumb(scope.backHref, scope.backLabel),
            h('p', { class: 'lesson-counter', text: `${round.index + 1} / ${round.list.length}` })),
        h('div', {
          class: 'progress', role: 'progressbar', 'aria-label': `${quizName} progress`,
          'aria-valuemin': '0', 'aria-valuemax': String(round.list.length), 'aria-valuenow': String(done)
        }, fill),
        h('p', { class: 'quiz-tally muted', text: done ? `${correct} of ${done} right so far` : '' }));
  }

  function prompt(entry) {
    const { item, track } = entry;
    const text = track === 'reading' ? (kind === 'kanji' ? entry.question.prompt.character : entry.question.prompt.word) : itemLabel(kind, item);
    const instruction = track === 'reading' ? entry.question.instruction
        : kind === 'kanji' ? 'What does this kanji mean?' : 'What does this word mean?';
    return h('div', { class: 'quiz-prompt' },
        h('p', { class: 'eyebrow', text: mode === 'review' ? `${track === 'reading' ? 'Reading' : 'Meaning'} review · ${instruction}` : instruction }),
        jp('h2', text, { class: kind === 'kanji' ? 'glyph-title' : 'word-title', tabindex: '-1', id: 'quiz-prompt', 'data-autofocus': track === 'reading' ? '' : null }),
        track === 'meaning' && kind === 'vocab' && item.word !== item.reading ? jp('p', item.reading, { class: 'prompt-reading' }) : null);
  }

  function answer(choice) {
    const entry = current();
    if (round.phase !== 'ask' || entry.track !== 'reading') {
      return;
    }
    const correct = isCorrect(entry.question, choice);
    round.phase = 'answered';
    round.choice = choice;
    round.results.push({ entry, correct, chosen: entry.question.options[choice] });
    grade(entry, correct);
    live.replaceChildren(...(correct ? ['Correct.'] : ['Incorrect. The answer is ', jp('span', entry.question.answer), '.']));
    show('[data-role="next"]');
  }

  function reveal(typed) {
    if (round.phase !== 'ask' || current().track !== 'meaning') {
      return;
    }
    round.typed = typed;
    if (typed === null) {
      round.phase = 'gave-up';
      round.results.push({ entry: current(), correct: false, typed: null });
      grade(current(), false);
      live.textContent = `Meaning: ${current().item.meanings.join(', ')}`;
      show('[data-role="next"]');
      return;
    }
    round.phase = 'revealed';
    live.textContent = `Meaning: ${current().item.meanings.join(', ')}. Did your answer match?`;
    show('[data-role="known"]');
  }

  function judge(known) {
    if (round.phase !== 'revealed') {
      return;
    }
    round.results.push({ entry: current(), correct: known, typed: round.typed });
    grade(current(), known);
    next();
  }

  async function finish() {
    round.phase = 'results';
    live.textContent = '';
    show('h1');
    const results = round.results;
    const startedAt = round.startedAt;
    const finishedAt = Date.now();
    const outcome = { correct: results.filter(result => result.correct).length, total: results.length, at: finishedAt };
    save(progressStore().then(async store => {
      await Promise.all(saving.slice(0, -1));
      await store.addSession({
        id: `${kind}-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
        kind, scope: scopeId ?? (scope.level ?? 'all').toLowerCase(), mode, startedAt: Math.min(startedAt, finishedAt), finishedAt,
        total: outcome.total, correct: outcome.correct,
        missed: results.filter(result => !result.correct).map(result => ({ id: result.entry.item.id, track: result.entry.track }))
      });
      if (scope.setId && (mode === 'reading' || mode === 'meaning')) {
        await store.updateSet(kind, scope.setId, scope.level, set => {
          const track = set[mode];
          const better = !track.best || outcome.correct / outcome.total > track.best.correct / track.best.total
              || (outcome.correct / outcome.total === track.best.correct / track.best.total);
          return { ...set, [mode]: { attempts: track.attempts + 1, last: outcome, best: better ? outcome : track.best } };
        }, finishedAt);
      }
    }));
  }

  function next() {
    if (round.index + 1 >= round.list.length) {
      finish();
      return;
    }
    round.index++;
    round.phase = 'ask';
    round.choice = null;
    round.typed = '';
    live.textContent = `Question ${round.index + 1} of ${round.list.length}`;
    show('#quiz-prompt');
  }

  function readingCard() {
    const entry = current();
    const { question } = entry;
    const answered = round.phase === 'answered';
    const chosenCorrect = answered && isCorrect(question, round.choice);
    const options = h('div', { class: 'options', role: 'group', 'aria-label': 'Answer choices' },
        question.options.map((option, index) => {
          const isAnswer = answered && index === question.answer_index;
          const isWrongChoice = answered && index === round.choice && !chosenCorrect;
          return h('button', {
            type: 'button',
            class: `option${isAnswer ? ' is-correct' : ''}${isWrongChoice ? ' is-wrong' : ''}`,
            'aria-disabled': answered ? 'true' : null,
            dataset: { index: String(index) },
            on: { click: () => answer(index) }
          },
          h('span', { class: 'option-key', 'aria-hidden': 'true', text: String(index + 1) }),
          jp('span', option, { class: 'option-text' }),
          isAnswer ? h('span', { class: 'option-mark' }, icon(ICONS.check), h('span', { class: 'visually-hidden', text: ' (correct answer)' })) : null,
          isWrongChoice ? h('span', { class: 'option-mark' }, icon(ICON_CROSS), h('span', { class: 'visually-hidden', text: ' (your answer)' })) : null);
        }));
    return h('div', { class: 'quiz-card' },
        h('div', { class: 'panel quiz-panel' }, prompt(entry), options,
            answered ? h('div', { class: `feedback ${chosenCorrect ? 'is-correct' : 'is-wrong'}` },
                h('p', { class: 'feedback-title' },
                    icon(chosenCorrect ? ICONS.check : ICON_CROSS),
                    chosenCorrect ? 'Correct' : 'Not quite. The answer is ',
                    chosenCorrect ? null : jp('strong', question.answer)),
                summary(kind, entry.item)) : null),
        h('div', { class: 'quiz-actions' },
            answered ? nextButton() : h('p', { class: 'muted key-hint', text: 'Press 1–4 to answer' })));
  }

  function nextButton() {
    return h('button', { type: 'button', class: 'btn', dataset: { role: 'next' }, on: { click: next } },
        round.index + 1 >= round.list.length ? 'See results' : 'Next', icon(ICONS.next));
  }

  function recallForm() {
    const input = h('input', {
      type: 'text', id: 'recall', class: 'recall-input', autocomplete: 'off', spellcheck: 'false',
      enterkeyhint: 'done', 'aria-describedby': 'recall-help', 'data-autofocus': ''
    });
    const error = h('p', { class: 'recall-error', id: 'recall-error', role: 'alert', hidden: true, text: 'Type what you think it means first, or choose “I don’t know”.' });
    const form = h('form', { class: 'recall', novalidate: true, on: { submit: event => {
      event.preventDefault();
      const typed = input.value.trim();
      if (!typed) {
        error.hidden = false;
        input.setAttribute('aria-invalid', 'true');
        input.focus();
        return;
      }
      reveal(typed);
    } } },
    h('label', { for: 'recall', class: 'recall-label', text: 'Your answer' }),
    h('p', { id: 'recall-help', class: 'muted', text: 'Recall the meaning in your own words before you check. You grade yourself honestly afterwards.' }),
    input,
    error,
    h('div', { class: 'recall-actions' },
        h('button', { type: 'button', class: 'btn btn-quiet', dataset: { role: 'unknown-now' }, on: { click: () => reveal(null) } }, 'I don’t know'),
        h('button', { type: 'submit', class: 'btn', dataset: { role: 'check' } }, 'Check meaning')));
    return form;
  }

  function meaningCard() {
    const entry = current();
    const { phase } = round;
    const shown = phase === 'revealed' || phase === 'gave-up';
    return h('div', { class: 'quiz-card' },
        h('div', { class: 'panel quiz-panel' }, prompt(entry),
            shown ? h('div', { class: `feedback ${phase === 'gave-up' ? 'is-wrong' : 'is-neutral'}` },
                phase === 'revealed' ? h('p', { class: 'your-recall' }, 'You wrote ', h('q', { text: round.typed })) : h('p', { class: 'feedback-title', text: 'Here is the meaning' }),
                summary(kind, entry.item, false)) : recallForm()),
        h('div', { class: 'quiz-actions' },
            phase === 'revealed'
                ? h('div', { class: 'judge', role: 'group', 'aria-label': 'Did your answer match?' },
                    h('button', { type: 'button', class: 'btn btn-secondary', dataset: { role: 'unknown' }, on: { click: () => judge(false) } },
                        h('span', { class: 'option-key', 'aria-hidden': 'true', text: '2' }), 'Not quite'),
                    h('button', { type: 'button', class: 'btn', dataset: { role: 'known' }, on: { click: () => judge(true) } },
                        h('span', { class: 'option-key', 'aria-hidden': 'true', text: '1' }), 'I had it'))
                : phase === 'gave-up' ? nextButton() : null,
            phase === 'revealed' ? h('p', { class: 'muted key-hint', text: 'Press 1 if your answer matched, 2 if not' }) : null));
  }

  function resultRow(result) {
    const { item, question, track } = result.entry;
    let detail;
    if (track === 'reading') {
      detail = h('p', {},
          result.correct ? null : h('span', { class: 'your-answer' }, 'Your answer ', jp('del', result.chosen)),
          h('span', { class: 'right-answer' }, 'Answer ', jp('strong', question.answer)));
    } else {
      detail = h('p', {},
          result.typed ? h('span', { class: result.correct ? 'right-answer' : 'your-answer' }, 'You wrote ', h('q', { text: result.typed })) : null,
          kind === 'vocab' && item.word !== item.reading ? jp('span', item.reading, { class: 'right-answer' }) : null);
    }
    return h('li', { class: `result-row${result.correct ? '' : ' is-wrong'}` },
        h('a', { class: 'result-item', href: entryHref(kind, item), lang: 'ja', text: itemLabel(kind, item) }),
        h('div', { class: 'result-detail' },
            mode === 'review' ? h('p', { class: 'eyebrow', text: track === 'reading' ? 'Reading' : 'Meaning' }) : null,
            detail,
            h('p', { class: 'muted', text: item.meanings.slice(0, 4).join('; ') })),
        h('span', { class: 'result-mark', title: result.correct ? 'Correct' : 'Missed' },
            icon(result.correct ? ICONS.check : ICON_CROSS),
            h('span', { class: 'visually-hidden', text: result.correct ? 'Correct' : 'Missed' })));
  }

  function results() {
    const totals = score(round.results);
    const missed = round.results.filter(result => !result.correct);
    const remaining = mode === 'review' ? Math.max(0, total - round.list.length) : 0;
    return h('div', { class: 'quiz-results' },
        crumb(scope.backHref, scope.backLabel),
        h('div', { class: 'page-head' },
            h('p', { class: 'eyebrow', text: `${scope.title} · ${quizName}` }),
            h('h1', { tabindex: '-1', text: 'Results' })),
        h('div', { class: 'panel score-panel' },
            h('p', { class: 'score' }, h('strong', { text: String(totals.correct) }), ` / ${totals.total}`),
            h('p', { class: 'score-note', text: `${totals.percent}% right${missed.length ? ` · ${missed.length} to review` : ' · nothing to review'}` }),
            h('p', { class: 'muted', text: missed.length
                ? 'Missed items come back for review in about ten minutes; the rest are scheduled further out.'
                : 'Everything is scheduled for its next review.' }),
            h('div', { class: 'lesson-actions' },
                missed.length ? h('button', { type: 'button', class: 'btn', on: { click: () => begin(shuffle(missed.map(result => result.entry))) } },
                    icon(ICONS.replay), `Retry ${missed.length} missed`) : null,
                mode === 'review'
                    ? (remaining ? h('button', { type: 'button', class: `btn ${missed.length ? 'btn-secondary' : ''}`, on: { click: async () => {
                      await Promise.all(saving);
                      window.dispatchEvent(new PopStateEvent('popstate', { state: history.state }));
                    } } }, `Continue (${remaining} more due)`) : null)
                    : h('button', { type: 'button', class: `btn ${missed.length ? 'btn-secondary' : ''}`, on: { click: fresh } },
                        scope.sampled ? 'New quiz' : 'Retry all'),
                h('a', { class: 'btn btn-quiet', href: scope.backHref, text: 'Done' }))),
        missed.length ? h('section', { class: 'detail-section', 'aria-labelledby': 'missed-title' },
            h('div', { class: 'section-head' }, h('h2', { id: 'missed-title', text: 'Mistakes to review' })),
            h('ul', { class: 'result-list' }, missed.map(resultRow))) : null,
        h('details', { class: 'detail-section all-results' },
            h('summary', { text: `All ${round.results.length} answers` }),
            h('ul', { class: 'result-list' }, round.results.map(resultRow))));
  }

  function emptyState() {
    return h('div', { class: 'empty-state' },
        h('h1', { tabindex: '-1', text: 'No reading questions here' }),
        h('p', { text: 'Every item in this set is written in kana only or has more than one accepted reading, so it has no approved reading question. Try the meaning check instead.' }),
        h('a', { class: 'btn', href: `/${kind}/quiz/${scopeId}/meaning`, text: 'Meaning check' }));
  }

  function show(focus) {
    if (pool.length === 0) {
      content.replaceChildren(emptyState());
      return;
    }
    content.replaceChildren(round.phase === 'results'
        ? results()
        : h('div', {}, header(), current().track === 'reading' ? readingCard() : meaningCard()));
    const target = focus === '#quiz-prompt' && current()?.track === 'meaning' && round.phase === 'ask' ? '#recall' : focus;
    if (target) {
      content.querySelector(target)?.focus({ preventScroll: true });
      if (round.phase === 'results' || focus === '#quiz-prompt') {
        window.scrollTo({ top: 0 });
      }
    }
  }

  document.addEventListener('keydown', event => {
    if (!round || round.phase === 'results' || event.altKey || event.ctrlKey || event.metaKey
        || event.target.closest?.('input, textarea, select, a')) {
      return;
    }
    const onButton = event.target.closest?.('button');
    const track = current().track;
    if (track === 'reading' && round.phase === 'ask' && /^[1-9]$/.test(event.key)) {
      const index = Number(event.key) - 1;
      if (index < current().question.options.length) {
        event.preventDefault();
        answer(index);
      }
    } else if ((round.phase === 'answered' || round.phase === 'gave-up')
        && (event.key === 'ArrowRight' || (event.key === 'Enter' && !onButton))) {
      event.preventDefault();
      next();
    } else if (round.phase === 'revealed' && (event.key === '1' || event.key === '2')) {
      event.preventDefault();
      judge(event.key === '1');
    }
  }, { signal });

  if (pool.length) {
    fresh();
  } else {
    show();
  }
  return { title: `${quizName} · ${scope.title}`, node: root };
}
