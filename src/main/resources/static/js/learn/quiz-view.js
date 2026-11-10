import { h, icon, ICONS, jp } from './dom.js';
import { dataset, LEVELS, lessons } from './data.js';
import { kindName } from './format.js';
import { isCorrect, prepare, QUIZ_SIZE, readingQuestions, sample, score, shuffle } from './quiz.js';
import { crumb, notFoundView, readingRows, wordTitle } from './views.js';

const MODE_NAMES = { reading: 'Reading quiz', meaning: 'Meaning check' };
const ICON_CROSS = 'M6 6l12 12M18 6L6 18';

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
      items: data.items.filter(item => item.level === level), level, sampled: true,
      title: `${level} ${kindName(kind).toLowerCase()}`, backHref: `/${kind}?level=${level}`, backLabel: `${level} sets`
    };
  }
  const lesson = plan.byId.get(scope);
  if (!lesson) {
    return null;
  }
  return {
    items: lesson.itemIds.map(id => data.byId.get(id)), level: lesson.level, sampled: false,
    title: `${lesson.level} ${lesson.title}`, backHref: `/${kind}/lesson/${lesson.id}`, backLabel: `${lesson.level} ${lesson.title}`
  };
}

export async function quizView({ kind, params, signal }) {
  const mode = params.mode;
  const scope = MODE_NAMES[mode] ? await resolveScope(kind, params.scope) : null;
  if (!scope) {
    return notFoundView({ kind });
  }
  let pool;
  if (mode === 'reading') {
    const questions = await readingQuestions(kind, scope.items);
    pool = scope.items.filter(item => questions.has(item.id)).map(item => ({ item, question: questions.get(item.id) }));
  } else {
    pool = scope.items.map(item => ({ item }));
  }
  const quizName = MODE_NAMES[mode];
  const content = h('div', {});
  const live = h('p', { class: 'visually-hidden', 'aria-live': 'polite' });
  const root = h('div', { class: 'quiz' }, content, live);
  let round;

  function begin(entries) {
    round = {
      list: entries.map(entry => entry.question ? { ...entry, question: prepare(entry.question) } : { ...entry }),
      index: 0, results: [], phase: 'ask', choice: null
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
        h('p', { class: 'quiz-tally muted', text: done ? `${correct} of ${done} ${mode === 'reading' ? 'correct' : 'known'} so far` : '' }));
  }

  function prompt(entry) {
    const { item } = entry;
    const text = mode === 'reading' ? (kind === 'kanji' ? entry.question.prompt.character : entry.question.prompt.word) : itemLabel(kind, item);
    return h('div', { class: 'quiz-prompt' },
        h('p', { class: 'eyebrow', text: mode === 'reading' ? entry.question.instruction : (kind === 'kanji' ? 'What does this kanji mean?' : 'What does this word mean?') }),
        jp('h2', text, { class: kind === 'kanji' ? 'glyph-title' : 'word-title', tabindex: '-1', id: 'quiz-prompt', 'data-autofocus': '' }),
        mode === 'meaning' && kind === 'vocab' && item.word !== item.reading ? jp('p', item.reading, { class: 'prompt-reading' }) : null);
  }

  function answer(choice) {
    if (round.phase !== 'ask') {
      return;
    }
    const entry = current();
    const correct = isCorrect(entry.question, choice);
    round.phase = 'answered';
    round.choice = choice;
    round.results.push({ entry, correct, chosen: entry.question.options[choice] });
    live.replaceChildren(...(correct ? ['Correct.'] : ['Incorrect. The answer is ', jp('span', entry.question.answer), '.']));
    show('[data-role="next"]');
  }

  function judge(known) {
    if (round.phase !== 'revealed') {
      return;
    }
    round.results.push({ entry: current(), correct: known });
    next();
  }

  function reveal() {
    if (round.phase !== 'ask') {
      return;
    }
    round.phase = 'revealed';
    live.textContent = `${current().item.meanings.join(', ')}`;
    show('[data-role="known"]');
  }

  function next() {
    if (round.index + 1 >= round.list.length) {
      round.phase = 'results';
      live.textContent = '';
      show('h1');
      return;
    }
    round.index++;
    round.phase = 'ask';
    round.choice = null;
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
            answered
                ? h('button', { type: 'button', class: 'btn', dataset: { role: 'next' }, on: { click: next } },
                    round.index + 1 >= round.list.length ? 'See results' : 'Next', icon(ICONS.next))
                : h('p', { class: 'muted key-hint', text: 'Press 1–4 to answer' })));
  }

  function meaningCard() {
    const entry = current();
    const revealed = round.phase === 'revealed';
    return h('div', { class: 'quiz-card' },
        h('div', { class: 'panel quiz-panel' }, prompt(entry),
            revealed ? h('div', { class: 'feedback is-neutral' }, summary(kind, entry.item, false)) : null),
        h('div', { class: 'quiz-actions' },
            revealed
                ? h('div', { class: 'judge' },
                    h('button', { type: 'button', class: 'btn btn-secondary', dataset: { role: 'unknown' }, on: { click: () => judge(false) } },
                        h('span', { class: 'option-key', 'aria-hidden': 'true', text: '2' }), 'Not yet'),
                    h('button', { type: 'button', class: 'btn', dataset: { role: 'known' }, on: { click: () => judge(true) } },
                        h('span', { class: 'option-key', 'aria-hidden': 'true', text: '1' }), 'I knew it'))
                : h('button', { type: 'button', class: 'btn', dataset: { role: 'reveal' }, on: { click: reveal } }, 'Show meaning'),
            h('p', { class: 'muted key-hint', text: revealed ? 'Press 1 if you knew it, 2 if not' : 'Press Space to reveal' })));
  }

  function resultRow(result) {
    const { item, question } = result.entry;
    return h('li', { class: `result-row${result.correct ? '' : ' is-wrong'}` },
        h('a', { class: 'result-item', href: entryHref(kind, item), lang: 'ja', text: itemLabel(kind, item) }),
        h('div', { class: 'result-detail' },
            mode === 'reading'
                ? h('p', {},
                    result.correct ? null : h('span', { class: 'your-answer' }, 'Your answer ', jp('del', result.chosen)),
                    h('span', { class: 'right-answer' }, 'Answer ', jp('strong', question.answer)))
                : (kind === 'vocab' && item.word !== item.reading ? jp('p', item.reading, { class: 'right-answer' }) : readingLine(item)),
            h('p', { class: 'muted', text: item.meanings.slice(0, 4).join('; ') })),
        h('span', { class: 'result-mark', title: result.correct ? 'Correct' : 'Missed' },
            icon(result.correct ? ICONS.check : ICON_CROSS),
            h('span', { class: 'visually-hidden', text: result.correct ? 'Correct' : 'Missed' })));
  }

  function readingLine(item) {
    return kind === 'kanji' ? jp('p', [...item.onyomi_display, ...item.kunyomi_display].join('、'), { class: 'right-answer' }) : null;
  }

  function results() {
    const total = score(round.results);
    const missed = round.results.filter(result => !result.correct);
    const verb = mode === 'reading' ? 'correct' : 'known';
    return h('div', { class: 'quiz-results' },
        crumb(scope.backHref, scope.backLabel),
        h('div', { class: 'page-head' },
            h('p', { class: 'eyebrow', text: `${scope.title} · ${quizName}` }),
            h('h1', { tabindex: '-1', text: 'Results' })),
        h('div', { class: 'panel score-panel' },
            h('p', { class: 'score' }, h('strong', { text: String(total.correct) }), ` / ${total.total}`),
            h('p', { class: 'score-note', text: `${total.percent}% ${verb}${missed.length ? ` · ${missed.length} to review` : ' · nothing to review'}` }),
            h('div', { class: 'lesson-actions' },
                missed.length ? h('button', { type: 'button', class: 'btn', on: { click: () => begin(shuffle(missed.map(result => result.entry))) } },
                    icon(ICONS.replay), `Retry ${missed.length} missed`) : null,
                h('button', { type: 'button', class: `btn ${missed.length ? 'btn-secondary' : ''}`, on: { click: fresh } },
                    scope.sampled ? 'New quiz' : 'Retry all'),
                h('a', { class: 'btn btn-quiet', href: scope.backHref, text: 'Done' }))),
        missed.length ? h('section', { class: 'detail-section', 'aria-labelledby': 'missed-title' },
            h('div', { class: 'section-head' }, h('h2', { id: 'missed-title', text: mode === 'reading' ? 'Mistakes to review' : 'Still to learn' })),
            h('ul', { class: 'result-list' }, missed.map(resultRow))) : null,
        h('details', { class: 'detail-section all-results' },
            h('summary', { text: `All ${round.results.length} ${mode === 'reading' ? 'questions' : 'items'}` }),
            h('ul', { class: 'result-list' }, round.results.map(resultRow))));
  }

  function emptyState() {
    return h('div', { class: 'empty-state' },
        h('h1', { tabindex: '-1', text: 'No reading questions here' }),
        h('p', { text: 'Every item in this set is written in kana only or has more than one accepted reading, so it has no approved reading question. Try the meaning check instead.' }),
        h('a', { class: 'btn', href: `/${kind}/quiz/${params.scope}/meaning`, text: 'Meaning check' }));
  }

  function show(focus) {
    if (pool.length === 0) {
      content.replaceChildren(emptyState());
      return;
    }
    content.replaceChildren(round.phase === 'results'
        ? results()
        : h('div', {}, header(), mode === 'reading' ? readingCard() : meaningCard()));
    if (focus) {
      content.querySelector(focus)?.focus({ preventScroll: true });
      if (round.phase === 'results' || focus === '#quiz-prompt') {
        window.scrollTo({ top: 0 });
      }
    }
  }

  document.addEventListener('keydown', event => {
    if (!round || event.altKey || event.ctrlKey || event.metaKey || event.target.closest?.('input, textarea, select, a')) {
      return;
    }
    const onButton = event.target.closest?.('button');
    if (mode === 'reading' && round.phase === 'ask' && /^[1-9]$/.test(event.key)) {
      const index = Number(event.key) - 1;
      if (index < current().question.options.length) {
        event.preventDefault();
        answer(index);
      }
    } else if (mode === 'reading' && round.phase === 'answered' && (event.key === 'ArrowRight' || (event.key === 'Enter' && !onButton))) {
      event.preventDefault();
      next();
    } else if (mode === 'meaning' && round.phase === 'ask' && !onButton && (event.key === ' ' || event.key === 'Enter')) {
      event.preventDefault();
      reveal();
    } else if (mode === 'meaning' && round.phase === 'revealed' && (event.key === '1' || event.key === '2')) {
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
