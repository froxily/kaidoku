package dev.akshat.grammarxray.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class LearningControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void summarisesTheApprovedDataset() {
        var json = assertThat(mvc.get().uri("/api/learning/summary")).hasStatusOk().bodyJson();

        json.extractingPath("$.kanji.total").isEqualTo(640);
        json.extractingPath("$.kanji.byLevel.N5").isEqualTo(84);
        json.extractingPath("$.vocab.total").isEqualTo(2963);
        json.extractingPath("$.vocab.byLevel.N3").isEqualTo(1659);
        json.extractingPath("$.questions.vocab_reading").isEqualTo(2294);
        json.extractingPath("$.questions.kanji_on_reading").isEqualTo(632);
        json.extractingPath("$.licenseUrl").isEqualTo("https://creativecommons.org/licenses/by-sa/4.0/");
    }

    @Test
    void listsLessonsForOneLevel() {
        var json = assertThat(mvc.get().uri("/api/learning/kanji/lessons?level=N5")).hasStatusOk().bodyJson();

        json.extractingPath("$[*].level").asArray().containsOnly("N5");
        json.extractingPath("$[0].kind").isEqualTo("kanji");
        json.extractingPath("$[0].id").isEqualTo("kanji-n5-1");
        json.extractingPath("$[0].itemIds[0]").isEqualTo("日");
    }

    @Test
    void listsVocabLessonsWithCategories() {
        var json = assertThat(mvc.get().uri("/api/learning/vocab/lessons")).hasStatusOk().bodyJson();

        json.extractingPath("$[0].category").isEqualTo("verbs");
        json.extractingPath("$[*].level").asArray().contains("N5", "N4", "N3");
    }

    @Test
    void rejectsUnknownKindAsProblemDetail() {
        assertThat(mvc.get().uri("/api/learning/grammar/lessons"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void rejectsUnknownLevel() {
        assertThat(mvc.get().uri("/api/learning/kanji/lessons?level=N9")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void forwardsAppRoutesToTheirPages() {
        assertThat(mvc.get().uri("/grammar")).hasForwardedUrl("/grammar.html");
        assertThat(mvc.get().uri("/kanji")).hasForwardedUrl("/learn.html");
        assertThat(mvc.get().uri("/kanji/library")).hasForwardedUrl("/learn.html");
        assertThat(mvc.get().uri("/kanji/{id}", "日")).hasForwardedUrl("/learn.html");
        assertThat(mvc.get().uri("/vocab/lesson/vocab-n5-1")).hasForwardedUrl("/learn.html");
    }

    @Test
    void servesTheReleaseFilesAsStaticData() {
        assertThat(mvc.get().uri("/learning/kanji.json")).hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(mvc.get().uri("/learning/OpenJLPT-LICENSE.txt")).hasStatusOk();
    }
}
