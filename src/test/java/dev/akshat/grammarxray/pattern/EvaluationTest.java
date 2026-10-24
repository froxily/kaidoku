package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.JapaneseTokenizer;
import dev.akshat.grammarxray.tokenizer.KuromojiTokenizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.ToIntFunction;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationTest {

    private static final double MIN_PRECISION = 0.98;
    private static final double MIN_RECALL = 0.98;
    private static final Path REPORT = Path.of("target/evaluation-report.md");

    private static final RuleRepository rules = RuleRepository.load("classpath:patterns/*.json");
    private static final JapaneseTokenizer tokenizer = new KuromojiTokenizer();
    private static final PatternMatcher matcher = new PatternMatcher(rules.all());

    private static EvaluationSet evaluation;
    private static Map<String, Counts> perRule;
    private static List<String> errors;
    private static int exactSentences;

    record EvaluationSet(List<String> evaluatedRules, List<Case> cases) {
    }

    record Case(String text, List<String> expected) {
    }

    static final class Counts {
        int truePositives;
        int falsePositives;
        int falseNegatives;
    }

    @BeforeAll
    static void evaluate() throws IOException {
        try (InputStream in = EvaluationTest.class.getResourceAsStream("/evaluation/cases.json")) {
            evaluation = JsonMapper.builder().build().readValue(in, EvaluationSet.class);
        }
        Set<String> evaluated = Set.copyOf(evaluation.evaluatedRules());
        perRule = new TreeMap<>();
        evaluated.forEach(id -> perRule.put(id, new Counts()));
        errors = new ArrayList<>();
        exactSentences = 0;
        for (Case testCase : evaluation.cases()) {
            Set<String> expected = Set.copyOf(testCase.expected());
            Set<String> predicted = matcher.match(testCase.text(), tokenizer.tokenize(testCase.text())).stream()
                    .map(match -> match.rule().id())
                    .filter(evaluated::contains)
                    .collect(Collectors.toSet());
            if (expected.equals(predicted)) {
                exactSentences++;
            }
            for (String id : evaluated) {
                Counts counts = perRule.get(id);
                boolean isExpected = expected.contains(id);
                boolean isPredicted = predicted.contains(id);
                if (isExpected && isPredicted) {
                    counts.truePositives++;
                } else if (isPredicted) {
                    counts.falsePositives++;
                    errors.add("FP  " + id + "  " + testCase.text());
                } else if (isExpected) {
                    counts.falseNegatives++;
                    errors.add("FN  " + id + "  " + testCase.text());
                }
            }
        }
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report(), StandardCharsets.UTF_8);
        System.out.println(summary());
    }

    @Test
    void casesAreIndependentOfRuleExamples() {
        Set<String> ruleSentences = rules.all().stream()
                .flatMap(rule -> Stream.concat(rule.examples().stream(), rule.counterExamples().stream()))
                .collect(Collectors.toSet());

        assertThat(evaluation.cases()).extracting(Case::text).doesNotContainAnyElementsOf(ruleSentences).doesNotHaveDuplicates();
    }

    @Test
    void labelsReferToEvaluatedRules() {
        Set<String> known = rules.all().stream().map(Rule::id).collect(Collectors.toSet());

        assertThat(known).containsAll(evaluation.evaluatedRules());
        assertThat(evaluation.cases()).allSatisfy(testCase -> assertThat(evaluation.evaluatedRules()).containsAll(testCase.expected()));
    }

    @Test
    void precisionAndRecallStayAboveTheirFloors() {
        assertThat(precision()).as(summary()).isGreaterThanOrEqualTo(MIN_PRECISION);
        assertThat(recall()).as(summary()).isGreaterThanOrEqualTo(MIN_RECALL);
    }

    private static int total(ToIntFunction<Counts> field) {
        return perRule.values().stream().mapToInt(field).sum();
    }

    private static double precision() {
        int tp = total(c -> c.truePositives);
        return ratio(tp, tp + total(c -> c.falsePositives));
    }

    private static double recall() {
        int tp = total(c -> c.truePositives);
        return ratio(tp, tp + total(c -> c.falseNegatives));
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    private static String summary() {
        int tp = total(c -> c.truePositives);
        int fp = total(c -> c.falsePositives);
        int fn = total(c -> c.falseNegatives);
        double p = precision();
        double r = recall();
        return String.format(Locale.ROOT,
                "Evaluation: %d sentences, %d rules | TP %d, FP %d, FN %d | precision %.3f, recall %.3f, F1 %.3f | exact sentences %d/%d",
                evaluation.cases().size(), perRule.size(), tp, fp, fn, p, r, p + r == 0 ? 0 : 2 * p * r / (p + r),
                exactSentences, evaluation.cases().size());
    }

    private static String report() {
        StringBuilder out = new StringBuilder("# Evaluation report\n\n").append(summary()).append("\n\n")
                .append("| Rule | TP | FP | FN | Precision | Recall |\n|---|---|---|---|---|---|\n");
        perRule.forEach((id, c) -> out.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %.2f | %.2f |%n", id,
                c.truePositives, c.falsePositives, c.falseNegatives,
                ratio(c.truePositives, c.truePositives + c.falsePositives), ratio(c.truePositives, c.truePositives + c.falseNegatives))));
        out.append("\n## Errors\n\n");
        errors.forEach(error -> out.append("- ").append(error).append('\n'));
        return out.toString();
    }
}
