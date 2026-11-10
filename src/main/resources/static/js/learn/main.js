import { markNav } from '../shell.js';
import { KINDS } from './data.js';
import { errorView, homeView, kanjiDetailView, lessonView, libraryView, notFoundView, vocabDetailView } from './views.js';

const view = document.getElementById('view');
const main = document.getElementById('main');
let controller = null;
let initial = true;
let focusHref = null;

history.scrollRestoration = 'manual';

function route(url) {
  let parts;
  try {
    parts = url.pathname.split('/').filter(Boolean).map(decodeURIComponent);
  } catch {
    return { render: notFoundView, kind: 'kanji' };
  }
  const [kind, section, id] = parts;
  if (!KINDS.includes(kind) || parts.length > 3) {
    return { render: notFoundView, kind: KINDS.includes(kind) ? kind : 'kanji' };
  }
  if (!section) {
    return { render: homeView, kind };
  }
  if (section === 'library' && parts.length === 2) {
    return { render: libraryView, kind };
  }
  if (section === 'lesson' && id) {
    return { render: lessonView, kind, params: { id } };
  }
  if (parts.length === 2) {
    return { render: kind === 'kanji' ? kanjiDetailView : vocabDetailView, kind, params: { id: section } };
  }
  return { render: notFoundView, kind };
}

async function render() {
  controller?.abort();
  controller = new AbortController();
  const { signal } = controller;
  const url = new URL(location.href);
  const match = route(url);
  markNav(match.kind);
  view.setAttribute('aria-busy', 'true');
  try {
    const result = await match.render({
      kind: match.kind, params: match.params ?? {}, query: url.searchParams, signal
    });
    if (signal.aborted) {
      return;
    }
    document.title = `${result.title} · Kaidoku 解読`;
    view.replaceChildren(result.node);
  } catch (error) {
    if (signal.aborted) {
      return;
    }
    console.error(error);
    document.title = 'Could not load · Kaidoku 解読';
    view.replaceChildren(errorView(render));
  }
  view.removeAttribute('aria-busy');
  window.scrollTo({ top: history.state?.scroll ?? 0 });
  if (!initial) {
    const keep = focusHref && view.querySelector(`a[href="${CSS.escape(focusHref)}"]`);
    (keep || view.querySelector('h1') || main).focus({ preventScroll: true });
  }
  initial = false;
  focusHref = null;
}

function navigate(href) {
  history.replaceState({ ...history.state, scroll: window.scrollY }, '');
  history.pushState({ scroll: 0 }, '', href);
  render();
}

document.addEventListener('click', event => {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return;
  }
  const link = event.target.closest('a[href]');
  if (!link || link.target || link.hasAttribute('download')) {
    return;
  }
  const url = new URL(link.href, location.href);
  if (url.origin !== location.origin || !KINDS.includes(url.pathname.split('/')[1])
      || (url.hash && url.pathname === location.pathname && url.search === location.search)) {
    return;
  }
  event.preventDefault();
  focusHref = link.getAttribute('href');
  if (url.pathname + url.search !== location.pathname + location.search) {
    navigate(url.pathname + url.search);
  }
});

window.addEventListener('popstate', render);
render();
