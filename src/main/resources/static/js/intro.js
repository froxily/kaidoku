const TIMING = {
  exitStart: 3100,
  total: 4000,
  reducedExitStart: 1500,
  reducedTotal: 1900,
  skipFade: 320,
}

export const INTRO_COMPLETE_EVENT = 'kaidoku:intro-complete'
const GUARD_CLASS = 'ki-intro-pending'

const DEFAULTS = {

  container: null,

  appRoot: null,

  theme: 'auto',

  themeStorageKey: 'theme',

  once: true,
  storageKey: 'kaidoku:intro-played',

  motion: 'auto',

  reducedMotion: 'static',

  logoSrc: null,

  fontTimeout: 400,
  onStart: null,
  onComplete: null,
}

const SENTENCE = { before: '漢字が読める', pattern: 'ようになった', after: '。' }

function readStorage(store, key) {
  try {
    return window[store].getItem(key)
  } catch {
    return null
  }
}

function writeStorage(store, key, value) {
  try {
    window[store].setItem(key, value)
  } catch {

  }
}

function matches(query) {
  try {
    return window.matchMedia(query).matches
  } catch {
    return false
  }
}


export function resolveTheme(theme = 'auto', themeStorageKey = 'theme') {
  if (typeof theme === 'function') theme = theme()
  if (theme === 'light' || theme === 'dark') return theme

  const html = document.documentElement
  const attr = html.getAttribute('data-theme')
  if (attr === 'dark' || attr === 'light') return attr
  if (html.classList.contains('dark')) return 'dark'
  if (html.classList.contains('light')) return 'light'

  const stored = readStorage('localStorage', themeStorageKey)
  if (stored === 'dark' || stored === 'light') return stored

  return matches('(prefers-color-scheme: dark)') ? 'dark' : 'light'
}


export function shouldPlayKaidokuIntro(storageKey = DEFAULTS.storageKey) {
  return !readStorage('sessionStorage', storageKey)
}


export function resetKaidokuIntro(storageKey = DEFAULTS.storageKey) {
  try {
    window.sessionStorage.removeItem(storageKey)
  } catch {

  }
}

function escapeAttr(value) {
  return String(value).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')
}

function chars(text) {
  return Array.from(text, (c) => `<span class="ki-ch">${c}</span>`).join('')
}

function template(logoSrc) {
  const mark = logoSrc
    ? `<img class="ki-mark-img" src="${escapeAttr(logoSrc)}" alt="" draggable="false">`
    : `<span class="ki-mark-glyph" lang="ja">解</span>`

  return `
<div class="ki-watermark" aria-hidden="true" lang="ja">解読</div>
<div class="ki-stage">
  <div class="ki-scene ki-scene--open" aria-hidden="true"><span class="ki-rule"></span></div>

  <div class="ki-scene ki-specimens" aria-hidden="true">
    <section class="ki-spec ki-spec--grammar">
      <p class="ki-eyebrow ki-a"><span class="ki-num">01</span><span>Grammar</span></p>
      <div class="ki-spec-body">
        <p class="ki-sentence" lang="ja">${chars(SENTENCE.before)}<span class="ki-hl">${chars(SENTENCE.pattern)}</span>${chars(SENTENCE.after)}</p>
      </div>
    </section>
    <span class="ki-divider ki-divider--a"></span>
    <section class="ki-spec ki-spec--kanji">
      <p class="ki-eyebrow ki-a"><span class="ki-num">02</span><span>Kanji</span></p>
      <div class="ki-spec-body">
        <div class="ki-kanji" lang="ja">
          <span class="ki-kanji-glyph ki-a">日</span>
          <dl class="ki-readings">
            <div class="ki-reading ki-reading--on ki-a"><dt class="ki-chip">音</dt><dd>ニチ・ジツ</dd></div>
            <div class="ki-reading ki-reading--kun ki-a"><dt class="ki-chip">訓</dt><dd>ひ・か</dd></div>
          </dl>
        </div>
      </div>
    </section>
    <span class="ki-divider ki-divider--b"></span>
    <section class="ki-spec ki-spec--vocab">
      <p class="ki-eyebrow ki-a"><span class="ki-num">03</span><span>Vocabulary</span></p>
      <div class="ki-spec-body">
        <div class="ki-vocab">
          <p class="ki-word ki-a" lang="ja"><ruby>会<rt>あ</rt></ruby>う</p>
          <p class="ki-gloss ki-a">to meet</p>
        </div>
      </div>
    </section>
  </div>

  <div class="ki-scene ki-brand">
    <div class="ki-mark ki-a" aria-hidden="true">${mark}</div>
    <p class="ki-wordmark ki-a">Kaidoku</p>
    <div class="ki-meta">
      <span class="ki-meta-jp ki-a" lang="ja">解読</span>
      <span class="ki-meta-rule" aria-hidden="true"></span>
      <span class="ki-tagline ki-a">Japanese Reading Companion</span>
    </div>
  </div>
</div>
<button class="ki-skip" type="button" aria-label="Skip intro">
  <span>Skip</span>
  <svg viewBox="0 0 12 12" fill="none" aria-hidden="true"><path d="M2.5 6h7M6.5 3l3 3-3 3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
</button>`
}

