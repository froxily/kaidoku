package dev.akshat.grammarxray.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProgressE2eTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setChannel(System.getProperty("e2e.browser.channel", "msedge"))
                .setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        try {
            if (browser != null) {
                browser.close();
            }
        } finally {
            if (playwright != null) {
                playwright.close();
            }
        }
    }

    @BeforeEach
    void openContext() {
        context = browser.newContext();
        page = context.newPage();
        page.navigate("http://localhost:" + port + "/kanji");
        page.evaluate("""
                async () => {
                  await new Promise((resolve, reject) => {
                    const request = indexedDB.deleteDatabase('kaidoku');
                    request.onsuccess = () => resolve();
                    request.onerror = () => reject(request.error);
                    request.onblocked = () => reject(new Error('Progress database deletion blocked'));
                  });
                }
                """);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    @Test
    void schedulesByTheDocumentedRules() {
        Map<?, ?> result = (Map<?, ?>) page.evaluate("""
                async () => {
                  const { newRecord, schedule, isValidRecord, isDue, mastery } = await import('/js/learn/srs.js');
                  const now = 1800000000000;
                  const initial = Object.freeze(newRecord({ kind: 'kanji', track: 'reading', id: '日', level: 'N5' }, now));
                  const first = schedule(initial, 'good', now);
                  const second = schedule(first, 'good', now + 86400000);
                  const againNow = now + 172800000;
                  const again = schedule(second, 'again', againNow);
                  const easy = schedule(initial, 'easy', now);
                  const capped = schedule({ ...initial, intervalDays: 300, state: 'review', streak: 2 }, 'good', now);
                  return {
                    initial, first, second, again, easy, capped, delay: again.dueAt - againNow,
                    allValid: [initial, first, second, again, easy, capped].every(isValidRecord),
                    due: [isDue(initial, now - 1), isDue(initial, now)],
                    mastery: [mastery(null), mastery(initial), mastery(first), mastery(capped)]
                  };
                }
                """);

        Map<?, ?> initial = (Map<?, ?>) result.get("initial");
        assertThat(initial.get("key")).isEqualTo("kanji:reading:日");
        assertThat(initial.get("state")).isEqualTo("new");
        assertThat(initial.get("ease")).isEqualTo(2.5);
        assertThat(initial.get("lastReviewedAt")).isNull();
        assertThat(number(initial.get("intervalDays"))).isZero();
        assertThat(number(initial.get("reps"))).isZero();
        assertThat(number(initial.get("dueAt"))).isEqualTo(1800000000000L);

        Map<?, ?> first = (Map<?, ?>) result.get("first");
        assertThat(number(first.get("intervalDays"))).isEqualTo(1L);
        assertThat(first.get("state")).isEqualTo("learning");
        assertThat(number(first.get("streak"))).isEqualTo(1L);
        assertThat(first.get("ease")).isEqualTo(2.5);

        Map<?, ?> second = (Map<?, ?>) result.get("second");
        assertThat(number(second.get("intervalDays"))).isEqualTo(3L);
        assertThat(second.get("state")).isEqualTo("review");
        assertThat(number(second.get("streak"))).isEqualTo(2L);
        assertThat(number(second.get("correct"))).isEqualTo(2L);

        Map<?, ?> again = (Map<?, ?>) result.get("again");
        assertThat(number(again.get("intervalDays"))).isZero();
        assertThat(again.get("ease")).isEqualTo(2.3);
        assertThat(number(again.get("lapses"))).isEqualTo(1L);
        assertThat(number(again.get("streak"))).isZero();
        assertThat(number(again.get("incorrect"))).isEqualTo(1L);
        assertThat(number(again.get("reps"))).isEqualTo(3L);
        assertThat(number(again.get("lastReviewedAt"))).isEqualTo(1800172800000L);
        assertThat(number(again.get("updatedAt"))).isEqualTo(1800172800000L);
        assertThat(number(again.get("createdAt"))).isEqualTo(1800000000000L);
        assertThat(number(result.get("delay"))).isEqualTo(600000L);
        assertThat(again.get("state")).isEqualTo("learning");

        Map<?, ?> easy = (Map<?, ?>) result.get("easy");
        assertThat(easy.get("ease")).isEqualTo(2.65);
        assertThat(number(easy.get("intervalDays"))).isEqualTo(3L);
        assertThat(easy.get("state")).isEqualTo("review");
        assertThat(number(((Map<?, ?>) result.get("capped")).get("intervalDays"))).isEqualTo(365L);
        assertThat(result.get("allValid")).isEqualTo(true);
        assertThat(result.get("due")).isEqualTo(List.of(false, true));
        assertThat(result.get("mastery")).isEqualTo(List.of("new", "new", "learning", "mastered"));
    }

    @Test
    void dueTimesLandOnTheLocalDayStart() {
        Map<?, ?> result = (Map<?, ?>) page.evaluate("""
                async () => {
                  const { newRecord, dayStartAfter, schedule } = await import('/js/learn/srs.js');
                  const now = new Date(2026, 2, 7, 23, 30).getTime();
                  const record = newRecord({ kind: 'vocab', track: 'meaning', id: 'ada066edfd', level: 'N5' }, now);
                  record.lastReviewedAt = now + 86400000;
                  return {
                    tomorrow: dayStartAfter(now, 1), expectedTomorrow: new Date(2026, 2, 8, 4, 0).getTime(),
                    thirty: dayStartAfter(now, 30), expectedThirty: new Date(2026, 3, 6, 4, 0).getTime(),
                    zero: dayStartAfter(now, 0), now,
                    backwards: schedule(record, 'again', now).dueAt, expectedBackwards: now + 600000
                  };
                }
                """);

        assertThat(number(result.get("tomorrow"))).isEqualTo(number(result.get("expectedTomorrow")));
        assertThat(number(result.get("thirty"))).isEqualTo(number(result.get("expectedThirty")));
        assertThat(number(result.get("zero"))).isEqualTo(number(result.get("now")));
        assertThat(number(result.get("backwards"))).isEqualTo(number(result.get("expectedBackwards")));
    }

    @Test
    void validatesRecordsStrictly() {
        Object result = page.evaluate("""
                async () => {
                  const { newRecord, isValidRecord } = await import('/js/learn/srs.js');
                  const record = newRecord({ kind: 'vocab', track: 'reading', id: 'ada066edfd', level: 'N5' }, 1800000000000);
                  return [record, { ...record, key: 'wrong' }, { ...record, ease: 1.0 },
                    { ...record, intervalDays: 1.5 }, { ...record, level: 'N2' }, { ...record, reps: -1 },
                    null, [], 'record', { ...record, dueAt: 1e100 }, { ...record, lastReviewedAt: -5 }].map(isValidRecord);
                }
                """);

        assertThat(result).isEqualTo(List.of(true, false, false, false, false, false, false, false, false, false, false));
    }

    @Test
    void persistsAcrossReload() {
        Map<?, ?> before = (Map<?, ?>) page.evaluate("""
                async () => {
                  const { openStore } = await import('/js/learn/store.js');
                  const store = await openStore();
                  const record = await store.grade({ kind: 'vocab', track: 'reading', id: 'ada066edfd', level: 'N5' },
                    'good', 1800000000000);
                  return { persistent: store.persistent, reps: record.reps };
                }
                """);
        assertThat(before.get("persistent")).isEqualTo(true);
        assertThat(number(before.get("reps"))).isEqualTo(1L);

        page.reload();

        Map<?, ?> after = (Map<?, ?>) page.evaluate("""
                async () => {
                  const { openStore } = await import('/js/learn/store.js');
                  const store = await openStore();
                  return { persistent: store.persistent, record: await store.record('vocab', 'reading', 'ada066edfd') };
                }
                """);
        assertThat(after.get("persistent")).isEqualTo(true);
        Map<?, ?> record = (Map<?, ?>) after.get("record");
        assertThat(record).isNotNull();
        assertThat(record.get("key")).isEqualTo("vocab:reading:ada066edfd");
        assertThat(number(record.get("reps"))).isEqualTo(1L);
    }

    @Test
    void parallelGradesAreNotLost() {
        Map<?, ?> result = (Map<?, ?>) page.evaluate("""
                async () => {
                  const { openStore } = await import('/js/learn/store.js');
                  const store = await openStore();
                  const item = { kind: 'vocab', track: 'reading', id: 'ada066edfd', level: 'N5' };
                  await Promise.all(Array.from({ length: 5 }, (_, i) => store.grade(item, 'good', 1800000000000 + i)));
                  return { persistent: store.persistent, record: await store.record('vocab', 'reading', item.id) };
                }
                """);

        assertThat(result.get("persistent")).isEqualTo(true);
        Map<?, ?> record = (Map<?, ?>) result.get("record");
        assertThat(number(record.get("reps"))).isEqualTo(5L);
        assertThat(number(record.get("correct"))).isEqualTo(5L);
        assertThat(number(record.get("intervalDays"))).isEqualTo(50L);
    }

    @Test
    void resetInvalidatesOlderGenerations() {
        page.navigate("http://localhost:" + port + "/about");
        List<?> results = (List<?>) page.evaluate("""
                async () => {
                  const { openStore } = await import('/js/learn/store.js');
                  const outcomes = [];
                  for (const store of [await openStore(), await openStore({ idb: undefined })]) {
                    const now = 1800000000000;
                    const item = { kind: 'kanji', track: 'reading', id: '日', level: 'N5' };
                    const session = {
                      id: 'round', kind: 'kanji', scope: 'kanji-n5-1', mode: 'reading',
                      startedAt: now, finishedAt: now + 1, total: 1, correct: 1, missed: []
                    };
                    const update = set => ({ ...set, cardsCompletedAt: now });
                    const g0 = await store.generation();
                    const first = await store.grade(item, 'good', now, { generation: g0 });
                    await store.updateSet('kanji', 'kanji-n5-1', 'N5', update, now, { generation: g0 });
                    await store.addSession(session, { generation: g0 });
                    const backup = await store.exportData(now + 2);
                    await store.setMeta('hidden:kanji', { keys: ['old-kanji'] });
                    await store.setMeta('hidden:vocab', { keys: ['old-vocab'] });
                    await store.reset();
                    const g1 = await store.generation();
                    const cleared = [await store.records(), await store.sets(), await store.sessions()];
                    const resetHidden = [await store.getMeta('hidden:kanji'), await store.getMeta('hidden:vocab')];
                    await store.setMeta('hidden:kanji', { keys: ['keep-kanji'] });
                    await store.setMeta('hidden:vocab', { keys: ['keep-vocab'] });
                    let updateCalled = false;
                    const staleGrade = await store.grade(item, 'good', now, { generation: g0 });
                    const staleSet = await store.updateSet('kanji', 'kanji-n5-1', 'N5', set => {
                      updateCalled = true;
                      return update(set);
                    }, now, { generation: g0 });
                    const staleSession = await store.addSession(session, { generation: g0 });
                    const staleImport = await store.importData(backup, now + 3, { generation: g0 });
                    const staleHidden = [await store.getMeta('hidden:kanji'), await store.getMeta('hidden:vocab')];
                    const afterStale = [await store.records(), await store.sets(), await store.sessions()];
                    const freshGrade = await store.grade(item, 'good', now, { generation: g1 });
                    const freshSet = await store.updateSet('kanji', 'kanji-n5-1', 'N5', update, now, { generation: g1 });
                    const freshSession = await store.addSession(session, { generation: g1 });
                    const restored = [await store.records(), await store.sets(), await store.sessions()];
                    const freshImport = await store.importData(await store.exportData(now + 2), now + 3, { generation: g1 });
                    const importedHidden = [await store.getMeta('hidden:kanji'), await store.getMeta('hidden:vocab')];
                    const afterImport = await store.generation();
                    const invalidGenerations = [];
                    for (const value of [null, -1, 1.5, '2']) {
                      await store.setMeta('generation', value);
                      invalidGenerations.push(await store.generation());
                    }
                    outcomes.push({
                      persistent: store.persistent, g0, g1, first, cleared,
                      staleGrade, staleSet, staleSession, staleImport, resetHidden, staleHidden, freshImport, importedHidden,
                      updateCalled, afterStale,
                      freshGrade, freshSet, freshSessionSaved: freshSession !== null, restored, afterImport, invalidGenerations
                    });
                  }
                  return outcomes;
                }
                """);

        assertThat(results).hasSize(2);
        assertThat(((Map<?, ?>) results.get(0)).get("persistent")).isEqualTo(true);
        assertThat(((Map<?, ?>) results.get(1)).get("persistent")).isEqualTo(false);
        for (Object value : results) {
            Map<?, ?> result = (Map<?, ?>) value;
            assertThat(number(result.get("g0"))).isZero();
            assertThat(number(result.get("g1"))).isEqualTo(number(result.get("g0")) + 1);
            assertThat(number(((Map<?, ?>) result.get("first")).get("reps"))).isEqualTo(1L);
            assertThat(result.get("cleared")).isEqualTo(List.of(List.of(), List.of(), List.of()));
            assertThat(result.get("staleGrade")).isNull();
            assertThat(result.get("staleSet")).isNull();
            assertThat(result.get("staleSession")).isNull();
            assertThat(result.get("staleImport")).isNull();
            assertThat((List<?>) result.get("resetHidden")).containsOnlyNulls().hasSize(2);
            assertThat(result.get("staleHidden")).isEqualTo(List.of(
                    Map.of("keys", List.of("keep-kanji")), Map.of("keys", List.of("keep-vocab"))));
            assertThat(number(((Map<?, ?>) result.get("freshImport")).get("imported"))).isEqualTo(3L);
            assertThat((List<?>) result.get("importedHidden")).containsOnlyNulls().hasSize(2);
            assertThat(result.get("updateCalled")).isEqualTo(false);
            assertThat(result.get("afterStale")).isEqualTo(List.of(List.of(), List.of(), List.of()));
            assertThat(number(((Map<?, ?>) result.get("freshGrade")).get("reps"))).isEqualTo(1L);
            assertThat(number(((Map<?, ?>) result.get("freshSet")).get("cardsCompletedAt"))).isEqualTo(1800000000000L);
            assertThat(result.get("freshSessionSaved")).isEqualTo(true);
            for (Object rows : (List<?>) result.get("restored")) {
                assertThat((List<?>) rows).hasSize(1);
            }
            assertThat(number(result.get("afterImport"))).isEqualTo(number(result.get("g1")));
            assertThat(result.get("invalidGenerations")).isEqualTo(List.of(0, 0, 0, 0));
        }
    }

    @Test
    void corruptRecordsAreSkippedNotFatal() {
        Map<?, ?> result = (Map<?, ?>) page.evaluate("""
                async () => {
                  const { openStore } = await import('/js/learn/store.js');
                  const store = await openStore();
                  await store.grade({ kind: 'vocab', track: 'reading', id: 'ada066edfd', level: 'N5' }, 'good', 1800000000000);
                  const db = await new Promise((resolve, reject) => {
                    const request = indexedDB.open('kaidoku', 1);
                    request.onsuccess = () => resolve(request.result);
                    request.onerror = () => reject(request.error);
                  });
                  try {
                    await new Promise((resolve, reject) => {
                      const transaction = db.transaction('progress', 'readwrite');
                      transaction.oncomplete = () => resolve();
                      transaction.onabort = () => reject(transaction.error);
                      transaction.objectStore('progress').put({ key: 'vocab:reading:x', kind: 'vocab', junk: true });
                      transaction.objectStore('progress').put({ key: 'bad' });
                    });
                    const records = await store.records('vocab');
                    const storedCount = await new Promise((resolve, reject) => {
                      const transaction = db.transaction('progress', 'readonly');
                      const request = transaction.objectStore('progress').count();
                      transaction.oncomplete = () => resolve(request.result);
                      transaction.onabort = () => reject(transaction.error);
                    });
                    return { persistent: store.persistent, ids: records.map(record => record.id), skipped: store.skipped, storedCount };
                  } finally {
                    db.close();
                  }
                }
                """);

        assertThat(result.get("persistent")).isEqualTo(true);
        assertThat(result.get("ids")).isEqualTo(List.of("ada066edfd"));
        assertThat(number(result.get("skipped"))).isGreaterThanOrEqualTo(2L);
        assertThat(number(result.get("storedCount"))).isEqualTo(3L);
    }

    @Test
    void fallsBackWhenIndexedDbIsUnavailable() {
        Map<?, ?> result = (Map<?, ?>) page.evaluate("""
                async () => {
                  const { openStore } = await import('/js/learn/store.js');
                  const store = await openStore({ idb: undefined });
                  const saved = await store.grade({ kind: 'vocab', track: 'meaning', id: 'ada066edfd', level: 'N5' },
                    'good', 1800000000000);
                  const records = await store.records('vocab');
                  const denied = await openStore({ idb: { open() { throw new Error('denied'); } } });
                  return {
                    persistent: store.persistent, problem: store.problem, saved, records,
                    deniedPersistent: denied.persistent, deniedProblem: denied.problem
                  };
                }
                """);

        assertThat(result.get("persistent")).isEqualTo(false);
        assertThat(result.get("problem")).isEqualTo("unavailable");
        assertThat(number(((Map<?, ?>) result.get("saved")).get("reps"))).isEqualTo(1L);
        assertThat(result.get("records")).isEqualTo(List.of(result.get("saved")));
        assertThat(result.get("deniedPersistent")).isEqualTo(false);
        assertThat(result.get("deniedProblem")).isEqualTo("unavailable");
    }

    @Test
    void exportsAndImportsWithoutLosingNewerData() {
        List<?> results = (List<?>) page.evaluate("""
                async () => {
                  const { openStore } = await import('/js/learn/store.js');
                  const outcomes = [];
                  for (const store of [await openStore(), await openStore({ idb: undefined })]) {
                    const now = 1800000000000;
                    await store.setMeta('datasetVersion', 'test-version');
                    await store.grade({ kind: 'vocab', track: 'reading', id: 'ada066edfd', level: 'N5' }, 'good', now);
                    await store.grade({ kind: 'vocab', track: 'meaning', id: 'ada066edfd', level: 'N5' }, 'again', now + 1);
                    await store.grade({ kind: 'kanji', track: 'reading', id: '日', level: 'N5' }, 'easy', now + 2);
                    await store.updateSet('vocab', 'vocab-n5-1', 'N5', record => ({
                      ...record, cardsCompletedAt: now + 3,
                      reading: { attempts: 2, last: { correct: 8, total: 10, at: now + 3 }, best: { correct: 9, total: 10, at: now + 2 } },
                      meaning: { attempts: 1, last: { correct: 4, total: 5, at: now + 2 }, best: { correct: 4, total: 5, at: now + 2 } }
                    }), now + 3);
                    await store.addSession({
                      id: 'original', kind: 'vocab', scope: 'vocab-n5-1', mode: 'reading', startedAt: now, finishedAt: now + 3,
                      total: 10, correct: 8, missed: [{ id: 'first', track: 'reading' }, { id: 'second', track: 'reading' }]
                    });
                    const exported = await store.exportData(now + 10);
                    await store.reset();
                    const empty = [await store.records('kanji'), await store.records('vocab'),
                      await store.sets('vocab'), await store.sessions('vocab')];
                    const retainedMeta = await store.getMeta('datasetVersion');
                    const restored = await store.importData(exported, now + 100);
                    const roundTrip = await store.exportData(now + 101);
                    const previous = await store.record('vocab', 'reading', 'ada066edfd');
                    const older = { ...previous, lastReviewedAt: now + 999999, reps: 0, intervalDays: 365 };
                    const incomingSet = {
                      ...exported.sets[0], cardsCompletedAt: now - 20, updatedAt: now + 50,
                      reading: { attempts: 1, last: { correct: 4, total: 10, at: now + 50 }, best: { correct: 10, total: 10, at: now - 10 } },
                      meaning: { attempts: 3, last: { correct: 3, total: 5, at: now + 1 }, best: { correct: 4, total: 5, at: now + 50 } }
                    };
                    await store.importData({ ...exported, progress: [older], sets: [incomingSet],
                      sessions: [{ ...exported.sessions[0], id: 'incoming', finishedAt: now + 50 }] }, now + 200);
                    const afterOlder = await store.record('vocab', 'reading', 'ada066edfd');
                    const mergedSet = (await store.sets('vocab'))[0];
                    const sessionIds = (await store.sessions('vocab')).map(session => session.id);
                    await store.importData({ ...exported, progress: [{ ...previous, reps: 7 }], sets: [], sessions: [] }, now + 201);
                    const tied = await store.record('vocab', 'reading', 'ada066edfd');
                    let formatError = null;
                    try {
                      await store.importData({ format: 'x' }, now);
                    } catch (error) {
                      formatError = error.message;
                    }
                    const skippedBefore = store.skipped;
                    const invalid = await store.importData({ ...exported, progress: [{ key: 'bad' }], sets: [], sessions: [] }, now);
                    outcomes.push({
                      persistent: store.persistent, exported, empty, retainedMeta, restored, roundTrip, previous, afterOlder,
                      mergedSet, sessionIds, tied, formatError, invalid, skippedIncrease: store.skipped - skippedBefore
                    });
                  }
                  return outcomes;
                }
                """);

        assertThat(results).hasSize(2);
        assertThat(((Map<?, ?>) results.get(0)).get("persistent")).isEqualTo(true);
        assertThat(((Map<?, ?>) results.get(1)).get("persistent")).isEqualTo(false);
        for (Object value : results) {
            Map<?, ?> result = (Map<?, ?>) value;
            Map<?, ?> exported = (Map<?, ?>) result.get("exported");
            assertThat(exported.get("format")).isEqualTo("kaidoku-progress");
            assertThat(number(exported.get("version"))).isEqualTo(1L);
            assertThat(number(exported.get("exportedAt"))).isEqualTo(1800000000010L);
            assertThat(exported.get("datasetVersion")).isEqualTo("test-version");
            assertThat((List<?>) exported.get("progress")).hasSize(3);
            assertThat((List<?>) exported.get("sets")).hasSize(1);
            assertThat((List<?>) exported.get("sessions")).hasSize(1);
            assertThat(result.get("empty")).isEqualTo(List.of(List.of(), List.of(), List.of(), List.of()));
            assertThat(result.get("retainedMeta")).isEqualTo("test-version");

            Map<?, ?> restored = (Map<?, ?>) result.get("restored");
            assertThat(number(restored.get("imported"))).isEqualTo(5L);
            assertThat(number(restored.get("skipped"))).isZero();
            Map<?, ?> roundTrip = (Map<?, ?>) result.get("roundTrip");
            for (String table : List.of("progress", "sets", "sessions")) {
                assertThat(roundTrip.get(table)).isEqualTo(exported.get(table));
            }
            assertThat(result.get("afterOlder")).isEqualTo(result.get("previous"));
            assertThat(number(((Map<?, ?>) result.get("tied")).get("reps"))).isEqualTo(7L);

            Map<?, ?> mergedSet = (Map<?, ?>) result.get("mergedSet");
            assertThat(number(mergedSet.get("cardsCompletedAt"))).isEqualTo(1799999999980L);
            Map<?, ?> reading = (Map<?, ?>) mergedSet.get("reading");
            assertThat(number(reading.get("attempts"))).isEqualTo(2L);
            Map<?, ?> last = (Map<?, ?>) reading.get("last");
            assertThat(number(last.get("correct"))).isEqualTo(4L);
            assertThat(number(last.get("at"))).isEqualTo(1800000000050L);
            Map<?, ?> best = (Map<?, ?>) reading.get("best");
            assertThat(number(best.get("correct"))).isEqualTo(10L);
            assertThat(number(best.get("at"))).isEqualTo(1799999999990L);
            Map<?, ?> meaning = (Map<?, ?>) mergedSet.get("meaning");
            assertThat(number(meaning.get("attempts"))).isEqualTo(3L);
            assertThat(number(((Map<?, ?>) meaning.get("last")).get("at"))).isEqualTo(1800000000002L);
            assertThat(number(((Map<?, ?>) meaning.get("best")).get("at"))).isEqualTo(1800000000050L);
            assertThat(result.get("sessionIds")).isEqualTo(List.of("incoming", "original"));
            assertThat(result.get("formatError")).isEqualTo("Not a Kaidoku progress file");
            Map<?, ?> invalid = (Map<?, ?>) result.get("invalid");
            assertThat(number(invalid.get("imported"))).isZero();
            assertThat(number(invalid.get("skipped"))).isEqualTo(1L);
            assertThat(number(result.get("skippedIncrease"))).isEqualTo(1L);
        }
    }

    @Test
    void keepsOnlyTheNewestSessions() {
        Map<?, ?> result = (Map<?, ?>) page.evaluate("""
                async () => {
                  const { openStore } = await import('/js/learn/store.js');
                  const store = await openStore();
                  const now = 1800000000000;
                  for (let i = 0; i < 205; i++) {
                    await store.addSession({
                      id: `session-${i}`, kind: 'kanji', scope: 'kanji-n5-1', mode: 'reading',
                      startedAt: now, finishedAt: now + i, total: 2, correct: 1,
                      missed: [{ id: '日', track: 'reading' }]
                    });
                  }
                  const sessions = await store.sessions('kanji');
                  return {
                    persistent: store.persistent, ids: sessions.map(session => session.id),
                    newestAt: sessions[0].finishedAt, oldestAt: sessions[sessions.length - 1].finishedAt
                  };
                }
                """);

        assertThat(result.get("persistent")).isEqualTo(true);
        assertThat(result.get("ids")).isEqualTo(IntStream.range(0, 200).mapToObj(i -> "session-" + (204 - i)).toList());
        assertThat(number(result.get("newestAt"))).isEqualTo(1800000000204L);
        assertThat(number(result.get("oldestAt"))).isEqualTo(1800000000005L);
    }
}
