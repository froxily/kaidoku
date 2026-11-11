package dev.akshat.grammarxray.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
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
import java.util.concurrent.atomic.AtomicReference;

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

    private void warmDashboard() {
        open("/");
        assertThat(page.locator(".module-card")).hasCount(3);
        page.evaluate("() => navigator.serviceWorker.ready.then(() => true)");
        page.waitForFunction("() => navigator.serviceWorker.controller !== null");
        waitUntil("""
                async () => {
                  const names = await caches.keys();
                  const shell = await caches.open(names.find(name => name.startsWith('kaidoku-shell-')));
                  const version = await shell.match('/__data-version');
                  if (!version) return false;
                  const cache = await caches.open('kaidoku-data-' + await version.text());
                  const response = await cache.match('/learning/manifest.json');
                  if (!response) return false;
                  const manifest = await response.json();
                  const paths = ['/learning/manifest.json', ...Object.keys(manifest.artifacts).map(name => '/learning/' + name),
                    '/api/learning/summary', '/api/learning/kanji/lessons', '/api/learning/vocab/lessons'];
                  return (await Promise.all(paths.map(path => cache.match(path)))).every(Boolean);
                }
                """);
    }

    private void waitUntil(String predicate, Object... arg) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!Boolean.TRUE.equals(arg.length == 0 ? page.evaluate(predicate) : page.evaluate(predicate, arg[0]))) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for " + predicate);
            }
            page.waitForTimeout(100);
        }
    }

    private String activeVersion() {
        return (String) page.evaluate("""
                async () => {
                  const names = await caches.keys();
                  const shell = await caches.open(names.find(name => name.startsWith('kaidoku-shell-')));
                  return (await shell.match('/__data-version')).text();
                }
                """);
    }

    private void warmLearningPages() {
        warmDashboard();
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
        warmDashboard();
        context.setOffline(true);

        open("/");
        assertThat(page.locator(".module-card")).hasCount(3);
        open("/kanji?level=N5");
        assertThat(page.locator(".lesson-card").first()).isVisible();
        open("/vocab?level=N5");
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
    void switchesDataVersionsOnlyAfterTheCompleteSetIsCached() {
        warmDashboard();
        String oldVersion = activeVersion();
        String newVersion = oldVersion + "-next";
        String manifest = (String) page.evaluate("""
                async version => {
                  const manifest = await (await fetch('/learning/manifest.json')).json();
                  return JSON.stringify({ ...manifest, dataset_version: version });
                }
                """, newVersion);
        context.route("**/learning/manifest.json", route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200).setContentType("application/json").setBody(manifest)));
        AtomicReference<Route> pendingDownload = new AtomicReference<>();
        context.route("**/learning/kanji.json", pendingDownload::set);

        page.reload();
        page.waitForCondition(() -> pendingDownload.get() != null);
        org.assertj.core.api.Assertions.assertThat(activeVersion()).isEqualTo(oldVersion);
        org.assertj.core.api.Assertions.assertThat(page.evaluate(
                "async version => (await caches.keys()).includes('kaidoku-data-' + version)", oldVersion))
                .isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(page.evaluate(
                "async () => (await (await fetch('/learning/manifest.json')).json()).dataset_version"))
                .isEqualTo(oldVersion);

        pendingDownload.get().abort();
        waitUntil("async version => !(await caches.keys()).includes('kaidoku-data-' + version)", newVersion);
        org.assertj.core.api.Assertions.assertThat(activeVersion()).isEqualTo(oldVersion);
        context.unroute("**/learning/manifest.json");
        context.unroute("**/learning/kanji.json");
        context.setOffline(true);
        open("/kanji?level=N5");
        assertThat(page.locator(".lesson-card").first()).isVisible();

        context.setOffline(false);
        context.route("**/learning/manifest.json", route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200).setContentType("application/json").setBody(manifest)));
        open("/");
        waitUntil("""
                async versions => {
                  const names = await caches.keys();
                  if (names.includes('kaidoku-data-' + versions.old)) return false;
                  const shell = await caches.open(names.find(name => name.startsWith('kaidoku-shell-')));
                  return await (await shell.match('/__data-version')).text() === versions.next;
                }
                """, Map.of("old", oldVersion, "next", newVersion));
        org.assertj.core.api.Assertions.assertThat(activeVersion()).isEqualTo(newVersion);
        org.assertj.core.api.Assertions.assertThat(page.evaluate(
                "async () => (await (await fetch('/learning/manifest.json')).json()).dataset_version"))
                .isEqualTo(newVersion);
        context.setOffline(true);
        open("/kanji/lesson/kanji-n5-1");
        assertThat(page.locator(".activity")).hasCount(3);
        open("/vocab?level=N5");
        assertThat(page.locator(".lesson-card").first()).isVisible();
    }

    @Test
    void redirectedStylesheetDoesNotReplaceTheCachedCss() {
        warmDashboard();
        String cachedCss = (String) page.evaluate("""
                async () => {
                  const names = await caches.keys();
                  const cache = await caches.open(names.find(name => name.startsWith('kaidoku-shell-')));
                  return (await cache.match('/css/learn.css')).text();
                }
                """);
        open("/kanji?level=N5");
        context.route("**/css/learn.css", route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(302).setHeaders(Map.of("Location", "/about.html"))));
        page.reload();
        Map<?, ?> result = (Map<?, ?>) page.evaluate("""
                async () => {
                  const response = await fetch('/css/learn.css');
                  const names = await caches.keys();
                  const cache = await caches.open(names.find(name => name.startsWith('kaidoku-shell-')));
                  return { redirected: response.redirected, type: response.headers.get('Content-Type'),
                    cached: await (await cache.match('/css/learn.css')).text() };
                }
                """);
        org.assertj.core.api.Assertions.assertThat(result.get("redirected")).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat((String) result.get("type")).contains("text/html");
        org.assertj.core.api.Assertions.assertThat(result.get("cached")).isEqualTo(cachedCss);
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