function waitForFonts(timeout) {
  const fonts = document.fonts
  if (!fonts || !fonts.ready || timeout <= 0) return Promise.resolve()
  return new Promise((resolve) => {
    requestAnimationFrame(() => {
      const timer = setTimeout(resolve, timeout)
      fonts.ready.then(
        () => {
          clearTimeout(timer)
          resolve()
        },
        () => resolve(),
      )
    })
  })
}

function releaseGuard() {
  document.documentElement.classList.remove(GUARD_CLASS)
}


export function playKaidokuIntro(options = {}) {
  const o = { ...DEFAULTS, ...options }

  let resolveFinished
  const finished = new Promise((resolve) => (resolveFinished = resolve))

  let root = null
  let done = false
  let skipping = false
  let reduced = false
  const appInertBefore = new Map()
  const timers = []
  const cleanups = []

  const controller = {
    element: null,
    finished,
    skip,
    destroy: () => finish('destroyed', false),
  }

  function releaseApp() {
    appInertBefore.forEach((inert, appRoot) => { appRoot.inert = inert })
  }

  function finish(reason, notify = true) {
    if (done) return
    done = true
    timers.forEach(clearTimeout)
    cleanups.forEach((fn) => fn())
    if (root) root.remove()
    releaseGuard()
    releaseApp()
    if (notify) {
      try {
        o.onComplete?.(reason)
      } catch (error) {
        console.error('[kaidoku-intro] onComplete threw', error)
      }
      window.dispatchEvent(new CustomEvent(INTRO_COMPLETE_EVENT, { detail: { reason } }))
    }
    resolveFinished(reason)
  }

  function beginLeaving() {
    if (done || !root) return
    root.classList.add('ki-leaving')
    releaseApp()
  }

  function skip() {
    if (done || skipping) return
    if (!root) return finish('skipped')
    skipping = true
    root.style.opacity = getComputedStyle(root).opacity
    root.classList.remove('ki-paused')
    root.classList.add('ki-skipping', 'ki-leaving')
    releaseApp()
    timers.push(setTimeout(() => finish('skipped'), TIMING.skipFade + 200))
  }

  function on(target, type, handler, opts) {
    target.addEventListener(type, handler, opts)
    cleanups.push(() => target.removeEventListener(type, handler, opts))
  }

  try {
    if (typeof document === 'undefined') {
      resolveFinished('error')
      return controller
    }

    if (o.once && !shouldPlayKaidokuIntro(o.storageKey)) {
      finish('already-played')
      return controller
    }

    reduced = o.motion === 'reduced' || (o.motion === 'auto' && matches('(prefers-reduced-motion: reduce)'))

    if (o.once) writeStorage('sessionStorage', o.storageKey, String(Date.now()))

    if (reduced && o.reducedMotion === 'skip') {
      finish('reduced-motion')
      return controller
    }

    root = document.createElement('div')
    root.className = 'ki-intro ki-paused' + (reduced ? ' ki-reduced' : '')
    root.dataset.theme = resolveTheme(o.theme, o.themeStorageKey)
    root.setAttribute('role', 'region')
    root.setAttribute('aria-label', 'Kaidoku 解読 — Japanese Reading Companion')
    root.innerHTML = template(o.logoSrc)
    root.querySelectorAll('.ki-ch').forEach((el, i) => el.style.setProperty('--i', String(i)))
    controller.element = root

    const container = o.container || document.body
    container.prepend(root)
    releaseGuard()

    const appRoots = Array.isArray(o.appRoot) ? o.appRoot : [o.appRoot]
    appRoots.forEach((appRoot) => {
      if (appRoot && 'inert' in appRoot && !appInertBefore.has(appRoot)) {
        appInertBefore.set(appRoot, appRoot.inert)
        appRoot.inert = true
      }
    })

    on(root.querySelector('.ki-skip'), 'click', skip)
    on(window, 'keydown', (e) => {
      if (e.key === 'Escape') skip()
    })
    const block = (e) => {
      if (e.cancelable) e.preventDefault()
    }
    on(root, 'wheel', block, { passive: false })
    on(root, 'touchmove', block, { passive: false })
    on(root, 'animationend', (e) => {
      if (e.target !== root) return
      if (e.animationName === 'ki-exit' || e.animationName === 'ki-fade-out') {
        finish(skipping ? 'skipped' : reduced ? 'reduced-motion' : 'completed')
      }
    })

    waitForFonts(o.fontTimeout).then(() => {
      if (done || skipping) return
      root.classList.remove('ki-paused')
      try {
        o.onStart?.()
      } catch (error) {
        console.error('[kaidoku-intro] onStart threw', error)
      }
      const exitStart = reduced ? TIMING.reducedExitStart : TIMING.exitStart
      const total = reduced ? TIMING.reducedTotal : TIMING.total
      timers.push(setTimeout(beginLeaving, exitStart))
      timers.push(setTimeout(() => finish(reduced ? 'reduced-motion' : 'completed'), total + 600))
    })
  } catch (error) {
    console.error('[kaidoku-intro] failed; revealing app', error)
    finish('error')
  }

  return controller
}

export default playKaidokuIntro

export function playIntro(config) {
  if (!config?.enabled) {
    releaseGuard()
    return Promise.resolve('not-shown')
  }
  return playKaidokuIntro({
    appRoot: [...document.body.children],
    themeStorageKey: 'kaidoku.theme',
    storageKey: 'kaidoku:intro-played',
  }).finished
}
