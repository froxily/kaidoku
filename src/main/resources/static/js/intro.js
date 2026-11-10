const SEEN_KEY = 'kaidoku.introSeen';

function readSeen(storage) {
  try {
    return Number(storage.getItem(SEEN_KEY)) || 0;
  } catch {
    return 0;
  }
}

function markSeen(storage, version) {
  try {
    storage.setItem(SEEN_KEY, String(version));
  } catch {
  }
}

function buildMedia(config) {
  if (config.type === 'video') {
    const video = document.createElement('video');
    video.className = 'intro-media';
    video.muted = true;
    video.autoplay = true;
    video.playsInline = true;
    video.preload = 'auto';
    video.setAttribute('aria-hidden', 'true');
    if (config.poster) {
      video.poster = config.poster;
    }
    for (const source of config.sources ?? []) {
      const node = document.createElement('source');
      node.src = source.src;
      node.type = source.type;
      video.append(node);
    }
    return video;
  }
  if (config.type === 'image') {
    const image = document.createElement('img');
    image.className = 'intro-media';
    image.src = config.sources?.[0]?.src ?? '';
    image.alt = config.alt ?? '';
    return image;
  }
  return null;
}

export function shouldPlay(config, { storage = globalThis.localStorage, reducedMotion = false } = {}) {
  return Boolean(config?.enabled) && !reducedMotion && Number.isInteger(config.version)
      && readSeen(storage) < config.version && buildable(config);
}

function buildable(config) {
  return (config.type === 'video' || config.type === 'image') && Array.isArray(config.sources) && config.sources.length > 0;
}

export function playIntro(config, options = {}) {
  const storage = options.storage ?? globalThis.localStorage;
  const reducedMotion = options.reducedMotion ?? matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (!shouldPlay(config, { storage, reducedMotion })) {
    return Promise.resolve('not-shown');
  }
  return new Promise(resolve => {
    const media = buildMedia(config);
    const skip = document.createElement('button');
    skip.type = 'button';
    skip.className = 'btn btn-secondary intro-skip';
    skip.textContent = 'Skip intro';
    const overlay = document.createElement('div');
    overlay.className = 'intro';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-label', 'Introduction');
    overlay.append(media, skip);
    const previousFocus = document.activeElement;
    const siblings = [...document.body.children];
    let finished = false;
    let timer = 0;

    const finish = outcome => {
      if (finished) {
        return;
      }
      finished = true;
      clearTimeout(timer);
      document.removeEventListener('keydown', onKey, true);
      markSeen(storage, config.version);
      siblings.forEach(node => { node.inert = false; });
      document.documentElement.classList.remove('intro-active');
      overlay.classList.add('is-leaving');
      const remove = () => {
        overlay.remove();
        previousFocus?.focus?.({ preventScroll: true });
        resolve(outcome);
      };
      const duration = Number.parseFloat(getComputedStyle(overlay).transitionDuration) * 1000;
      if (duration > 0) {
        overlay.addEventListener('transitionend', remove, { once: true });
        setTimeout(remove, duration + 100);
      } else {
        remove();
      }
    };
    const onKey = event => {
      if (event.key === 'Escape') {
        event.preventDefault();
        finish('skipped');
      }
    };

    skip.addEventListener('click', () => finish('skipped'));
    media.addEventListener('ended', () => finish('played'));
    media.addEventListener('error', () => finish('failed'), true);
    document.addEventListener('keydown', onKey, true);
    siblings.forEach(node => { node.inert = true; });
    document.documentElement.classList.add('intro-active');
    document.body.append(overlay);
    skip.focus({ preventScroll: true });
    timer = setTimeout(() => finish('played'), Math.max(500, Number(config.maxDurationMs) || 6000));
    if (media instanceof HTMLVideoElement) {
      media.play?.().catch(() => finish('failed'));
    }
  });
}
