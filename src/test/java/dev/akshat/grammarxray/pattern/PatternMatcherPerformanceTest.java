package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.api.AnalyzeRequest;
import dev.akshat.grammarxray.tokenizer.JapaneseTokenizer;
import dev.akshat.grammarxray.tokenizer.KuromojiTokenizer;
import dev.akshat.grammarxray.tokenizer.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PatternMatcherPerformanceTest {

    private static final RuleRepository rules = RuleRepository.load("classpath:patterns/*.json");
    private static final JapaneseTokenizer tokenizer = new KuromojiTokenizer();
    private static final PatternMatcher matcher = new PatternMatcher(rules.all());

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void analyzesMaximumLengthDenseTextQuickly() {
        String text = denseText();
        for (int run = 0; run < 20; run++) {
            List<Token> tokens = tokenizer.tokenize(text);
            List<Match> matches = matcher.match(text, tokens);

            assertThat(matches).hasSizeGreaterThan(500)
                    .allSatisfy(match -> assertThat(text.substring(match.start(), match.end())).isEqualTo(match.text()));
        }
    }

    private static String denseText() {
        List<String> sentences = rules.all().stream()
                .flatMap(rule -> Stream.concat(rule.examples().stream(), rule.counterExamples().stream()))
                .distinct()
                .toList();
        StringBuilder text = new StringBuilder();
        for (int i = 0; text.length() + sentences.get(i % sentences.size()).length() <= AnalyzeRequest.MAX_TEXT_LENGTH; i++) {
            text.append(sentences.get(i % sentences.size()));
        }
        return text.toString();
    }
}
