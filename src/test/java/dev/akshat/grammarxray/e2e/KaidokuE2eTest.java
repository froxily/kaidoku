package dev.akshat.grammarxray.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
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

import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KaidokuE2eTest {

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
    void openPage() {
        context = browser.newContext();
        page = context.newPage();
        page.navigate("http://localhost:" + port);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void analyzesExampleAndHighlightsPatterns() {
        loadExample();

        assertThat(page.locator("#found button.pattern").first()).isVisible();
        assertThat(recentActionHighlight()).isVisible();
    }

    @Test
    void showsPatternCardForSelectedHighlight() {
        loadExample();
        Locator highlight = recentActionHighlight();
        highlight.click();

        assertThat(page.locator("#card")).isVisible();
        assertThat(page.locator("#card h2")).containsText("～たばかり");
        assertThat(page.locator("#card .meaning")).hasText("just did ~ / only a short time ago");
        assertThat(highlight).hasClass(Pattern.compile(".*\\bselected\\b.*"));
    }

    @Test
    void filtersAndRestoresN3Patterns() {
        loadExample();
        Locator highlights = page.locator("#output mark.lvl-N3");
        Locator entries = page.locator("#found li:has(.badge.N3)");
        Locator checkbox = page.locator("#levels input[value='N3']");
        assertThat(highlights.first()).isVisible();
        assertThat(entries.first()).isVisible();
        int highlightCount = highlights.count();
        int entryCount = entries.count();

        checkbox.uncheck();
        assertThat(highlights).hasCount(0);
        assertThat(entries).hasCount(0);

        checkbox.check();
        assertThat(highlights).hasCount(highlightCount);
        assertThat(entries).hasCount(entryCount);
    }

    @Test
    void displaysOverlappingPatterns() {
        page.locator("#text").fill("彼は東京に住んでいることになっている。");
        page.locator("#analyze").click();

        assertThat(page.locator("#output mark.nested").first()).isVisible();
    }

    @Test
    void hidesAndRestoresPatternsNestedInsideLongerMatches() {
        page.locator("#text").fill("宿題はまだ終わっていない。");
        page.locator("#analyze").click();
        Locator nested = page.locator("#output mark.nested");
        Locator entries = page.locator("#found button.pattern .name");
        Locator toggle = page.locator("#outermost");
        assertThat(nested).hasCount(1);
        assertThat(entries).hasText(new String[] {"まだ～ていない", "～ている"});

        toggle.check();
        assertThat(nested).hasCount(0);
        assertThat(entries).hasText(new String[] {"まだ～ていない"});
        assertThat(page.locator("#output mark")).hasText("まだ終わっていない");

        toggle.uncheck();
        assertThat(nested).hasCount(1);
        assertThat(entries).hasCount(2);
    }

    @Test
    void stacksResultsWithoutHorizontalOverflowOnMobile() {
        page.setViewportSize(375, 812);
        loadExample();
        assertThat(page.locator("#found button.pattern").first()).isVisible();

        org.assertj.core.api.Assertions.assertThat(page.evaluate("""
                () => document.documentElement.scrollWidth <= document.documentElement.clientWidth
                """)).isEqualTo(true);
        var output = page.locator("#output").boundingBox();
        var aside = page.locator(".results aside").boundingBox();
        org.assertj.core.api.Assertions.assertThat(aside.y).isGreaterThanOrEqualTo(output.y + output.height);
    }

    @Test
    void showsBlankTextError() {
        page.locator("#text").fill("");
        page.locator("#analyze").click();

        assertThat(page.locator("#error")).isVisible();
        assertThat(page.locator("#error")).hasText("text must not be blank");
    }

    @Test
    void showsOversizedTextError() {
        page.locator("#text").fill("a".repeat(10001));
        page.locator("#analyze").click();

        assertThat(page.locator("#error")).isVisible();
        assertThat(page.locator("#error")).containsText("size must be between 0 and 10000");
    }

    private void loadExample() {
        page.locator("#sample").click();
        assertThat(page.locator("#output mark").first()).isVisible();
    }

    private Locator recentActionHighlight() {
        return page.locator("#output mark").filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile("^たばかり$")));
    }
}
