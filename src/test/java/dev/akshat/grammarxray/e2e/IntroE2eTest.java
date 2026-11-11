package dev.akshat.grammarxray.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.ReducedMotion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntroE2eTest {

    private static final String RECORD_COMPLETION = """
            new MutationObserver((records, observer) => {
              if (document.querySelector('.ki-intro')) {
                window.__introMountedAt = performance.now();
                observer.disconnect();
              }
            }).observe(document, { childList: true, subtree: true });
            window.addEventListener('kaidoku:intro-complete', e => {
              window.__introReason = e.detail.reason;
              window.__introAt = performance.now();
            });
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

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    private void createContext(Browser.NewContextOptions options) {
        context = browser.newContext(options);
        context.addInitScript(RECORD_COMPLETION);
        page = context.newPage();
    }

    private void open(String path) {
        page.navigate("http://localhost:" + port + path);
    }

    private void open(Browser.NewContextOptions options) {
        createContext(options);
        open("/");
        assertThat(page.locator(".ki-intro")).hasCount(1);
    }

    private void waitUntil(String predicate, long timeout) {
        long deadline = System.currentTimeMillis() + timeout;
        while (!Boolean.TRUE.equals(page.evaluate(predicate))) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for " + predicate);
            }
            page.waitForTimeout(50);
        }
    }

    private double playedFor() {
        return ((Number) page.evaluate("() => window.__introAt - window.__introMountedAt")).doubleValue();
    }

    private void waitForReason(String reason, long timeout) {
        waitUntil("() => window.__introReason === '" + reason + "'", timeout);
    }

    private void assertDashboardRevealed() {
        assertThat(page.locator(".ki-intro")).hasCount(0);
        assertThat(page.locator("html.ki-intro-pending")).hasCount(0);
        assertThat(page.locator("[inert]")).hasCount(0);
        assertThat(page.locator(".module-card")).hasCount(3);
        for (int i = 0; i < 3; i++) {
            assertThat(page.locator(".module-card").nth(i)).isVisible();
        }
    }

    private void useDashboard() {
        assertDashboardRevealed();
        page.locator(".module-card[data-module='kanji']").click();
        assertThat(page).hasURL("http://localhost:" + port + "/kanji");
    }

    private void assertSelectedTheme(String theme) {
        assertThat(page.locator(".ki-intro")).hasAttribute("data-theme", theme);
        org.assertj.core.api.Assertions.assertThat(page.evaluate("""
                () => getComputedStyle(document.querySelector('.ki-intro')).backgroundColor
                  === getComputedStyle(document.body).backgroundColor
                """)).isEqualTo(true);
    }

    @Test
    void playsOverTheRealDashboardAndRemovesItself() {
        open(new Browser.NewContextOptions());

        assertThat(page.locator("body > .ki-intro:first-child")).hasCount(1);
        assertThat(page.locator(".module-card")).hasCount(3);
        org.assertj.core.api.Assertions.assertThat(page.evaluate("""
                () => document.querySelector('header').inert && document.querySelector('main').inert
                """)).isEqualTo(true);
        String text = page.locator(".ki-intro").textContent();
        org.assertj.core.api.Assertions.assertThat(text)
                .contains("Kaidoku", "Japanese Reading Companion", "ようになった", "ニチ・ジツ", "to meet");
        org.assertj.core.api.Assertions.assertThat(text.toLowerCase(Locale.ROOT))
                .doesNotContain("placeholder", "your app", "dashboard", "demo");

        waitForReason("completed", 15_000);
        org.assertj.core.api.Assertions.assertThat(playedFor()).isBetween(3_900.0, 5_500.0);
        useDashboard();
    }

    @Test
    void skipButtonRevealsTheDashboardAtOnce() {
        open(new Browser.NewContextOptions());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Skip intro").setExact(true)).click();

        waitForReason("skipped", 1_500);
        assertDashboardRevealed();
        page.keyboard().press("Tab");
        org.assertj.core.api.Assertions.assertThat(page.evaluate("""
                () => {
                  const focused = document.activeElement;
                  return focused !== document.body && focused !== document.documentElement
                    && focused.isConnected && document.body.contains(focused)
                    && !focused.closest('.ki-intro, [inert]');
                }
                """)).isEqualTo(true);
    }

    @Test
    void escapeSkips() {
        open(new Browser.NewContextOptions());
        page.keyboard().press("Escape");

        waitForReason("skipped", 1_500);
        useDashboard();
    }

    @Test
    void reducedMotionShowsAStillFrameBriefly() {
        open(new Browser.NewContextOptions().setReducedMotion(ReducedMotion.REDUCE));

        assertThat(page.locator(".ki-intro.ki-reduced")).hasCount(1);
        assertThat(page.locator(".ki-specimens")).isHidden();
        org.assertj.core.api.Assertions.assertThat(page.locator(".ki-specimens")
                .evaluate("el => getComputedStyle(el).display")).isEqualTo("none");
        waitForReason("reduced-motion", 15_000);
        org.assertj.core.api.Assertions.assertThat(playedFor()).isBetween(1_800.0, 3_000.0);
        assertDashboardRevealed();
    }

    @Test
    void playsOncePerSession() {
        open(new Browser.NewContextOptions());
        waitForReason("completed", 6_000);

        page.reload();
        waitForReason("already-played", 1_500);
        assertDashboardRevealed();
        open("/kanji");
        assertThat(page.locator(".lesson-card").first()).isVisible();
        open("/");
        waitForReason("already-played", 1_500);
        assertDashboardRevealed();

        context.close();
        createContext(new Browser.NewContextOptions());
        open("/kanji?level=N5");
        assertThat(page.locator(".lesson-card").first()).isVisible();
        open("/");
        waitForReason("already-played", 1_500);
        assertDashboardRevealed();
    }

    @Test
    void newLaunchPlaysAgain() {
        open(new Browser.NewContextOptions());
        waitForReason("completed", 6_000);
        assertDashboardRevealed();

        page = context.newPage();
        open("/");
        assertThat(page.locator(".ki-intro")).hasCount(1);
        waitForReason("completed", 6_000);
        assertDashboardRevealed();
    }

    @Test
    void matchesTheSelectedTheme() {
        open(new Browser.NewContextOptions().setColorScheme(ColorScheme.DARK));
        assertSelectedTheme("dark");

        context.close();
        createContext(new Browser.NewContextOptions().setColorScheme(ColorScheme.LIGHT));
        context.addInitScript("localStorage.setItem('kaidoku.theme', 'dark');");
        open("/");
        assertSelectedTheme("dark");

        context.close();
        open(new Browser.NewContextOptions().setColorScheme(ColorScheme.LIGHT));
        assertSelectedTheme("light");
    }

    @Test
    void dashboardStillWorksWhenStorageAccessThrows() {
        createContext(new Browser.NewContextOptions());
        context.addInitScript("""
                for (const name of ['sessionStorage', 'localStorage']) {
                  Object.defineProperty(window, name, {
                    get() { throw new DOMException('denied', 'SecurityError'); }
                  });
                }
                """);
        List<String> errors = new ArrayList<>();
        page.onPageError(errors::add);
        open("/");

        waitUntil("""
                async () => {
                  await import('/js/home.js');
                  return !document.querySelector('.ki-intro') && !document.querySelector('[inert]')
                    && !document.documentElement.classList.contains('ki-intro-pending');
                }
                """, 6_000);
        useDashboard();
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
    }

    @Test
    void killSwitchShowsNothing() {
        createContext(new Browser.NewContextOptions());
        context.route("**/js/intro-config.js", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("text/javascript").setBody("export const INTRO = { enabled: false };")));
        context.addInitScript("""
                window.__introAppeared = false;
                new MutationObserver(records => {
                  if (records.some(record => [...record.addedNodes].some(node => node.nodeType === 1
                    && (node.matches('.ki-intro') || node.querySelector('.ki-intro'))))) {
                    window.__introAppeared = true;
                  }
                }).observe(document, { childList: true, subtree: true });
                """);
        open("/");

        waitUntil("""
                async () => {
                  await import('/js/home.js');
                  return !document.documentElement.classList.contains('ki-intro-pending');
                }
                """, 1_500);
        org.assertj.core.api.Assertions.assertThat(page.evaluate("() => window.__introAppeared")).isEqualTo(false);
        useDashboard();
    }

    @Test
    void introWorksAtPhoneWidth() {
        open(new Browser.NewContextOptions().setViewportSize(320, 640));

        org.assertj.core.api.Assertions.assertThat(page.evaluate("""
                () => document.documentElement.scrollWidth <= document.documentElement.clientWidth
                """)).isEqualTo(true);
        org.assertj.core.api.Assertions.assertThat(page.locator(".ki-skip").evaluate("""
                el => {
                  const rect = el.getBoundingClientRect();
                  return rect.left >= 0 && rect.top >= 0 && rect.right <= innerWidth && rect.bottom <= innerHeight;
                }
                """)).isEqualTo(true);
        waitForReason("completed", 6_000);
        assertDashboardRevealed();
    }
}
