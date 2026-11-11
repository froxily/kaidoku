if (location.pathname !== '/' && location.pathname !== '/index.html') {
  try {
    sessionStorage.setItem('kaidoku:intro-played', '1');
  } catch {
  }
}

const MODES = ['system', 'light', 'dark'];
const LABELS = { system: 'Theme: match system', light: 'Theme: light', dark: 'Theme: dark' };
const root = document.documentElement;

function currentMode() {
  return root.dataset.theme ?? 'system';
}

function showMode(mode) {
  document.querySelectorAll('[data-theme-toggle]').forEach(button => {
    button.dataset.mode = mode;
    button.setAttribute('aria-label', `${LABELS[mode]}. Change theme`);
    button.title = LABELS[mode];
  });
}

function applyMode(mode) {
  if (mode === 'system') {
    delete root.dataset.theme;
  } else {
    root.dataset.theme = mode;
  }
  try {
    if (mode === 'system') {
      localStorage.removeItem('kaidoku.theme');
    } else {
      localStorage.setItem('kaidoku.theme', mode);
    }
  } catch {
  }
  showMode(mode);
}

export function markNav(section) {
  document.querySelectorAll('[data-nav]').forEach(link => {
    if (link.dataset.nav === section) {
      link.setAttribute('aria-current', 'page');
    } else {
      link.removeAttribute('aria-current');
    }
  });
}

document.querySelectorAll('[data-theme-toggle]').forEach(button => button.addEventListener('click', () => {
  applyMode(MODES[(MODES.indexOf(currentMode()) + 1) % MODES.length]);
}));
showMode(currentMode());
markNav(document.body.dataset.section);

if ('serviceWorker' in navigator && (location.protocol === 'https:'
    || location.hostname === 'localhost' || location.hostname === '127.0.0.1')) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => {});
  }, { once: true });
}
