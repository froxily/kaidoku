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
class AnalyzeControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void returnsTokensAndMatchesWithOffsets() {
        var json = assertThat(analyze(json("高いわけではない。"))).hasStatusOk().bodyJson();

        json.extractingPath("$.tokens[*].surface").asArray().containsExactly("高い", "わけ", "で", "は", "ない", "。");
        json.extractingPath("$.tokens[0].pos").isEqualTo("形容詞");
        json.extractingPath("$.tokens[0].reading").isEqualTo("タカイ");
        json.isLenientlyEqualTo("""
                {"matches": [{"ruleId": "wake-dewa-nai", "pattern": "～わけではない", "level": "N3",
                              "start": 2, "end": 8, "text": "わけではない"}]}""");
        json.extractingPath("$.matches[0].meaning").asString().isNotBlank();
        json.extractingPath("$.matches[0].explanation").asString().isNotBlank();
    }

    @Test
    void dropsSubsumedMatches() {
        assertThat(analyze(json("明日までに出さなければならない。"))).hasStatusOk().bodyJson()
                .extractingPath("$.matches[*].ruleId").asArray().containsExactly("nakereba-naranai");
    }

    @Test
    void reportsAdjacentPatternsInTextOrder() {
        var json = assertThat(analyze(json("書いてしまわなければならない。"))).hasStatusOk().bodyJson();

        json.extractingPath("$.matches[*].ruleId").asArray().containsExactly("te-shimau", "nakereba-naranai");
        json.extractingPath("$.matches[*].text").asArray().containsExactly("てしまわ", "なければならない");
    }

    @Test
    void returnsNoMatchesForTextWithoutKnownPatterns() {
        var json = assertThat(analyze(json("猫が好きです。"))).hasStatusOk().bodyJson();

        json.extractingPath("$.tokens").asArray().isNotEmpty();
        json.extractingPath("$.matches").asArray().isEmpty();
    }

    @Test
    void acceptsTextAtLengthLimit() {
        assertThat(analyze(json("あ".repeat(AnalyzeRequest.MAX_TEXT_LENGTH)))).hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"text\": \"\"}", "{\"text\": \"   \"}", "{\"text\": null}", "{}"})
    void rejectsBlankText(String body) {
        assertProblem(analyze(body), "text must not be blank");
    }

    @Test
    void rejectsTextOverLengthLimit() {
        assertProblem(analyze(json("あ".repeat(AnalyzeRequest.MAX_TEXT_LENGTH + 1))), "text size must be between 0 and 10000");
    }

    @Test
    void acceptsFullyEscapedTextAtLengthLimit() {
        assertThat(analyze(json("\\u3042".repeat(AnalyzeRequest.MAX_TEXT_LENGTH)))).hasStatusOk();
    }

    @Test
    void rejectsBodyOverSizeLimitBeforeParsing() {
        var json = assertThat(analyze(json("a".repeat(RequestBodySizeLimit.MAX_BODY_BYTES))))
                .hasStatus(HttpStatus.CONTENT_TOO_LARGE)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson();
        json.extractingPath("$.status").isEqualTo(413);
        json.extractingPath("$.detail").isEqualTo("Request body must not exceed 65536 bytes");
    }

    @Test
    void rejectsMalformedJson() {
        assertThat(analyze("{\"text\": ")).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private MvcTestResult analyze(String body) {
        return mvc.post().uri("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static String json(String text) {
        return "{\"text\": \"" + text + "\"}";
    }

    private static void assertProblem(MvcTestResult result, String detail) {
        var json = assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON).bodyJson();
        json.extractingPath("$.status").isEqualTo(400);
        json.extractingPath("$.detail").isEqualTo(detail);
    }
}
