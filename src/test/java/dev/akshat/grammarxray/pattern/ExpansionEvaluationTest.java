package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.JapaneseTokenizer;
import dev.akshat.grammarxray.tokenizer.KuromojiTokenizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExpansionEvaluationTest {

    private static final Path REPORT = Path.of("target/expansion-evaluation-report.md");

    private static final RuleRepository rules = RuleRepository.load("classpath:patterns/*.json");
    private static final JapaneseTokenizer tokenizer = new KuromojiTokenizer();
    private static final PatternMatcher matcher = new PatternMatcher(rules.all());

    private static List<EvaluationFile> evaluations;
    private static Result combined;

    record EvaluationSet(List<String> evaluatedRules, List<Case> cases) {
    }

    record Case(String text, List<String> expected) {
    }

    record EvaluationFile(String filename, EvaluationSet evaluation, Result result) {
    }

    static final class Counts {
        int truePositives;
        int falsePositives;
        int falseNegatives;
    }

    static final class Result {
        final Map<String, Counts> perRule = new TreeMap<>();
        final List<String> errors = new ArrayList<>();
        int sentences;
        int exactSentences;
    }

    @BeforeAll
    static void evaluate() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResource("classpath:evaluation/heldout/").exists()
                ? resolver.getResources("classpath:evaluation/heldout/*.json") : new Resource[0];
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        JsonMapper mapper = JsonMapper.builder().build();
        evaluations = new ArrayList<>();
        combined = new Result();
        for (Resource resource : resources) {
            EvaluationSet evaluation;
            try (InputStream in = resource.getInputStream()) {
                evaluation = mapper.readValue(in, EvaluationSet.class);
            }
            Result result = evaluate(evaluation);
            evaluations.add(new EvaluationFile(resource.getFilename(), evaluation, result));
            combined.sentences += result.sentences;
            combined.exactSentences += result.exactSentences;
            result.perRule.forEach((id, counts) -> {
                Counts totals = combined.perRule.computeIfAbsent(id, key -> new Counts());
                totals.truePositives += counts.truePositives;
                totals.falsePositives += counts.falsePositives;
                totals.falseNegatives += counts.falseNegatives;
            });
        }
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report(), StandardCharsets.UTF_8);
        System.out.println(summary(combined));
    }

    @Test
    void casesAreIndependentOfRuleExamples() {
        Set<String> ruleSentences = rules.all().stream()
                .flatMap(rule -> Stream.concat(rule.examples().stream(), rule.counterExamples().stream()))
                .collect(Collectors.toSet());

        assertThat(cases()).extracting(Case::text).doesNotContainAnyElementsOf(ruleSentences).doesNotHaveDuplicates();
    }

    @Test
    void labelsReferToEvaluatedRules() {
        Set<String> known = rules.all().stream().map(Rule::id).collect(Collectors.toSet());

        for (EvaluationFile file : evaluations) {
            assertThat(known).as(file.filename()).containsAll(file.evaluation().evaluatedRules());
            assertThat(file.evaluation().cases()).as(file.filename()).allSatisfy(testCase ->
                    assertThat(file.evaluation().evaluatedRules()).as(testCase.text()).containsAll(testCase.expected()));
        }
    }

    @Test
    void heldOutSetsDoNotOverlapRegressionSet() throws IOException {
        EvaluationSet regression;
        try (InputStream in = ExpansionEvaluationTest.class.getResourceAsStream("/evaluation/cases.json")) {
            regression = JsonMapper.builder().build().readValue(in, EvaluationSet.class);
        }
        Set<String> regressionSentences = regression.cases().stream().map(Case::text).collect(Collectors.toSet());

        assertThat(cases()).extracting(Case::text).doesNotContainAnyElementsOf(regressionSentences);
    }

    private static List<Case> cases() {
        return evaluations.stream().flatMap(file -> file.evaluation().cases().stream()).toList();
    }

    private static Result evaluate(EvaluationSet evaluation) {
        Set<String> evaluated = Set.copyOf(evaluation.evaluatedRules());
        Result result = new Result();
        evaluated.forEach(id -> result.perRule.put(id, new Counts()));
        result.sentences = evaluation.cases().size();
        for (Case testCase : evaluation.cases()) {
            Set<String> expected = Set.copyOf(testCase.expected());
            Set<String> predicted = matcher.match(testCase.text(), tokenizer.tokenize(testCase.text())).stream()
                    .map(match -> match.rule().id())
                    .filter(evaluated::contains)
                    .collect(Collectors.toSet());
            if (expected.equals(predicted)) {
                result.exactSentences++;
            }
            for (String id : evaluated) {
                Counts counts = result.perRule.get(id);
                boolean isExpected = expected.contains(id);
                boolean isPredicted = predicted.contains(id);
                if (isExpected && isPredicted) {
                    counts.truePositives++;
                } else if (isPredicted) {
                    counts.falsePositives++;
                    result.errors.add("FP  " + id + "  " + testCase.text());
                } else if (isExpected) {
                    counts.falseNegatives++;
                    result.errors.add("FN  " + id + "  " + testCase.text());
                }
            }
        }
        return result;
    }

    private static int total(Result result, ToIntFunction<Counts> field) {
        return result.perRule.values().stream().mapToInt(field).sum();
    }

    private static double precision(Result result) {
        int tp = total(result, c -> c.truePositives);
        return ratio(tp, tp + total(result, c -> c.falsePositives));
    }

    private static double recall(Result result) {
        int tp = total(result, c -> c.truePositives);
        return ratio(tp, tp + total(result, c -> c.falseNegatives));
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    private static double f1(double precision, double recall) {
        return precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
    }

    private static String summary(Result result) {
        int tp = total(result, c -> c.truePositives);
        int fp = total(result, c -> c.falsePositives);
        int fn = total(result, c -> c.falseNegatives);
        double p = precision(result);
        double r = recall(result);
        return String.format(Locale.ROOT,
                "Expansion evaluation: %d sentences, %d rules | TP %d, FP %d, FN %d | precision %.3f, recall %.3f, F1 %.3f | exact sentences %d/%d",
                result.sentences, result.perRule.size(), tp, fp, fn, p, r, f1(p, r), result.exactSentences, result.sentences);
    }

    private static String report() {
        StringBuilder out = new StringBuilder("# Expansion evaluation report\n\n").append(summary(combined)).append("\n\n")
                .append(table(combined));
        for (EvaluationFile file : evaluations) {
            out.append("\n## ").append(file.filename()).append("\n\n").append(summary(file.result())).append("\n\n")
                    .append(table(file.result())).append("\n### Errors\n\n");
            file.result().errors.forEach(error -> out.append("- ").append(error).append('\n'));
        }
        return out.toString();
    }

    private static String table(Result result) {
        StringBuilder out = new StringBuilder("| Rule | TP | FP | FN | Precision | Recall | F1 |\n|---|---|---|---|---|---|---|\n");
        result.perRule.forEach((id, c) -> {
            double p = ratio(c.truePositives, c.truePositives + c.falsePositives);
            double r = ratio(c.truePositives, c.truePositives + c.falseNegatives);
            out.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %.2f | %.2f | %.2f |%n", id,
                    c.truePositives, c.falsePositives, c.falseNegatives, p, r, f1(p, r)));
        });
        return out.toString();
    }
}
