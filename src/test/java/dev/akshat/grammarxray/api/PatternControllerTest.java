package dev.akshat.grammarxray.api;

import dev.akshat.grammarxray.pattern.Level;
import dev.akshat.grammarxray.pattern.RuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class PatternControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private RuleRepository rules;

    @Test
    void listsEveryPatternWithoutStepsOrderedByLevel() {
        var json = assertThat(mvc.get().uri("/api/patterns")).hasStatusOk().bodyJson();

        json.extractingPath("$").asArray().hasSize(rules.all().size());
        json.extractingPath("$[*].level").asArray()
                .containsSubsequence("N5", "N4", "N3")
                .isSortedAccordingTo((a, b) -> Level.valueOf((String) a).compareTo(Level.valueOf((String) b)));
        json.doesNotHavePath("$[0].steps");
        json.doesNotHavePath("$[0].counterExamples");
        json.extractingPath("$[0].examples").asArray().isNotEmpty();
    }

    @Test
    void filtersByLevel() {
        long n3 = rules.all().stream().filter(rule -> rule.level() == Level.N3).count();

        var json = assertThat(mvc.get().uri("/api/patterns").param("level", "N3")).hasStatusOk().bodyJson();

        json.extractingPath("$[*].level").asArray().hasSize((int) n3).containsOnly("N3");
        json.extractingPath("$[*].id").asArray().contains("wake-dewa-nai", "ba-hodo");
    }

    @Test
    void rejectsUnknownLevel() {
        assertThat(mvc.get().uri("/api/patterns").param("level", "N9"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void servesStaticPage() {
        assertThat(mvc.get().uri("/index.html")).hasStatusOk().bodyText().contains("Kaidoku");
        assertThat(List.of("/app.js", "/style.css")).allSatisfy(path -> assertThat(mvc.get().uri(path)).hasStatusOk());
    }
}
