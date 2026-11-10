const MAX_LENGTH = 10000;
const SAMPLE = '日本に来たばかりの頃は、漢字が読めなくて困っていた。でも、毎日練習するようにしたら、少しずつ読めるようになった。'
    + '宿題は明日までに出さなければならないので、今書いてしまわなければならない。'
    + '考えれば考えるほど、感じ方は人によって違うわけではないと思う。';

const $ = id => document.getElementById(id);
const state = { text: '', matches: [], selected: null, examples: new Map(), analyzed: false };

function h(tag, props = {}, ...children) {
  const node = Object.assign(document.createElement(tag), props);
  node.append(...children.filter(child => child !== null && child !== undefined));
  return node;
}

function badge(level) {
  return h('span', { className: `badge ${level}`, textContent: level });
}

function enabledLevels() {
  return new Set([...$('levels').querySelectorAll('input:checked')].map(input => input.value));
}

function visibleMatches() {
  const levels = enabledLevels();
  const shown = state.matches.filter(match => levels.has(match.level));
  if (!$('outermost').checked) {
    return shown;
  }
  return shown.filter(match => !shown.some(other => other.start <= match.start && match.end <= other.end
      && other.end - other.start > match.end - match.start));
}

const byLengthDesc = (a, b) => (b.end - b.start) - (a.end - a.start) || a.start - b.start;

async function analyze() {
  const text = $('text').value;
  showError(null);
  $('analyze').disabled = true;
  try {
    const response = await fetch('/api/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text })
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      showError(body.detail || body.title || `Request failed (${response.status})`);
      return;
    }
    Object.assign(state, { text, matches: body.matches, selected: null, analyzed: true });
    render();
  } catch {
    showError('Could not reach the server.');
  } finally {
    $('analyze').disabled = false;
  }
}

function render() {
  const visible = visibleMatches();
  if (!visible.includes(state.selected)) {
    state.selected = null;
  }
  renderText(visible);
  renderFound(visible);
  renderCard(visible);
}

function renderText(visible) {
  const output = $('output');
  if (!state.analyzed) {
    return;
  }
  const cuts = new Set([0, state.text.length]);
  visible.forEach(match => cuts.add(match.start).add(match.end));
  const points = [...cuts].sort((a, b) => a - b);
  const pieces = [];
  for (let i = 0; i < points.length - 1; i++) {
    const from = points[i];
    const to = points[i + 1];
    const text = state.text.slice(from, to);
    const covering = visible.filter(match => match.start <= from && to <= match.end).sort(byLengthDesc);
    pieces.push(covering.length === 0 ? text : segment(text, from, to, covering));
  }
  output.replaceChildren(...pieces);
}

function segment(text, from, to, covering) {
  const outer = covering[0];
  const inner = covering[covering.length - 1];
  const mark = h('mark', {
    textContent: text,
    className: `lvl-${outer.level}`,
    tabIndex: 0,
    title: covering.map(match => `${match.pattern} (${match.level})`).join(' ⊃ ')
  });
  mark.classList.toggle('nested', covering.length > 1);
  mark.classList.toggle('first', from === outer.start);
  mark.classList.toggle('last', to === outer.end);
  mark.classList.toggle('selected', covering.includes(state.selected));
  if (covering.length > 1) {
    mark.style.setProperty('--inner', `var(--${inner.level.toLowerCase()})`);
  }
  const choose = () => select(inner);
  mark.addEventListener('click', choose);
  mark.addEventListener('keydown', event => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      choose();
    }
  });
  return mark;
}

function renderFound(visible) {
  const groups = new Map();
  visible.forEach(match => {
    const group = groups.get(match.ruleId) ?? { first: match, matches: [] };
    group.matches.push(match);
    groups.set(match.ruleId, group);
  });
  const levelOrder = ['N5', 'N4', 'N3', 'N2', 'N1'];
  const sorted = [...groups.values()].sort((a, b) =>
      levelOrder.indexOf(a.first.level) - levelOrder.indexOf(b.first.level) || a.first.start - b.first.start);

  $('found-title').textContent = state.analyzed
      ? `Patterns found (${visible.length} ${visible.length === 1 ? 'match' : 'matches'})`
      : 'Patterns found';
  if (state.analyzed && sorted.length === 0) {
    $('found').replaceChildren(h('li', { className: 'empty', textContent: 'No known patterns at the selected levels.' }));
    return;
  }
  $('found').replaceChildren(...sorted.map(group => {
    const button = h('button', { type: 'button', className: 'pattern' },
        badge(group.first.level),
        h('span', { className: 'name', lang: 'ja', textContent: group.first.pattern }),
        h('span', { className: 'times', textContent: `×${group.matches.length}` }));
    button.classList.toggle('active', group.matches.includes(state.selected));
    button.addEventListener('click', () => {
      const current = group.matches.indexOf(state.selected);
      select(group.matches[(current + 1) % group.matches.length], true);
    });
    return h('li', {}, button);
  }));
}

function renderCard(visible) {
  const card = $('card');
  const match = state.selected;
  if (!match) {
    card.hidden = true;
    card.replaceChildren();
    return;
  }
  const examples = (state.examples.get(match.ruleId) ?? []).slice(0, 3);
  const related = visible.filter(other => other !== match && other.start < match.end && match.start < other.end);
  const close = h('button', { type: 'button', className: 'close', textContent: '×', title: 'Close' });
  close.setAttribute('aria-label', 'Close');
  close.addEventListener('click', () => select(null));

  card.replaceChildren(...[
      close,
      h('h2', {}, h('span', { lang: 'ja', textContent: match.pattern }), ' ', badge(match.level)),
      h('p', { className: 'meaning', textContent: match.meaning }),
      h('p', { className: 'quote', lang: 'ja', textContent: `「${match.text}」` }),
      h('p', { textContent: match.explanation }),
      examples.length === 0 ? null : h('h3', { textContent: 'Examples' }),
      examples.length === 0 ? null : h('ul', { className: 'examples', lang: 'ja' },
          ...examples.map(example => h('li', { textContent: example }))),
      related.length === 0 ? null : h('h3', { textContent: 'Overlapping patterns' }),
      related.length === 0 ? null : h('div', { className: 'related' }, ...related.map(other => {
        const button = h('button', { type: 'button', className: 'secondary' }, badge(other.level), ' ',
            h('span', { lang: 'ja', textContent: other.pattern }));
        button.addEventListener('click', () => select(other));
        return button;
      }))
  ].filter(Boolean));
  card.hidden = false;
}

function select(match, reveal = false) {
  state.selected = match;
  render();
  if (reveal) {
    document.querySelector('#output mark.selected')?.scrollIntoView({ block: 'nearest' });
  }
}

function showError(message) {
  $('error').hidden = !message;
  $('error').textContent = message ?? '';
}

function updateCount() {
  const length = $('text').value.length;
  $('count').textContent = `${length.toLocaleString()} / ${MAX_LENGTH.toLocaleString()}`;
  $('count').classList.toggle('over', length > MAX_LENGTH);
}

$('analyze').addEventListener('click', analyze);
$('sample').addEventListener('click', () => {
  $('text').value = SAMPLE;
  updateCount();
  analyze();
});
$('text').addEventListener('input', updateCount);
$('text').addEventListener('keydown', event => {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    analyze();
  }
});
$('levels').addEventListener('change', render);
$('outermost').addEventListener('change', render);

fetch('/api/patterns')
    .then(response => response.ok ? response.json() : [])
    .then(patterns => {
      patterns.forEach(pattern => state.examples.set(pattern.id, pattern.examples));
      render();
    })
    .catch(() => {});

updateCount();
