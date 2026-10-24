package dev.akshat.grammarxray.pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleRepositoryTest {

    private static final String STEPS = "[{\"surface\": \"a\"}]";
    private static final String EXAMPLES = "\"examples\": [\"a\", \"b\"], \"counterExamples\": [\"c\"]";

    @Test
    void loadsBundledRules() {
        RuleRepository repository = RuleRepository.load("classpath:patterns/*.json");

        assertThat(repository.all()).extracting(Rule::id).contains("te-iru", "nakereba-naranai", "wake-dewa-nai", "ni-yotte");
        assertThat(repository.find("ba-hodo")).get().extracting(Rule::level, Rule::subsumes)
                .containsExactly(Level.N3, List.of("ba-conditional", "nara", "hodo-extent"));
    }

    @Test
    void acceptsSingleValueOrListForConstraints() {
        String steps = "[{\"surface\": \"a\", \"pos\": [\"名詞\", \"動詞\"]}]";

        Rule rule = repository(rule("r", steps, EXAMPLES)).find("r").orElseThrow();

        assertThat(rule.steps().getFirst().surface()).containsExactly("a");
        assertThat(rule.steps().getFirst().pos()).containsExactly("名詞", "動詞");
        assertThat(rule.steps().getFirst().optional()).isFalse();
        assertThat(rule.subsumes()).isEmpty();
    }

    @Test
    void readsBoundedGapSteps() {
        String steps = "[{\"surface\": \"a\"}, {\"gap\": 3}, {\"surface\": \"b\"}]";

        Rule rule = repository(rule("r", steps, EXAMPLES)).find("r").orElseThrow();

        assertThat(rule.steps()).extracting(Step::isGap).containsExactly(false, true, false);
        assertThat(rule.steps().get(1).gap()).isEqualTo(3);
        assertThat(rule.steps().get(1).constraints()).isEmpty();
    }

    @Test
    void readsNotPrecededBySequences() {
        String tail = EXAMPLES + ", \"notPrecededBy\": [[{\"surface\": \"x\"}, {\"baseForm\": \"y\"}], {\"surface\": \"z\"}]";

        Rule rule = repository(rule("r", STEPS, tail)).find("r").orElseThrow();

        assertThat(rule.notPrecededBy()).extracting(List::size).containsExactly(2, 1);
        assertThat(rule.notPrecededBy().getFirst().get(1).baseForm()).containsExactly("y");
    }

    static Stream<Arguments> invalidRules() {
        return Stream.of(
                Arguments.of(rule("Bad_Id", STEPS, EXAMPLES), "id must be kebab-case"),
                Arguments.of(rule("r", STEPS, EXAMPLES) + "," + rule("r", STEPS, EXAMPLES), "duplicate id"),
                Arguments.of(rule("r", "[{\"surfce\": \"a\"}]", EXAMPLES), "surfce"),
                Arguments.of(rule("r", "[]", EXAMPLES), "step sequences must not be empty"),
                Arguments.of(rule("r", "[{}]", EXAMPLES), "exactly one of token constraints, anyOf or gap"),
                Arguments.of(rule("r", "[{\"surface\": \"a\", \"anyOf\": [[{\"surface\": \"b\"}]]}]", EXAMPLES), "exactly one of token constraints, anyOf or gap"),
                Arguments.of(rule("r", "[{\"surface\": []}]", EXAMPLES), "constraint lists must not be empty"),
                Arguments.of(rule("r", "[{\"anyOf\": []}]", EXAMPLES), "anyOf must not be empty"),
                Arguments.of(rule("r", "[{\"surface\": \"a\", \"optional\": true}]", EXAMPLES), "must not start with an optional step"),
                Arguments.of(rule("r", "[{\"anyOf\": [[{\"surface\": \"a\", \"optional\": true}]]}]", EXAMPLES), "must not start with an optional step"),
                Arguments.of(rule("r", STEPS, "\"examples\": [\"a\"], \"counterExamples\": [\"c\"]"), "at least 2 examples"),
                Arguments.of(rule("r", STEPS, "\"examples\": [\"a\", \"b\"], \"counterExamples\": []"), "at least 1 counter-example"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"subsumes\": [\"missing\"]"), "cannot subsume 'missing'"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"subsumes\": [\"r\"]"), "cannot subsume 'r'"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"followedBy\": [{\"anyOf\": [[{\"surface\": \"a\"}]]}]"), "followedBy steps"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"followedBy\": [{\"surface\": \"a\", \"optional\": true}]"), "followedBy steps"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"followedBy\": [{}]"), "followedBy steps"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"followedBy\": [{\"surface\": \"a\", \"gap\": 2}]"), "followedBy steps"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"notPrecededBy\": [[]]"), "notPrecededBy sequences"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"notPrecededBy\": [[{\"surface\": \"a\", \"optional\": true}]]"), "notPrecededBy sequences"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"notPrecededBy\": [[{\"gap\": 1}]]"), "notPrecededBy sequences"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"notPrecededBy\": [[{\"anyOf\": [[{\"surface\": \"a\"}]]}]]"), "notPrecededBy sequences"),
                Arguments.of(rule("r", "[{\"gap\": 2}, {\"surface\": \"a\"}]", EXAMPLES), "must not start or end with a gap"),
                Arguments.of(rule("r", "[{\"surface\": \"a\"}, {\"gap\": 2}]", EXAMPLES), "must not start or end with a gap"),
                Arguments.of(rule("r", "[{\"surface\": \"a\"}, {\"anyOf\": [[{\"surface\": \"b\"}, {\"gap\": 1}]]}, {\"surface\": \"c\"}]", EXAMPLES), "must not start or end with a gap"),
                Arguments.of(rule("r", "[{\"surface\": \"a\"}, {\"gap\": 0}, {\"surface\": \"b\"}]", EXAMPLES), "gap must be between 1 and 8"),
                Arguments.of(rule("r", "[{\"surface\": \"a\"}, {\"gap\": 9}, {\"surface\": \"b\"}]", EXAMPLES), "gap must be between 1 and 8"),
                Arguments.of(rule("r", "[{\"surface\": \"a\"}, {\"gap\": 2, \"optional\": true}, {\"surface\": \"b\"}]", EXAMPLES), "a gap cannot be optional"),
                Arguments.of(rule("r", "[{\"surface\": \"a\"}, {\"gap\": 2, \"surface\": \"x\"}, {\"surface\": \"b\"}]", EXAMPLES), "exactly one of token constraints, anyOf or gap"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidRules")
    void rejectsInvalidRules(String rules, String reason) {
        assertThatThrownBy(() -> repository(rules))
                .isInstanceOf(InvalidRuleException.class)
                .hasMessageContaining(reason);
    }

    @Test
    void failsWhenNoRuleFilesExist() {
        assertThatThrownBy(() -> RuleRepository.load("classpath:no-such-folder/*.json"))
                .isInstanceOf(RuntimeException.class);
    }

    private static RuleRepository repository(String rules) {
        return new RuleRepository(RuleRepository.parse("test.json", "[" + rules + "]"));
    }

    private static String rule(String id, String steps, String tail) {
        return """
                {"id": "%s", "pattern": "～p", "level": "N5", "meaning": "m", "explanation": "e", "steps": %s, %s}"""
                .formatted(id, steps, tail);
    }
}
