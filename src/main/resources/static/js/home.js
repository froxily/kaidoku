import { INTRO } from './intro-config.js';
import { playIntro } from './intro.js';
import { dashboardDueCount } from './learn/progress.js';

playIntro(INTRO);

const now = Date.now();
for (const kind of ['kanji', 'vocab']) {
  const due = await dashboardDueCount(kind, now);
  const line = document.querySelector(`[data-due="${kind}"]`);
  if (line && due) {
    line.textContent = `${due} ${due === 1 ? 'review' : 'reviews'} due`;
    line.hidden = false;
  }
}
