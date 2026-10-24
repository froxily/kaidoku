package dev.akshat.grammarxray.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class TokenControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void returnsTokensWithOffsets() {
        var json = assertThat(mvc.get().uri("/api/tokens").param("text", "日本語を勉強しています。"))
                .hasStatusOk()
                .bodyJson();

        json.extractingPath("$[*].surface").asArray().containsExactly("日本語", "を", "勉強", "し", "て", "い", "ます", "。");
        json.extractingPath("$[*].start").asArray().containsExactly(0, 3, 4, 6, 7, 8, 9, 11);
        json.extractingPath("$[*].end").asArray().containsExactly(3, 4, 6, 7, 8, 9, 11, 12);
        json.extractingPath("$[5].baseForm").isEqualTo("いる");
        json.extractingPath("$[5].pos2").isEqualTo("非自立");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankText(String text) {
        assertProblem(mvc.get().uri("/api/tokens").param("text", text).exchange(), "text must not be blank");
    }

    @Test
    void rejectsMissingText() {
        assertProblem(mvc.get().uri("/api/tokens").exchange(), "Required parameter 'text' is not present.");
    }

    @Test
    void rejectsTextOverLimit() {
        assertProblem(mvc.get().uri("/api/tokens").param("text", "あ".repeat(AnalyzeRequest.MAX_TEXT_LENGTH + 1)).exchange(),
                "text size must be between 0 and 10000");
    }

    private static void assertProblem(MvcTestResult result, String detail) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo(detail);
    }
}
