package dev.akshat.grammarxray.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ReducedMotion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntroE2eTest {

    private static final String PLAY = """
            async maxDurationMs => {
              const { playIntro } = await import('/js/intro.js');
              window.introResult = null;
              playIntro({
                enabled: true, version: 1, type: 'image', alt: 'Kaidoku', maxDurationMs,
                sources: [{ src: 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22200%22 height=%22120%22/%3E' }]
              }).then(result => { window.introResult = result; });
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

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    private void open(Browser.NewContextOptions options) {
        context = browser.newContext(options);
        page = context.newPage();
        page.navigate("http://localhost:" + port + "/");
        assertThat(page.locator(".module-card")).hasCount(3);
    }

    @Test
    void showsNoIntroUntilAnAssetIsConfigured() {
        open(new Browser.NewContextOptions());

        assertThat(page.locator(".intro")).hasCount(0);
        assertThat(page.locator(".module-card").first()).isVisible();
    }

    @Test
    void introCanBeSkippedAndPlaysOnlyOnce() {
        open(new Browser.NewContextOptions());
        page.evaluate(PLAY, 60000);

        assertThat(page.locator(".intro[role='dialog']")).isVisible();
        assertThat(page.locator(".intro-skip")).isFocused();
        org.assertj.core.api.Assertions.assertThat(page.evaluate("() => document.querySelector('main').inert")).isEqualTo(true);

        page.keyboard().press("Escape");
        assertThat(page.locator(".intro")).hasCount(0);
        org.assertj.core.api.Assertions.assertThat(page.evaluate("() => window.introResult")).isEqualTo("skipped");
        org.assertj.core.api.Assertions.assertThat(page.evaluate("() => document.querySelector('main').inert")).isEqualTo(false);

        page.evaluate(PLAY, 60000);
        page.waitForFunction("() => window.introResult === 'not-shown'");
        assertThat(page.locator(".intro")).hasCount(0);
    }

    @Test
    void introEndsByItselfAfterItsMaximumDuration() {
        open(new Browser.NewContextOptions());
        page.evaluate(PLAY, 600);

        page.waitForFunction("() => window.introResult === 'played'");
        assertThat(page.locator(".intro")).hasCount(0);
    }

    @Test
    void dashboardStillWorksWhenStorageAccessThrows() {
        context = browser.newContext();
        context.addInitScript("Object.defineProperty(window, 'localStorage', { get() { throw new DOMException('denied', 'SecurityError'); } });");
        page = context.newPage();
        java.util.List<String> errors = new java.util.ArrayList<>();
        page.onPageError(errors::add);
        page.navigate("http://localhost:" + port + "/");

        assertThat(page.locator(".module-card")).hasCount(3);
        page.waitForFunction("async () => { const { playIntro } = await import('/js/intro.js'); return (await playIntro({ enabled: true, version: 1, type: 'image', sources: [{ src: 'x.png' }] })) === 'not-shown'; }");
        assertThat(page.locator(".intro")).hasCount(0);
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
    }

    @Test
    void introIsNotShownWhenReducedMotionIsRequested() {
        open(new Browser.NewContextOptions().setReducedMotion(ReducedMotion.REDUCE));
        page.evaluate(PLAY, 60000);

        page.waitForFunction("() => window.introResult === 'not-shown'");
        assertThat(page.locator(".intro")).hasCount(0);
    }
}
