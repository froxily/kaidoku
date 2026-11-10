import { isDue } from './learn/srs.js';
import { progressStore } from './learn/store.js';

const store = await progressStore();
const now = Date.now();
for (const kind of ['kanji', 'vocab']) {
  const due = (await store.records(kind)).filter(record => record.state !== 'new' && isDue(record, now)).length;
  const line = document.querySelector(`[data-due="${kind}"]`);
  if (line && due) {
    line.textContent = `${due} ${due === 1 ? 'review' : 'reviews'} due`;
    line.hidden = false;
  }
}
