package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.JapaneseTokenizer;
import dev.akshat.grammarxray.tokenizer.KuromojiTokenizer;
import dev.akshat.grammarxray.tokenizer.Token;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleConsistencyTest {

    private static final Path SNAPSHOT = Path.of("src/test/resources/crosscheck/rule-examples.snapshot");
    private static final RuleRepository rules = RuleRepository.load("classpath:patterns/*.json");
    private static final JapaneseTokenizer tokenizer = new KuromojiTokenizer();
    private static final PatternMatcher matcher = new PatternMatcher(rules.all());
    private static final List<String> sentences = rules.all().stream()
            .flatMap(rule -> Stream.concat(rule.examples().stream(), rule.counterExamples().stream()))
            .collect(Collectors.toCollection(LinkedHashSet::new))
            .stream().toList();

    @Test
    void everyExampleSentenceMatchesTheReviewedSnapshot() throws IOException {
        List<String> actual = sentences.stream().map(text -> text + "\t" + describe(matcher.match(text, tokenizer.tokenize(text)))).toList();
        if (Boolean.getBoolean("crosscheck.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.write(SNAPSHOT, actual, StandardCharsets.UTF_8);
        }
        List<String> expected = Files.readAllLines(SNAPSHOT, StandardCharsets.UTF_8);

        List<String> changed = new ArrayList<>(actual);
        changed.removeAll(expected);
        List<String> removed = new ArrayList<>(expected);
        removed.removeAll(actual);
        assertThat(changed)
                .as("Cross-rule matches changed. Review, then rerun with -Dcrosscheck.update=true. No longer produced: %s", removed)
                .isEmpty();
        assertThat(removed).as("Snapshot lines no longer produced").isEmpty();
    }

    @Test
    void everyDeclaredSubsumptionSuppressesAMatchInSomeExample() {
        PatternMatcher withoutSubsumption = new PatternMatcher(rules.all().stream()
                .map(rule -> new Rule(rule.id(), rule.pattern(), rule.level(), rule.meaning(), rule.explanation(), List.of(),
                        rule.steps(), rule.followedBy(), rule.notPrecededBy(), rule.examples(), rule.counterExamples()))
                .toList());
        List<List<Match>> raw = sentences.stream().map(text -> withoutSubsumption.match(text, tokenizer.tokenize(text))).toList();

        for (Rule outer : rules.all()) {
            for (String inner : outer.subsumes()) {
                assertThat(raw)
                        .as("'%s' subsumes '%s' but no example sentence exercises it", outer.id(), inner)
                        .anyMatch(matches -> matches.stream().anyMatch(o -> o.rule().id().equals(outer.id())
                                && matches.stream().anyMatch(i -> i.rule().id().equals(inner)
                                && o.start() <= i.start() && i.end() <= o.end())));
            }
        }
    }

    @Test
    void noTwoRulesHighlightExactlyTheSameSpan() {
        for (String text : sentences) {
            List<Token> tokens = tokenizer.tokenize(text);
            List<Match> matches = matcher.match(text, tokens);
            assertThat(matches.stream().map(match -> match.start() + ":" + match.end()))
                    .as("Two rules highlight the same span in %s: %s", text, describe(matches))
                    .doesNotHaveDuplicates();
        }
    }

    private static String describe(List<Match> matches) {
        return matches.stream().map(match -> match.rule().id() + "[" + match.text() + "]").collect(Collectors.joining(", "));
    }
}
