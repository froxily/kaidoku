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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfflineE2eTest {

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

    private void warmLearningPages() {
        open("/");
        assertThat(page.locator(".module-card")).hasCount(3);
        page.waitForFunction("() => navigator.serviceWorker.ready.then(() => true)");
        if (Boolean.FALSE.equals(page.evaluate("() => navigator.serviceWorker.controller !== null"))) {
            page.reload();
        }
        page.waitForFunction("() => navigator.serviceWorker.controller !== null");

        open("/kanji?level=N5");
        assertThat(page.locator(".lesson-card").first()).isVisible();
        open("/vocab?level=N5");
        assertThat(page.locator(".lesson-card").first()).isVisible();
        open("/kanji/library");
        assertThat(page.locator("#library-search")).isVisible();
        assertThat(page.locator(".tile-glyph").first()).isVisible();
        open("/kanji/lesson/kanji-n5-1");
        assertThat(page.locator(".activity")).hasCount(3);
        open("/kanji/quiz/kanji-n5-1/reading");
        assertThat(page.locator(".option")).hasCount(4);
    }

    @Test
    void learningWorksOfflineAfterOneVisit() {
        warmLearningPages();
        context.setOffline(true);

        open("/");
        assertThat(page.locator(".module-card")).hasCount(3);
        open("/kanji?level=N5");
        assertThat(page.locator(".lesson-card").first()).isVisible();
        open("/vocab/library");
        page.locator("#library-search").fill("taberu");
        assertThat(page.locator(".result-count")).hasText("1 word found");
        assertThat(page.locator(".word-row")).isVisible();
        open("/kanji/" + "日");
        assertThat(page.locator("h1.glyph-title")).hasText("日");
        open("/kanji/lesson/kanji-n5-1");
        assertThat(page.locator(".activity")).hasCount(3);
        open("/kanji/quiz/kanji-n5-1/reading");
        assertThat(page.locator(".option")).hasCount(4);
        assertThat(page.locator("#quiz-prompt")).isVisible();
    }

    @Test
    void grammarExplainsItNeedsAConnection() {
        warmLearningPages();
        context.setOffline(true);
        open("/grammar");

        assertThat(page.locator("#offline-note")).isVisible();
        assertThat(page.locator("#offline-note")).containsText("needs an internet connection");
        assertThat(page.locator("#analyze")).isDisabled();
        assertThat(page.locator("#sample")).isDisabled();
    }

    @Test
    void doesNotCacheTheAnalyzer() {
        warmLearningPages();
        open("/grammar");
        page.locator("#text").fill("日本語を勉強しています。");
        var response = page.waitForResponse(result -> result.url().endsWith("/api/analyze")
                && result.request().method().equals("POST"), () -> page.locator("#analyze").click());
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(200);
        assertThat(page.locator("#output mark").first()).isVisible();

        Object urls = page.evaluate("""
                async () => {
                  const names = await caches.keys();
                  const requests = await Promise.all(names.map(async name => (await caches.open(name)).keys()));
                  return requests.flat().map(request => request.url);
                }
                """);
        org.assertj.core.api.Assertions.assertThat((List<?>) urls).isNotEmpty()
                .allSatisfy(url -> org.assertj.core.api.Assertions.assertThat((String) url)
                        .doesNotContain("/api/analyze"));
    }
}
