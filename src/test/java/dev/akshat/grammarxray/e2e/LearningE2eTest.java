package dev.akshat.grammarxray.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LearningE2eTest {

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
        context.addInitScript("try { sessionStorage.setItem('kaidoku:intro-played', '1') } catch (e) {}");
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

    @ParameterizedTest
    @ValueSource(ints = {360, 1440})
    void dashboardOffersExactlyThreeModulesWithoutOverflow(int width) {
        page.setViewportSize(width, 900);
        open("/");

        assertThat(page.locator(".module-card")).hasCount(3);
        assertThat(page.locator(".module-card h2")).hasText(new String[] {
                "Grammar Pattern Breakdown", "Kanji Learning", "Vocabulary Learning"});
        assertThat(page.locator(".module-card[data-module='grammar']")).hasAttribute("href", "/grammar");
        org.assertj.core.api.Assertions.assertThat(page.evaluate(
                "() => document.documentElement.scrollWidth <= document.documentElement.clientWidth")).isEqualTo(true);
    }

    @Test
    void dashboardLeadsToTheGrammarAnalyzer() {
        open("/");
        page.locator(".module-card[data-module='grammar']").click();

        assertThat(page).hasURL(Pattern.compile(".*/grammar$"));
        assertThat(page.locator("#text")).isVisible();
        assertThat(page.locator("nav.primary-nav a[aria-current='page']")).hasText("Grammar");
    }

    @Test
    void walksThroughTheFirstKanjiLesson() {
        open("/kanji");
        page.locator(".lesson-card").first().click();
        assertThat(page.locator("h1")).hasText("Kanji set 1");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Study cards")).click();
        assertThat(page.locator(".study .glyph-title")).hasText("日");
        assertThat(page).hasURL(Pattern.compile(".*/kanji/lesson/kanji-n5-1\\?card=1$"));

        page.keyboard().press("ArrowRight");
        assertThat(page.locator(".study .glyph-title")).hasText("一");
        assertThat(page.locator(".lesson-counter")).hasText("2 / 10");

        page.reload();
        assertThat(page.locator(".study .glyph-title")).hasText("一");
    }

    @Test
    void finishesALessonAndOffersTheNextOne() {
        open("/vocab/lesson/vocab-n5-1?card=15");
        page.locator("[data-role='next']").click();

        assertThat(page.locator("h1")).hasText("Cards complete");
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Next set")))
                .hasAttribute("href", "/vocab/lesson/vocab-n5-2");
    }

    @Test
    void searchesVocabularyByRomajiAndOpensTheEntry() {
        open("/vocab/library");
        page.locator("#library-search").fill("taberu");
        assertThat(page.locator(".result-count")).hasText("1 word found");

        page.locator(".word-row").first().click();
        assertThat(page.locator("h1.word-title")).containsText("食");
        assertThat(page.locator(".meanings li").first()).hasText("to eat");
        assertThat(page).hasURL(Pattern.compile(".*/vocab/[0-9a-f]{10}$"));
    }

    @Test
    void convertsRomajiToKana() {
        open("/kanji/library");

        Object converted = page.evaluate("""
                async () => {
                  const { romajiToKana } = await import('/js/learn/romaji.js');
                  return ['nichi', 'toukyou', 'tōkyō', 'shinbun', 'shimbun', "kon'ya", 'konya',
                    'kitte', 'matcha', 'jisho', 'fuji', 'hello world', '123', 'kyou', 'zasshi']
                    .map(romajiToKana);
                }
                """);
        org.assertj.core.api.Assertions.assertThat(converted).isEqualTo(java.util.Arrays.asList(
                "にち", "とうきょう", "とうきょう", "しんぶん", "しんぶん", "こんや", "こにゃ",
                "きって", "まっちゃ", "じしょ", "ふじ", null, null, "きょう", "ざっし"));
    }

    @Test
    void searchesKanjiByRomaji() {
        open("/kanji/library");
        page.locator("#library-search").fill("nichi");
        assertThat(page.locator(".tile-glyph").first()).hasText("日");

        page.locator("#library-search").fill("mizu");
        assertThat(page.locator(".tile-glyph").filter(new com.microsoft.playwright.Locator.FilterOptions()
                .setHasText(Pattern.compile("^水$")))).isVisible();
    }

    @Test
    void searchesVocabularyByMacronRomaji() {
        open("/vocab/library");
        page.locator("#library-search").fill("benkyō");
        assertThat(page.locator(".word-row .word-text").first()).containsText("勉強");

        page.locator("#library-search").fill("ōkii");
        assertThat(page.locator(".word-row .word-text").first()).hasText("大きい");
    }

    @Test
    void linksWordsAndKanjiBothWays() {
        open("/vocab/library?q=" + "会う");
        page.locator(".word-row").first().click();
        page.locator(".kanji-card").first().click();

        assertThat(page.locator("h1.glyph-title")).hasText("会");
        assertThat(page.locator("#words-title")).containsText("会");
        assertThat(page.locator(".word-list .hl").first()).hasText("会");
    }

    @Test
    void filtersTheKanjiLibraryByLevel() {
        open("/kanji/library");
        assertThat(page.locator(".result-count")).hasText("640 kanji");

        page.locator("[data-level='N5']").click();
        assertThat(page.locator(".result-count")).hasText("84 kanji");
        assertThat(page).hasURL(Pattern.compile(".*\\?level=N5$"));
    }

    @Test
    void everyItemIsFoundBySearch() {
        open("/kanji/library");
        assertThat(page.locator(".result-count")).hasText("640 kanji");

        Object missing = page.evaluate("""
                async () => {
                  const data = await import('/js/learn/data.js');
                  const kanji = await data.dataset('kanji');
                  const vocab = await data.dataset('vocab');
                  return [
                    ...kanji.items.filter(item => !data.search(kanji.items, item.character).includes(item)).map(item => item.id),
                    ...vocab.items.filter(item => !data.search(vocab.items, item.word).includes(item)
                        || !data.search(vocab.items, item.reading).includes(item)).map(item => item.id)
                  ];
                }
                """);
        org.assertj.core.api.Assertions.assertThat((java.util.List<?>) missing).isEmpty();
    }

    @Test
    void showsNotFoundForUnknownEntries() {
        open("/kanji/" + "鬱");

        assertThat(page.locator("h1")).hasText("Not found");
    }

    @Test
    void remembersTheChosenTheme() {
        open("/");
        page.locator("header [data-theme-toggle]").click();
        assertThat(page.locator("html")).hasAttribute("data-theme", "light");

        page.reload();
        assertThat(page.locator("html")).hasAttribute("data-theme", "light");
    }
}
