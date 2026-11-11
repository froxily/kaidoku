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
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuizE2eTest {

    private static final String WRONG_OPTION = """
            async () => {
              const prompt = document.querySelector('#quiz-prompt').textContent;
              const { questions } = await (await fetch('/learning/questions.json')).json();
              const question = questions.find(q => q.type === 'kanji_on_reading' && q.prompt.character === prompt);
              const options = [...document.querySelectorAll('.option .option-text')].map(o => o.textContent);
              return options.findIndex(option => option !== question.answer) + 1;
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

    @Test
    void setPageOffersCardsReadingQuizAndMeaningCheck() {
        open("/kanji/lesson/kanji-n5-1");

        assertThat(page.locator(".activity .activity-name")).hasText(new String[] {"Study cards", "Reading quiz", "Meaning check"});
        assertThat(page.locator("a.activity").first()).hasAttribute("href", "/kanji/quiz/kanji-n5-1/reading");
        assertThat(page.locator(".activity").nth(1)).containsText("8 questions");
    }

    @Test
    void readingQuizMarksAnswersAndShowsTheCorrectOne() {
        open("/kanji/quiz/kanji-n5-1/reading");
        assertThat(page.locator(".lesson-counter")).hasText("1 / 8");

        page.keyboard().press(String.valueOf(wrongOption()));

        assertThat(page.locator(".option.is-wrong")).hasCount(1);
        assertThat(page.locator(".option.is-correct")).hasCount(1);
        assertThat(page.locator(".feedback-title")).containsText("Not quite. The answer is");
        assertThat(page.locator("[data-role='next']")).isFocused();
        assertThat(page.locator(".option").first()).hasAttribute("aria-disabled", "true");

        page.locator(".option.is-correct").dispatchEvent("click");
        assertThat(page.locator(".option.is-wrong")).hasCount(1);
    }

    @Test
    void finishesWithResultsMistakeReviewAndRetry() {
        open("/kanji/quiz/kanji-n5-1/reading");
        for (int i = 0; i < 8; i++) {
            page.keyboard().press(String.valueOf(wrongOption()));
            page.keyboard().press("ArrowRight");
        }

        assertThat(page.locator("h1")).hasText("Results");
        assertThat(page.locator(".score")).hasText("0 / 8");
        assertThat(page.locator("section:has(#missed-title) .result-row")).hasCount(8);
        assertThat(page.locator(".result-row .your-answer del").first()).isVisible();

        page.getByText("Retry 8 missed").click();
        assertThat(page.locator(".lesson-counter")).hasText("1 / 8");
        assertThat(page.locator("#quiz-prompt")).isFocused();
    }

    @Test
    void correctAnswersCountTowardTheScore() {
        open("/kanji/quiz/kanji-n5-1/reading");
        for (int i = 0; i < 8; i++) {
            page.locator(".option-text").filter(new com.microsoft.playwright.Locator.FilterOptions()
                    .setHasText(Pattern.compile("^" + correctAnswer() + "$"))).click();
            assertThat(page.locator(".feedback.is-correct")).isVisible();
            page.locator("[data-role='next']").click();
        }

        assertThat(page.locator(".score")).hasText("8 / 8");
        assertThat(page.locator(".score-note")).containsText("nothing to review");
        assertThat(page.locator("#missed-title")).hasCount(0);
    }

    @Test
    void meaningCheckIsSelfGraded() {
        open("/vocab/quiz/vocab-n5-1/meaning");
        int total = Integer.parseInt(page.locator(".lesson-counter").textContent().split("/")[1].trim());
        assertThat(page.locator(".feedback")).hasCount(0);

        page.locator("#recall").fill("to meet");
        page.keyboard().press("Enter");
        assertThat(page.locator(".feedback .summary-meaning")).isVisible();
        assertThat(page.locator(".option")).hasCount(0);
        page.keyboard().press("2");
        for (int i = 1; i < total; i++) {
            page.locator("#recall").fill("my guess");
            page.locator("[data-role='check']").click();
            page.locator("[data-role='known']").click();
        }

        assertThat(page.locator(".score")).hasText((total - 1) + " / " + total);
        assertThat(page.locator("#missed-title")).hasText("Mistakes to review");
        assertThat(page.locator("section:has(#missed-title) .result-row")).hasCount(1);
        assertThat(page.locator("section:has(#missed-title) .result-row q")).hasText("to meet");
    }

    @Test
    void levelQuizSamplesTwentyQuestions() {
        open("/vocab?level=N4");
        page.locator(".level-quiz a", new Page.LocatorOptions().setHasText("Reading quiz")).click();

        assertThat(page).hasURL(Pattern.compile(".*/vocab/quiz/n4/reading$"));
        assertThat(page.locator(".lesson-counter")).hasText("1 / 20");
    }

    @Test
    void unknownQuizModeIsNotFound() {
        open("/kanji/quiz/kanji-n5-1/writing");

        assertThat(page.locator("h1")).hasText("Not found");
    }

    @Test
    void quizEngineServesEveryApprovedQuestionAndRecomputesAnswers() {
        open("/kanji");
        assertThat(page.locator(".lesson-card").first()).isVisible();

        Object result = page.evaluate("""
                async () => {
                  const data = await import('/js/learn/data.js');
                  const quiz = await import('/js/learn/quiz.js');
                  const counts = [];
                  for (const kind of ['kanji', 'vocab']) {
                    const { items } = await data.dataset(kind);
                    counts.push((await quiz.readingQuestions(kind, items)).size);
                  }
                  const { items } = await data.dataset('vocab');
                  const served = await quiz.readingQuestions('vocab', items);
                  let recomputed = 0;
                  for (const question of served.values()) {
                    const prepared = quiz.prepare(question);
                    if (prepared.options[prepared.answer_index] === question.answer
                        && quiz.isCorrect(prepared, prepared.answer_index)
                        && !quiz.isCorrect(prepared, (prepared.answer_index + 1) % prepared.options.length)) {
                      recomputed++;
                    }
                  }
                  const [id, question] = served.entries().next().value;
                  const item = items.find(each => each.id === id);
                  const rejected = [
                    { ...question, verified: false },
                    { ...question, answer: 'ー' },
                    { ...question, options: [...question.options.slice(0, -1), question.options[0]] },
                    { ...question, prompt: { word: '別' } }
                  ].filter(bad => !quiz.isServable('vocab', bad, item)).length;
                  const homograph = { ...item, other_readings: question.options.filter(o => o !== question.answer).slice(0, 1) };
                  return [...counts, recomputed, rejected, quiz.isServable('vocab', question, homograph) ? 1 : 0];
                }
                """);
        org.assertj.core.api.Assertions.assertThat(((List<?>) result).stream().map(value -> ((Number) value).intValue()).toList())
                .containsExactly(632, 2294, 2294, 4, 0);
    }

    private void awaitQuestion() {
        assertThat(page.locator(".option:not([aria-disabled])").first()).isVisible();
    }

    private int wrongOption() {
        awaitQuestion();
        return ((Number) page.evaluate(WRONG_OPTION)).intValue();
    }

    private String correctAnswer() {
        awaitQuestion();
        return (String) page.evaluate("""
                async () => {
                  const prompt = document.querySelector('#quiz-prompt').textContent;
                  const { questions } = await (await fetch('/learning/questions.json')).json();
                  return questions.find(q => q.type === 'kanji_on_reading' && q.prompt.character === prompt).answer;
                }
                """);
    }
}
