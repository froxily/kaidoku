package dev.akshat.grammarxray.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
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

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudyProgressE2eTest {

    private static final String SET = "/kanji/quiz/kanji-n5-1/reading";
    private static final String ANSWER = """
            async () => {
              const prompt = document.querySelector('#quiz-prompt').textContent;
              const { questions } = await (await fetch('/learning/questions.json')).json();
              const question = questions.find(q => q.type === 'kanji_on_reading' && q.prompt.character === prompt);
              const options = [...document.querySelectorAll('.option .option-text')].map(o => o.textContent);
              return [options.indexOf(question.answer) + 1, options.findIndex(o => o !== question.answer) + 1];
            }
            """;

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
        context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    private void open(String path) {
        page.navigate("http://localhost:" + port + path);
    }

    private void answerAll(boolean correct) {
        for (int i = 0; i < 8; i++) {
            assertThat(page.locator(".option:not([aria-disabled])").first()).isVisible();
            var choices = (java.util.List<?>) page.evaluate(ANSWER);
            page.keyboard().press(String.valueOf(((Number) choices.get(correct ? 0 : 1)).intValue()));
            page.locator("[data-role='next']").click();
        }
        assertThat(page.locator(".score")).isVisible();
    }

    private long recordCount(String kind) {
        return ((Number) page.evaluate("async kind => (await (await import('/js/learn/store.js')).progressStore()).records(kind).then(r => r.length)", kind)).longValue();
    }

    @Test
    void meaningCheckRequiresRecallBeforeRevealing() {
        open("/vocab/quiz/vocab-n5-1/meaning");
        assertThat(page.locator("#recall")).isFocused();

        page.locator("[data-role='check']").click();
        assertThat(page.locator(".recall-error")).isVisible();
        assertThat(page.locator(".summary-meaning")).hasCount(0);

        page.locator("#recall").fill("to come across someone");
        page.keyboard().press("Enter");
        assertThat(page.locator(".your-recall q")).hasText("to come across someone");
        assertThat(page.locator(".summary-meaning")).isVisible();
        assertThat(page.locator("[data-role='known']")).isFocused();

        page.keyboard().press("2");
        assertThat(page.locator(".lesson-counter")).hasText(Pattern.compile("^2 / \\d+$"));
        page.locator("[data-role='unknown-now']").click();
        assertThat(page.locator(".feedback .feedback-title")).hasText("Here is the meaning");
        assertThat(page.locator("[data-role='next']")).isFocused();
    }

    @Test
    void quizResultsSurviveAReload() {
        open(SET);
        answerAll(true);
        assertThat(page.locator(".score")).hasText("8 / 8");

        open("/kanji/lesson/kanji-n5-1");
        page.reload();
        assertThat(page.locator("a.activity[href$='/reading'] .activity-note")).hasText("Last 8/8 · best 8/8");

        open("/kanji/" + "国");
        assertThat(page.locator(".progress-facts")).containsText("Learning · next review tomorrow · 1 of 1 right");
        assertThat(page.locator(".progress-facts")).containsText("Meaning");
        org.assertj.core.api.Assertions.assertThat(recordCount("kanji")).isEqualTo(8);
    }

    @Test
    void wrongAnswersComeBackForReviewAndRetriesDoNotDuplicate() {
        page.clock().install();
        open(SET);
        answerAll(false);
        page.getByText("Retry 8 missed").click();
        answerAll(false);
        org.assertj.core.api.Assertions.assertThat(recordCount("kanji")).isEqualTo(8);
        Object reps = page.evaluate("async () => (await (await import('/js/learn/store.js')).progressStore()).records('kanji').then(r => r.map(x => x.reps + ':' + x.incorrect + ':' + x.state))");
        org.assertj.core.api.Assertions.assertThat(((java.util.List<?>) reps).stream().map(String::valueOf).toList()).containsOnly("2:2:learning");

        open("/kanji?level=N5");
        assertThat(page.locator(".review-panel [role='status']")).containsText("Next review in 10 min");

        page.clock().fastForward("11:00");
        page.reload();
        assertThat(page.locator(".review-panel [role='status']")).hasText("8 reviews due now.");
        page.locator(".review-panel a", new Page.LocatorOptions().setHasText("Review N5 (8)")).click();
        assertThat(page).hasURL(Pattern.compile(".*/kanji/review\\?level=N5$"));
        assertThat(page.locator(".lesson-counter")).hasText("1 / 8");

        open("/");
        assertThat(page.locator("[data-due='kanji']")).hasText("8 reviews due");
    }

    @Test
    void corruptRecordsAreIgnoredWithANotice() {
        open(SET);
        answerAll(true);
        page.evaluate("""
                () => new Promise((resolve, reject) => {
                  const request = indexedDB.open('kaidoku');
                  request.onsuccess = () => {
                    const tx = request.result.transaction('progress', 'readwrite');
                    tx.objectStore('progress').put({ key: 'kanji:reading:日', kind: 'kanji', track: 'reading', id: '日', ease: 'broken' });
                    tx.oncomplete = () => { request.result.close(); resolve(); };
                    tx.onerror = () => reject(tx.error);
                  };
                })
                """);

        open("/kanji?level=N5");
        assertThat(page.locator(".notice")).containsText("could not be read");
        assertThat(page.locator(".lesson-card").first()).isVisible();
        assertThat(page.locator(".level-progress")).isVisible();
    }

    @Test
    void worksWithoutBrowserStorage() {
        context.addInitScript("Object.defineProperty(window, 'indexedDB', { value: undefined, configurable: true });");
        open("/kanji?level=N5");
        assertThat(page.locator(".notice.error")).containsText("can’t be saved");

        open(SET);
        answerAll(true);
        assertThat(page.locator(".score")).hasText("8 / 8");
        assertThat(page.locator(".save-notice")).containsText("can’t be saved");
    }

    @Test
    void backsUpResetsAndRestoresProgress() {
        open(SET);
        answerAll(true);
        open("/kanji?level=N5");

        Download download = page.waitForDownload(() -> page.locator("[data-role='export']").click());
        Path backup = download.path();
        assertThat(page.locator(".data-status")).containsText("8 review records");

        page.onceDialog(dialog -> dialog.accept());
        page.locator("[data-role='reset']").click();
        assertThat(page.locator(".data-status")).hasText("Progress cleared.");
        page.reload();
        org.assertj.core.api.Assertions.assertThat(recordCount("kanji")).isZero();

        page.locator("#import-file").setInputFiles(backup);
        assertThat(page.locator(".data-status")).containsText("Restored");
        org.assertj.core.api.Assertions.assertThat(recordCount("kanji")).isEqualTo(8);

        page.locator("#import-file").setInputFiles(new com.microsoft.playwright.options.FilePayload(
                "notes.json", "application/json", "{\"hello\":1}".getBytes()));
        assertThat(page.locator(".data-status")).hasText("That file is not a Kaidoku progress backup. Nothing was changed.");
    }

    @Test
    void meaningCheckWorksByTouchOnAPhone() {
        context.close();
        context = browser.newContext(new Browser.NewContextOptions().setHasTouch(true).setIsMobile(true).setViewportSize(375, 812));
        page = context.newPage();
        open("/kanji/quiz/kanji-n5-1/meaning");
        page.locator("#recall").fill("day");
        page.locator("[data-role='check']").tap();
        page.locator("[data-role='known']").tap();
        assertThat(page.locator(".lesson-counter")).hasText("2 / 10");

        Locator next = page.locator("[data-role='unknown-now']");
        next.tap();
        assertThat(page.locator("[data-role='next']")).isVisible();
        org.assertj.core.api.Assertions.assertThat(page.evaluate(
                "() => document.documentElement.scrollWidth <= document.documentElement.clientWidth")).isEqualTo(true);
    }
}
