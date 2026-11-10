import { toHiragana } from './data.js';

export const QUIZ_SIZE = 20;
export const VERIFICATION = 'single_answer_against_all_pinned_source_readings';
export const QUESTION_TYPE = { kanji: 'kanji_on_reading', vocab: 'vocab_reading' };

let questionRequest = null;

export function shuffle(list, random = Math.random) {
  const copy = [...list];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

function toKatakana(text) {
  return text.replace(/[ぁ-ゖ]/g, char => String.fromCharCode(char.charCodeAt(0) + 0x60));
}

function kanjiReading(reading) {
  return toKatakana(reading.replace(/-/g, '').split('.')[0]);
}

export function acceptedReadings(kind, item) {
  if (kind === 'kanji') {
    return { correct: new Set(item.onyomi.map(kanjiReading)), forbidden: new Set([...item.onyomi, ...item.kunyomi].map(kanjiReading)) };
  }
  const readings = new Set([item.reading, ...(item.other_readings ?? [])].map(toHiragana));
  return { correct: readings, forbidden: readings };
}

export function isServable(kind, question, item) {
  if (!question || !item || question.verified !== true || question.verification !== VERIFICATION
      || question.type !== QUESTION_TYPE[kind] || question.source_id !== item.id) {
    return false;
  }
  const options = question.options;
  if (!Array.isArray(options) || options.length < 2 || new Set(options).size !== options.length
      || options[question.answer_index] !== question.answer) {
    return false;
  }
  const prompt = kind === 'kanji' ? question.prompt?.character : question.prompt?.word;
  if (prompt !== (kind === 'kanji' ? item.character : item.word)) {
    return false;
  }
  const { correct, forbidden } = acceptedReadings(kind, item);
  const normal = kind === 'kanji' ? kanjiReading : toHiragana;
  return correct.has(normal(question.answer))
      && options.every(option => option === question.answer || !forbidden.has(normal(option)));
}

export async function readingQuestions(kind, items) {
  questionRequest ??= fetch('/learning/questions.json', { credentials: 'same-origin' })
      .then(response => {
        if (!response.ok) {
          throw new Error(`questions.json returned ${response.status}`);
        }
        return response.json();
      })
      .catch(error => {
        questionRequest = null;
        throw error;
      });
  const document = await questionRequest;
  const bySource = new Map(document.questions
      .filter(question => question.type === QUESTION_TYPE[kind])
      .map(question => [question.source_id, question]));
  return new Map(items
      .filter(item => isServable(kind, bySource.get(item.id), item))
      .map(item => [item.id, bySource.get(item.id)]));
}

export function prepare(question, random = Math.random) {
  const options = shuffle(question.options, random);
  return { ...question, options, answer_index: options.indexOf(question.answer) };
}

export function isCorrect(question, choice) {
  return Number.isInteger(choice) && choice === question.answer_index && question.options[choice] === question.answer;
}

export function sample(list, size, random = Math.random) {
  return list.length <= size ? shuffle(list, random) : shuffle(list, random).slice(0, size);
}

export function score(results) {
  const correct = results.filter(result => result.correct).length;
  return { correct, total: results.length, percent: results.length ? Math.round((correct / results.length) * 100) : 0 };
}
