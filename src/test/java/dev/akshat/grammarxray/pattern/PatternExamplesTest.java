package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.JapaneseTokenizer;
import dev.akshat.grammarxray.tokenizer.KuromojiTokenizer;
import dev.akshat.grammarxray.tokenizer.Token;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PatternExamplesTest {

    private static final RuleRepository rules = RuleRepository.load("classpath:patterns/*.json");
    private static final JapaneseTokenizer tokenizer = new KuromojiTokenizer();
    private static final PatternMatcher matcher = new PatternMatcher(rules.all());

    static Stream<Arguments> examples() {
        return rules.all().stream().flatMap(rule -> rule.examples().stream().map(text -> Arguments.of(rule.id(), text)));
    }

    static Stream<Arguments> counterExamples() {
        return rules.all().stream().flatMap(rule -> rule.counterExamples().stream().map(text -> Arguments.of(rule.id(), text)));
    }

    @ParameterizedTest(name = "{0} matches {1}")
    @MethodSource("examples")
    void exampleMatches(String ruleId, String text) {
        List<Token> tokens = tokenizer.tokenize(text);
        assertThat(matchedRuleIds(text, tokens)).as(() -> dump(tokens)).contains(ruleId);
    }

    @ParameterizedTest(name = "{0} does not match {1}")
    @MethodSource("counterExamples")
    void counterExampleDoesNotMatch(String ruleId, String text) {
        List<Token> tokens = tokenizer.tokenize(text);
        assertThat(matchedRuleIds(text, tokens)).as(() -> dump(tokens)).doesNotContain(ruleId);
    }

    private static List<String> matchedRuleIds(String text, List<Token> tokens) {
        return matcher.match(text, tokens).stream().map(match -> match.rule().id()).toList();
    }

    private static String dump(List<Token> tokens) {
        return tokens.stream()
                .map(t -> "%s(%s,%s/%s,%s/%s)".formatted(t.surface(), t.baseForm(), t.pos1(), t.pos2(), t.conjType(), t.conjForm()))
                .collect(Collectors.joining(" | "));
    }
}
