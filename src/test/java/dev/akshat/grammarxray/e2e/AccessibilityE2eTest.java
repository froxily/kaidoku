package dev.akshat.grammarxray.e2e;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ColorScheme;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessibilityE2eTest {

    private static final List<String> TAGS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
    private static final List<String> BLOCKING = List.of("serious", "critical");

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

    private record Screen(String name, String path, String ready, Consumer<Page> prepare) {
    }

    private List<Screen> screens() {
        return List.of(
                new Screen("dashboard", "/", ".module-card", p -> { }),
                new Screen("grammar with results", "/grammar", "#sample", p -> {
                    p.locator("#sample").click();
                    assertThat(p.locator("#output mark").first()).isVisible();
                    p.locator("#output mark").first().click();
                    assertThat(p.locator("#card")).isVisible();
                }),
                new Screen("kanji study sets", "/kanji?level=N5", ".lesson-card", p -> { }),
                new Screen("vocabulary study sets", "/vocab?level=N4", ".lesson-card", p -> { }),
                new Screen("kanji library", "/kanji/library?q=nichi", ".kanji-tile", p -> { }),
                new Screen("vocabulary library", "/vocab/library", ".word-row", p -> { }),
                new Screen("kanji detail", "/kanji/%E6%97%A5", "h1.glyph-title", p -> { }),
                new Screen("vocabulary detail", "/vocab/ada066edfd", "h1.word-title", p -> { }),
                new Screen("set page", "/kanji/lesson/kanji-n5-1", ".activity", p -> { }),
                new Screen("study card", "/vocab/lesson/vocab-n5-1?card=2", ".study", p -> { }),
                new Screen("reading quiz answered", "/kanji/quiz/kanji-n5-1/reading", ".option", p -> {
                    p.keyboard().press("1");
                    assertThat(p.locator(".feedback")).isVisible();
                }),
                new Screen("meaning check revealed", "/vocab/quiz/vocab-n5-1/meaning", "#recall", p -> {
                    p.locator("#recall").fill("to meet");
                    p.keyboard().press("Enter");
                    assertThat(p.locator(".your-recall")).isVisible();
                }),
                new Screen("quiz results", "/kanji/quiz/kanji-n5-1/meaning", "#recall", p -> {
                    for (int i = 0; i < 10; i++) {
                        p.locator("[data-role='unknown-now']").click();
                        p.locator("[data-role='next']").click();
                    }
                    assertThat(p.locator(".score")).isVisible();
                }),
                new Screen("review queue", "/kanji/review", "h1", p -> { }),
                new Screen("about", "/about", ".attribution", p -> { }),
                new Screen("not found", "/kanji/nope/extra/path", "h1", p -> { }));
    }

    @ParameterizedTest(name = "{0} at {1} px wide")
    @CsvSource({"/kanji?level=N5, 320, 1, [data-role='export']", "/vocab/quiz/vocab-n5-1/reading, 320, 1, .option >> nth=3",
            "/kanji/review, 640, 2, footer a >> nth=0"})
    void keyboardFocusIsNotHiddenBehindTheTabBar(String path, int width, int scale, String target) {
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(width, 450).setDeviceScaleFactor(scale));
        page = context.newPage();
        page.navigate("http://localhost:" + port + path);
        assertThat(page.locator(target)).isVisible();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        for (int i = 0; i < 20 && !Boolean.TRUE.equals(page.evaluate("""
                () => new Promise(resolve => {
                  const height = document.documentElement.scrollHeight;
                  setTimeout(() => resolve(document.documentElement.scrollHeight === height), 300);
                })
                """)); i++) {
        }
        page.locator(target).evaluate("""
                el => {
                  window.scrollBy(0, el.getBoundingClientRect().top - (innerHeight - 40));
                  let previous = null;
                  for (const candidate of document.querySelectorAll('a[href], button:not([disabled]), input:not([type=hidden]):not([tabindex="-1"])')) {
                    if (candidate === el) break;
                    if (candidate.offsetParent !== null) previous = candidate;
                  }
                  previous.focus({ preventScroll: true });
                }
                """);
        page.keyboard().press("Tab");
        List<?> focus = (List<?>) page.evaluate("""
                () => {
                  const el = document.activeElement;
                  const r = el.getBoundingClientRect();
                  const hit = document.elementFromPoint(r.left + Math.min(r.width / 2, 8), r.top + r.height / 2);
                  return [el.textContent.trim().slice(0, 20), el === hit || el.contains(hit)];
                }
                """);
        org.assertj.core.api.Assertions.assertThat(focus.get(1)).as("focused %s is visible", focus.get(0)).isEqualTo(true);
    }

    @org.junit.jupiter.api.Test
    void keyboardFocusIsDrawnAsAnUnclippedOutline() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 900));
        page = context.newPage();
        page.navigate("http://localhost:" + port + "/");
        page.locator(".module-card").first().focus();
        page.keyboard().press("Tab");
        page.keyboard().press("Shift+Tab");
        org.assertj.core.api.Assertions.assertThat(page.evaluate(
                "() => { const s = getComputedStyle(document.activeElement); return [document.activeElement.className, s.outlineStyle, s.outlineWidth]; }"))
                .isEqualTo(List.of("module-card", "solid", "2px"));

        page.navigate("http://localhost:" + port + "/vocab/library?q=taberu");
        assertThat(page.locator(".word-row")).hasCount(1);
        page.locator(".word-row").focus();
        page.keyboard().press("Shift+Tab");
        page.keyboard().press("Tab");
        org.assertj.core.api.Assertions.assertThat(page.evaluate(
                "() => { const s = getComputedStyle(document.activeElement); return [s.outlineStyle, Number.parseFloat(s.outlineOffset) < 0]; }"))
                .isEqualTo(List.of("solid", true));
    }

    @ParameterizedTest(name = "{0} theme at {1} px")
    @CsvSource({"LIGHT, 375", "LIGHT, 1280", "DARK, 375", "DARK, 1280"})
    void everyScreenPassesAutomatedWcagChecks(ColorScheme scheme, int width) {
        context = browser.newContext(new Browser.NewContextOptions()
                .setColorScheme(scheme).setViewportSize(width, 900));
        page = context.newPage();
        List<String> problems = new ArrayList<>();
        for (Screen screen : screens()) {
            page.navigate("http://localhost:" + port + screen.path());
            assertThat(page.locator(screen.ready()).first()).isVisible();
            screen.prepare().accept(page);
            page.waitForTimeout(250);
            AxeResults results = new AxeBuilder(page).withTags(TAGS).analyze();
            results.getViolations().stream()
                    .filter(rule -> BLOCKING.contains(rule.getImpact()))
                    .forEach(rule -> problems.add("%s: %s (%s) %s".formatted(screen.name(), rule.getId(), rule.getImpact(),
                            rule.getNodes().stream().map(node -> String.valueOf(node.getTarget())).limit(3).toList())));
            org.assertj.core.api.Assertions.assertThat((Boolean) page.evaluate(
                    "() => document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
                    .as("%s has no horizontal scroll at %d px", screen.name(), width).isTrue();
        }
        org.assertj.core.api.Assertions.assertThat(problems).as("serious or critical axe violations").isEmpty();
    }
}
