package dev.akshat.grammarxray.pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
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
        assertThat(rule.steps().getFirst().sameAsFirst()).isFalse();
        assertThat(rule.subsumes()).isEmpty();
        assertThat(rule.notFollowedBy()).isEmpty();
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
    void readsNotFollowedBySteps() {
        String tail = EXAMPLES + ", \"notFollowedBy\": [{\"surface\": \"x\"}, {\"baseForm\": \"y\"}]";

        Rule rule = repository(rule("r", STEPS, tail)).find("r").orElseThrow();

        assertThat(rule.notFollowedBy()).hasSize(2);
        assertThat(rule.notFollowedBy().getFirst().surface()).containsExactly("x");
        assertThat(rule.notFollowedBy().get(1).baseForm()).containsExactly("y");
    }

    @Test
    void readsNotPrecededBySequences() {
        String tail = EXAMPLES + ", \"notPrecededBy\": [[{\"surface\": \"x\"}, {\"baseForm\": \"y\"}], {\"surface\": \"z\"}]";

        Rule rule = repository(rule("r", STEPS, tail)).find("r").orElseThrow();

        assertThat(rule.notPrecededBy()).extracting(List::size).containsExactly(2, 1);
        assertThat(rule.notPrecededBy().getFirst().get(1).baseForm()).containsExactly("y");
    }

    @Test
    void readsSuffixAndExclusionConstraints() {
        String steps = """
                [{"pos": "動詞", "baseFormSuffix": ["る", "う"], "surfaceSuffix": ["っ", "ん"], "notBaseForm": ["帰る"]}]
                """;

        Rule rule = repository(rule("r", steps, EXAMPLES)).find("r").orElseThrow();

        assertThat(rule.steps().getFirst().baseFormSuffix()).containsExactly("る", "う");
        assertThat(rule.steps().getFirst().surfaceSuffix()).containsExactly("っ", "ん");
        assertThat(rule.steps().getFirst().notBaseForm()).containsExactly("帰る");
    }

    @Test
    void acceptsExclusionsWithExactConstraintsWithoutPos() {
        String steps = """
                [{"surface": "a", "notBaseForm": ["b"]}]
                """;

        Rule rule = repository(rule("r", steps, EXAMPLES)).find("r").orElseThrow();

        assertThat(rule.steps().getFirst().notBaseForm()).containsExactly("b");
    }

    @Test
    void acceptsExclusionsWithPosAlone() {
        String steps = """
                [{"pos": "動詞", "notBaseForm": ["帰る"]}]
                """;

        Rule rule = repository(rule("r", steps, EXAMPLES)).find("r").orElseThrow();

        assertThat(rule.steps().getFirst().pos()).containsExactly("動詞");
        assertThat(rule.steps().getFirst().notBaseForm()).containsExactly("帰る");
    }

    @Test
    void acceptsSameAsFirstAfterGapsAndInsideAlternatives() {
        String steps = """
                [{"surface": "a"}, {"gap": 2}, {"anyOf": [
                    [{"sameAsFirst": true}],
                    [{"surface": "b"}, {"sameAsFirst": true, "optional": true}]
                ]}, {"sameAsFirst": true}]
                """;

        Rule rule = repository(rule("r", steps, EXAMPLES)).find("r").orElseThrow();

        assertThat(rule.steps().get(2).anyOf().getFirst().getFirst().sameAsFirst()).isTrue();
        assertThat(rule.steps().get(2).anyOf().get(1).get(1).sameAsFirst()).isTrue();
        assertThat(rule.steps().getLast().sameAsFirst()).isTrue();
    }

    @Test
    void acceptsSameAsFirstInNestedAlternativesAfterRootToken() {
        String steps = """
                [{"anyOf": [
                    [{"surface": "a"}, {"anyOf": [[{"anyOf": [[{"sameAsFirst": true}]]}]]}],
                    [{"surface": "b"}, {"surface": "c", "optional": true}, {"sameAsFirst": true}]
                ]}]
                """;

        Rule rule = repository(rule("r", steps, EXAMPLES)).find("r").orElseThrow();
        List<List<Step>> alternatives = rule.steps().getFirst().anyOf();

        assertThat(alternatives.getFirst().get(1).anyOf().getFirst().getFirst()
                .anyOf().getFirst().getFirst().sameAsFirst()).isTrue();
        assertThat(alternatives.get(1).getLast().sameAsFirst()).isTrue();
    }

    static Stream<String> sameAsFirstAtRootStart() {
        return Stream.of(
                """
                [{"anyOf": [[{"surface": "a"}], [{"sameAsFirst": true}]]}]
                """,
                """
                [{"anyOf": [[{"surface": "a"}], [{"anyOf": [[{"anyOf": [
                    [{"surface": "b"}], [{"sameAsFirst": true}]
                ]}]]}]]}]
                """,
                """
                [{"surface": "a", "optional": true}, {"sameAsFirst": true}]
                """,
                """
                [{"surface": "a", "optional": true}, {"surface": "b", "optional": true},
                    {"anyOf": [[{"sameAsFirst": true}]]}]
                """,
                """
                [{"anyOf": [[{"surface": "a"}]], "optional": true}, {"sameAsFirst": true}]
                """,
                """
                [{"anyOf": [[{"anyOf": [[{"surface": "a", "optional": true},
                    {"anyOf": [[{"sameAsFirst": true}]]}]]}]]}]
                """);
    }

    @ParameterizedTest
    @MethodSource("sameAsFirstAtRootStart")
    void rejectsSameAsFirstBeforeRootToken(String steps) {
        assertThatThrownBy(() -> repository(rule("r", steps, EXAMPLES)))
                .isInstanceOf(InvalidRuleException.class)
                .hasMessageContaining("sameAsFirst cannot be used on the first rule step");
    }

    @Test
    void acceptsSuffixAndExclusionConstraintsInAllContexts() {
        String steps = """
                [{"pos": "動詞", "baseFormSuffix": ["る"], "surfaceSuffix": ["っ"], "notBaseForm": ["帰る"]}]
                """.strip();
        String tail = EXAMPLES + ", \"followedBy\": " + steps + ", \"notFollowedBy\": " + steps
                + ", \"notPrecededBy\": [" + steps + "]";

        Rule rule = repository(rule("r", STEPS, tail)).find("r").orElseThrow();

        for (Step step : List.of(rule.followedBy().getFirst(), rule.notFollowedBy().getFirst(), rule.notPrecededBy().getFirst().getFirst())) {
            assertThat(step.baseFormSuffix()).containsExactly("る");
            assertThat(step.surfaceSuffix()).containsExactly("っ");
            assertThat(step.notBaseForm()).containsExactly("帰る");
        }
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
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"notFollowedBy\": [{\"surface\": \"a\", \"optional\": true}]"), "notFollowedBy steps"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"notFollowedBy\": [{\"gap\": 1}]"), "notFollowedBy steps"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"notFollowedBy\": [{\"anyOf\": [[{\"surface\": \"a\"}]]}]"), "notFollowedBy steps"),
                Arguments.of(rule("r", STEPS, EXAMPLES + ", \"notFollowedBy\": [{}]"), "notFollowedBy steps"),
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

    static Stream<Arguments> invalidNewRules() {
        List<Arguments> cases = new ArrayList<>();
        for (String field : List.of("baseFormSuffix", "surfaceSuffix", "notBaseForm")) {
            for (String value : List.of("[]", "[\"\"]", "[\"   \"]", "[null]")) {
                String step = "{\"pos\": \"動詞\", \"%s\": %s}".formatted(field, value);
                cases.add(Arguments.of(rule("r", "[" + step + "]", EXAMPLES),
                        value.equals("[]") ? "constraint lists must not be empty" : "must not contain blank entries"));
                addInvalidContexts(cases, step);
            }
            String constraint = "\"%s\": [\"x\"]".formatted(field);
            String anyOf = "[{\"anyOf\": [[{\"surface\": \"a\"}]], " + constraint + "}]";
            String gap = "[{\"surface\": \"a\"}, {\"gap\": 1, " + constraint + "}, {\"surface\": \"b\"}]";
            cases.add(Arguments.of(rule("r", anyOf, EXAMPLES), "exactly one of token constraints, anyOf or gap"));
            cases.add(Arguments.of(rule("r", gap, EXAMPLES), "exactly one of token constraints, anyOf or gap"));
            if (!field.equals("notBaseForm")) {
                String step = "{\"surface\": \"a\", " + constraint + "}";
                cases.add(Arguments.of(rule("r", "[" + step + "]", EXAMPLES), "suffix constraints require pos"));
                addInvalidContexts(cases, step);
            }
        }
        String exclusionOnly = "{\"notBaseForm\": [\"a\"]}";
        cases.add(Arguments.of(rule("r", "[" + exclusionOnly + "]", EXAMPLES), "token steps need a positive constraint"));
        addInvalidContexts(cases, exclusionOnly);
        cases.add(Arguments.of(rule("r", "[{\"sameAsFirst\": true}]", EXAMPLES), "sameAsFirst cannot be used on the first rule step"));
        cases.add(Arguments.of(rule("r", "[{\"surface\": \"a\", \"sameAsFirst\": true}]", EXAMPLES), "sameAsFirst cannot be used on the first rule step"));
        cases.add(Arguments.of(rule("r", "[{\"surface\": \"a\"}, {\"anyOf\": [[{\"surface\": \"b\"}]], \"sameAsFirst\": true}]", EXAMPLES),
                "exactly one of token constraints, anyOf or gap"));
        cases.add(Arguments.of(rule("r", "[{\"surface\": \"a\"}, {\"gap\": 1, \"sameAsFirst\": true}, {\"surface\": \"b\"}]", EXAMPLES),
                "exactly one of token constraints, anyOf or gap"));
        addInvalidContexts(cases, "{\"surface\": \"a\", \"sameAsFirst\": true}");
        return cases.stream();
    }

    private static void addInvalidContexts(List<Arguments> cases, String step) {
        for (String context : List.of("followedBy", "notFollowedBy", "notPrecededBy")) {
            String steps = context.equals("notPrecededBy") ? "[[" + step + "]]" : "[" + step + "]";
            cases.add(Arguments.of(rule("r", STEPS, EXAMPLES + ", \"" + context + "\": " + steps), context));
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource({"invalidRules", "invalidNewRules"})
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
