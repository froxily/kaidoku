package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.Token;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class PatternMatcherTest {

    @Test
    void optionalStepCanBeSkippedOrConsumed() {
        List<Rule> rules = List.of(rule("r", surface("a"), optional(surface("b")), surface("c")));

        assertThat(texts(rules, "a", "c")).containsExactly("ac");
        assertThat(texts(rules, "a", "b", "c")).containsExactly("abc");
        assertThat(texts(rules, "a", "x", "c")).isEmpty();
    }

    @Test
    void optionalLeadingStepsCanBeSkippedOrConsumed() {
        List<Rule> rules = List.of(rule("r", optional(surface("a")), optional(surface("b")), surface("c")));

        assertThat(texts(rules, "x", "a", "b", "c")).containsExactly("abc");
        assertThat(texts(rules, "x", "a", "c")).containsExactly("ac");
        assertThat(texts(rules, "x", "b", "c")).containsExactly("bc");
        assertThat(texts(rules, "x", "c")).containsExactly("c");
        assertThat(texts(rules, "x", "a", "b")).isEmpty();
    }

    @Test
    void optionalLeadingAnyOfCanBeSkippedOrConsumed() {
        List<Rule> rules = List.of(rule("r", optional(anyOf(List.of(surface("a")), List.of(surface("b")))), surface("c")));

        assertThat(texts(rules, "x", "a", "c")).containsExactly("ac");
        assertThat(texts(rules, "x", "b", "c")).containsExactly("bc");
        assertThat(texts(rules, "x", "c")).containsExactly("c");
    }

    @Test
    void nestedLeadingAnyOfChecksAllStarterConstraints() {
        List<Rule> rules = List.of(rule("r", anyOf(
                List.of(anyOf(List.of(suffixes(List.of("る"), List.of("っ"), List.of("帰る"))), List.of(baseForm("なる")))),
                List.of(surface("a"))), surface("た")));

        assertThat(texts(rules, "x", "戻っ/戻る/動詞", "た")).containsExactly("戻った");
        assertThat(texts(rules, "x", "なっ/なる", "た")).containsExactly("なった");
        assertThat(texts(rules, "x", "a", "た")).containsExactly("aた");
        assertThat(texts(rules, "帰っ/帰る/動詞", "た")).isEmpty();
        assertThat(texts(rules, "戻り/戻る/動詞", "た")).isEmpty();
        assertThat(texts(rules, "戻っ/戻る/名詞", "た")).isEmpty();
    }

    @Test
    void leadingAnyOfAlternativeCanSkipItsOptionalFirstStep() {
        List<Rule> rules = List.of(rule("r", anyOf(
                List.of(optional(surface("a")), surface("b")),
                List.of(surface("x"))), surface("c")));

        assertThat(texts(rules, "y", "a", "b", "c")).containsExactly("abc");
        assertThat(texts(rules, "y", "b", "c")).containsExactly("bc");
        assertThat(texts(rules, "y", "x", "c")).containsExactly("xc");
        assertThat(texts(rules, "y", "c")).isEmpty();
    }

    @Test
    void nullableLeadingAnyOfContinuesIntoTheContainingSequence() {
        for (Step alternative : List.of(surface("b"), optional(surface("b")))) {
            List<Rule> rules = List.of(rule("r", anyOf(
                    List.of(anyOf(List.of(optional(surface("a"))))),
                    List.of(alternative)), optional(surface("c")), surface("d")));

            assertThat(texts(rules, "x", "a", "c", "d")).containsExactly("acd");
            assertThat(texts(rules, "x", "b", "d")).containsExactly("bd");
            assertThat(texts(rules, "x", "c", "d")).containsExactly("cd");
            assertThat(texts(rules, "x", "d")).containsExactly("d");
        }
    }

    @Test
    void anchorsOnPos2WithoutRequiringPos1() {
        List<Rule> rules = List.of(rule("r", pos2("数", "numeric"), surface("に")));

        assertThat(texts(rules, "x", "二/二/名詞/*/*/数", "に")).containsExactly("二に");
        assertThat(texts(rules, "x", "2/2/動詞/*/*/numeric", "に")).containsExactly("2に");
        assertThat(texts(rules, "x", "二/二/名詞/*/*/一般", "に")).isEmpty();
    }

    @Test
    void deduplicatesPos2AndOtherLeadingAnchors() {
        List<Rule> rules = List.of(rule("r", anyOf(
                List.of(surface("a")), List.of(baseForm("a")), List.of(pos("名詞")), List.of(pos2("数")))));

        assertThat(texts(rules, "a/a/名詞/*/*/数")).containsExactly("a");
    }

    @Test
    void nullableRulesKeepTheirNonEmptyMatches() {
        List<Rule> rules = List.of(rule("r", optional(surface("a")), optional(surface("b"))));

        assertThat(texts(rules, "x", "a", "b")).containsExactly("ab");
        assertThat(texts(rules, "x", "b")).containsExactly("b");
        assertThat(texts(rules, "x")).isEmpty();
        assertThat(texts(rules)).isEmpty();
    }

    @Test
    void unknownStartersKeepMatchesAtUnanchoredTokens() {
        for (List<Step> sequence : List.of(
                List.of(gap(1), surface("c")),
                List.of(optional(surface("a")), gap(1), surface("c")),
                List.of(anyOf(List.of(surface("a")), List.of(gap(1), surface("c")))),
                List.of(sameAsFirst(), surface("c")))) {
            List<Rule> rules = List.of(rule("r", sequence.toArray(Step[]::new)));

            assertThat(texts(rules, "x/known", "c")).containsExactly("xc");
        }
    }

    @Test
    void requiredLiteralsInsideNestedAnyOfPreserveAllBranches() {
        List<Rule> rules = List.of(rule("r", pos("名詞"), anyOf(
                List.of(pos("名詞"), anyOf(List.of(surface("上")), List.of(baseForm("見える")))),
                List.of(pos("名詞"), baseForm("当たる")))));

        assertThat(texts(rules, "x/x/助詞", "a", "b", "上")).containsExactly("ab上");
        assertThat(texts(rules, "x/x/助詞", "a", "b", "見え/見える/動詞")).containsExactly("ab見え");
        assertThat(texts(rules, "x/x/助詞", "a", "b", "当たっ/当たる/動詞")).containsExactly("ab当たっ");
        assertThat(texts(rules, "a", "b", "c")).isEmpty();
    }

    @Test
    void anAlternativeWithoutALiteralDisablesTheLiteralRequirement() {
        List<Rule> rules = List.of(rule("r", pos("名詞"), anyOf(
                List.of(surface("上")),
                List.of(anyOf(List.of(baseForm("見える")), List.of(pos("動詞")))))));

        assertThat(texts(rules, "a", "走っ/走る/動詞")).containsExactly("a走っ");
        assertThat(texts(rules, "a", "見え/見える/動詞")).containsExactly("a見え");
        assertThat(texts(rules, "a", "上")).containsExactly("a上");
    }

    @Test
    void optionalLiteralStepsNeverSupplyARequirement() {
        for (Step step : List.of(optional(surface("上")), optional(anyOf(
                List.of(surface("上")), List.of(baseForm("当たる")))))) {
            List<Rule> rules = List.of(rule("r", pos("名詞"), step, pos("動詞")));

            assertThat(texts(rules, "a", "走っ/走る/動詞")).containsExactly("a走っ");
            assertThat(texts(rules, "a", "上/上/助詞", "走っ/走る/動詞")).containsExactly("a上走っ");
        }
    }

    @Test
    void literalRequirementsIgnoreContextSteps() {
        List<Rule> rules = List.of(
                followedBy(surface("上"), "followed", pos("名詞"), pos("動詞")),
                notFollowedBy(List.of(surface("上")), "not-followed", pos("名詞"), pos("動詞")),
                notPrecededBy(List.of(surface("上")), "not-preceded", pos("名詞"), pos("動詞")));
        for (Rule rule : rules) {
            assertThat(texts(List.of(rule), "a", "走っ/走る/動詞")).containsExactly("a走っ");
        }
        assertThat(texts(List.of(rules.getFirst()), "a", "走っ/走る/動詞", "上/上/助詞")).containsExactly("a走っ");
    }

    @Test
    void literalWindowsIncludeMaximumGapAndOptionalAlternativeSpans() {
        for (Rule rule : List.of(
                rule("r", pos("名詞"), gap(2), surface("上")),
                rule("r", pos("名詞"), optional(anyOf(List.of(pos("動詞")), List.of(pos("動詞"), pos("動詞")))), surface("上")))) {
            List<Rule> rules = List.of(rule);

            assertThat(texts(rules, "a", "x/x/動詞", "y/y/動詞", "上/上/助詞")).containsExactly("axy上");
            assertThat(texts(rules, "a", "x/x/動詞", "y/y/動詞", "z/z/動詞", "上/上/助詞")).isEmpty();
            assertThat(texts(rules, "a", "上/上/助詞", "x/x/動詞", "y/y/動詞", "上/上/助詞")).containsExactly("a上");
            assertThat(texts(rules, "a", "上/上/助詞", "x/x/動詞", "b", "上/上/助詞")).containsExactly("a上", "b上");
        }
    }

    @Test
    void sharedLiteralRequirementsKeepEachRulesWindow() {
        List<Rule> rules = List.of(
                rule("short", pos("名詞"), gap(1), surface("上")),
                rule("long", pos("名詞"), gap(2), surface("上")));

        assertThat(match(rules, "a", "x/x/動詞", "y/y/動詞", "上/上/助詞"))
                .extracting(m -> m.rule().id(), Match::text).containsExactly(tuple("long", "axy上"));
    }

    @Test
    void prefilterResultsEqualRulesTriedAtEveryToken() {
        Step conjugation = new Step(null, null, null, null, null, List.of("*"), null, null, null, false, false, null, null);
        List<Rule> rules = List.of(
                rule("optional", optional(surface("a")), optional(surface("b")), surface("c")),
                rule("optional-anyof", optional(anyOf(List.of(surface("a")), List.of(pos2("数")))), surface("c")),
                rule("nested", anyOf(List.of(anyOf(List.of(pos2("数")), List.of(baseForm("v"))))), surface("c")),
                rule("optional-alternative", anyOf(List.of(optional(surface("a")), surface("b")), List.of(pos2("数"))), surface("c")),
                rule("nullable-alternative", anyOf(List.of(optional(surface("a"))), List.of(surface("b"))), surface("c")),
                rule("nullable-rule", optional(surface("a"))),
                rule("nullable-nested", anyOf(List.of(anyOf(List.of(optional(surface("a"))))), List.of(optional(surface("b"))))),
                rule("suffix", suffixes(List.of("る"), List.of("っ"), List.of("帰る")), surface("c")),
                rule("unanchored", conjugation, surface("c")),
                rule("required-nested", pos("名詞"), anyOf(
                        List.of(pos("助詞"), anyOf(List.of(surface("上")), List.of(baseForm("見える")))),
                        List.of(baseForm("当たる")))),
                rule("optional-literal", pos("名詞"), optional(surface("上")), pos("動詞")),
                rule("literal-free-alternative", pos("名詞"), anyOf(List.of(surface("上")), List.of(pos("動詞")))),
                rule("bounded-literal", pos("名詞"), gap(2), surface("上")),
                rule("gap", surface("a"), gap(2), sameAsFirst()),
                rule("unknown", anyOf(List.of(surface("a")), List.of(gap(1), surface("c")))),
                rule("outer", List.of("optional"), surface("a"), surface("b"), surface("c")),
                followedBy(surface("!"), "followed", pos("名詞"), surface("c")),
                notFollowedBy(List.of(surface("!")), "not-followed", surface("b"), surface("c")),
                notPrecededBy(List.of(surface("x")), "not-preceded", surface("c")));
        PatternMatcher indexed = new PatternMatcher(rules);
        PatternMatcher everywhere = new PatternMatcher(rules.stream().map(PatternMatcherTest::triedEverywhere).toList());
        List<List<String>> inputs = new ArrayList<>(List.of(
                List.<String>of(),
                List.of("x", "a", "b", "c", "!", "c", "a", "c", "b", "c"),
                List.of("x", "二/二/名詞/*/*/数", "c", "!", "V/v/動詞", "c", "二/二/名詞/*/*/一般", "c"),
                List.of("戻っ/戻る/動詞", "c", "帰っ/帰る/動詞", "c", "戻り/戻る/動詞", "c"),
                List.of("prefix/other", "a/shared", "x/other", "b/shared", "c", "a/*", "b/*"),
                List.of("戻っ/戻る/動詞/連用タ接続", "c/c/助動詞/基本形/特殊・タ", "。"),
                List.of("空", "が/が/助詞", "見え/見える/動詞", "。/。/記号/*/*/句点"),
                List.of("役", "当たっ/当たる/動詞", "空", "が/が/助詞", "上"),
                List.of("犬", "走っ/走る/動詞", "猫", "上/上/助詞", "歩く/歩く/動詞"),
                List.of("a", "x/x/動詞", "y/y/動詞", "上/上/助詞", "b", "上/上/助詞"),
                List.of("上/上/助詞", "a", "x/x/動詞", "y/y/動詞", "z/z/動詞", "上/上/助詞")));
        List<String> alphabet = List.of("a", "b", "c", "x");
        int combinations = 1;
        for (int length = 1; length <= 4; length++) {
            combinations *= alphabet.size();
            for (int encoded = 0; encoded < combinations; encoded++) {
                List<String> specs = new ArrayList<>();
                int remaining = encoded;
                for (int index = 0; index < length; index++) {
                    specs.add(alphabet.get(remaining % alphabet.size()));
                    remaining /= alphabet.size();
                }
                inputs.add(specs);
            }
        }

        for (List<String> specs : inputs) {
            List<Token> tokens = tokens(specs.toArray(String[]::new));
            String text = tokens.stream().map(Token::surface).collect(Collectors.joining());
            assertThat(indexed.match(text, tokens)).as("tokens: %s", specs)
                    .extracting(m -> m.rule().id(), Match::start, Match::end, Match::text)
                    .containsExactlyElementsOf(everywhere.match(text, tokens).stream()
                            .map(m -> tuple(m.rule().id(), m.start(), m.end(), m.text())).toList());
        }
    }

    @Test
    void anyOfAlternativesMayDifferInLength() {
        List<Rule> rules = List.of(rule("r",
                surface("a"),
                anyOf(List.of(surface("b"), surface("c")), List.of(surface("d"))),
                surface("e")));

        assertThat(texts(rules, "a", "b", "c", "e")).containsExactly("abce");
        assertThat(texts(rules, "a", "d", "e")).containsExactly("ade");
        assertThat(texts(rules, "a", "b", "e")).isEmpty();
    }

    @Test
    void prefersLongestMatch() {
        List<Rule> alternatives = List.of(rule("r", surface("a"), anyOf(List.of(surface("b")), List.of(surface("b"), surface("c")))));
        List<Rule> trailingOptional = List.of(rule("r", surface("a"), optional(surface("b"))));

        assertThat(texts(alternatives, "a", "b", "c")).containsExactly("abc");
        assertThat(texts(trailingOptional, "a", "b")).containsExactly("ab");
    }

    @Test
    void supportsNestedAnyOfAsFirstStep() {
        List<Rule> rules = List.of(rule("r", anyOf(
                List.of(surface("a"), anyOf(List.of(surface("b")), List.of(surface("c"), surface("d")))),
                List.of(surface("x")))));

        assertThat(texts(rules, "a", "c", "d")).containsExactly("acd");
        assertThat(texts(rules, "a", "b")).containsExactly("ab");
        assertThat(texts(rules, "y", "x")).containsExactly("x");
        assertThat(texts(rules, "a", "c")).isEmpty();
    }

    @Test
    void anchorsOnBaseFormForInflectedTokens() {
        List<Rule> rules = List.of(rule("r", baseForm("なる"), surface("た")));

        assertThat(texts(rules, "なっ/なる", "た")).containsExactly("なった");
    }

    @Test
    void triesRulesWithoutLiteralAnchorAtEveryToken() {
        List<Rule> rules = List.of(rule("r", pos("動詞"), surface("た")));

        assertThat(texts(rules, "x", "行っ/行く/動詞", "た")).containsExactly("行った");
    }

    @Test
    void triesRulesWithoutAnyIndexableAnchorAtEveryToken() {
        Step anyConjugation = new Step(null, null, null, null, null, List.of("*"), null, null, null, false, false, null, null);
        List<Rule> rules = List.of(rule("r", anyConjugation, surface("た")));

        assertThat(texts(rules, "x", "y", "た")).containsExactly("yた");
    }

    @Test
    void triesEachCandidateRuleOncePerToken() {
        List<Rule> rules = List.of(rule("r", anyOf(List.of(surface("a")), List.of(baseForm("a")), List.of(pos("名詞")))));

        assertThat(texts(rules, "a")).containsExactly("a");
    }

    @Test
    void longValueListsPreserveMatchingAndListSemantics() {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            values.add("value" + index + "x");
        }
        values.add(values.getLast());
        List<String> exclusions = new ArrayList<>(values.subList(0, 255));
        List<String> endings = List.of("x");
        Step step = new Step(values, values, values, values, values, values, endings, endings, exclusions,
                false, false, null, null);
        String accepted = values.getLast();
        Token matching = new Token(accepted, accepted, accepted, accepted, "*", accepted, accepted, "*", 0, accepted.length());
        Token excluded = new Token(accepted, exclusions.getLast(), accepted, accepted, "*", accepted, accepted, "*", 0, accepted.length());

        assertThat(step.matches(matching)).isTrue();
        assertThat(step.matches(excluded)).isFalse();
        for (int index = 0; index < 6; index++) {
            String[] fields = {accepted, accepted, accepted, accepted, accepted, accepted};
            fields[index] = "missing";
            Token mismatch = new Token(fields[0], fields[1], fields[2], fields[3], "*", fields[4], fields[5], "*", 0, accepted.length());
            assertThat(step.matches(mismatch)).as("constraint %s", index).isFalse();
        }
        assertThat(step.surface()).isEqualTo(values).hasSameHashCodeAs(values).hasToString(values.toString());
        assertThat(values).isEqualTo(step.surface());
        assertThat(step.notBaseForm()).containsExactlyElementsOf(exclusions);
        assertThat(step.baseFormSuffix()).isSameAs(endings);
        assertThat(step.surfaceSuffix()).isSameAs(endings);
        assertThatThrownBy(() -> step.surface().set(0, "changed")).isInstanceOf(UnsupportedOperationException.class);

        values.clear();
        exclusions.clear();
        assertThat(step.matches(matching)).isTrue();
        assertThat(step.matches(excluded)).isFalse();
    }

    @Test
    void valueLookupsHandleNullTokenFields() {
        Token token = new Token(null, null, null, null, null, null, null, null, 0, 0);
        assertThat(surface("known").baseForm()).isNull();
        for (boolean includesNull : List.of(false, true)) {
            List<String> values = includesNull ? Arrays.asList("known", null) : List.of("known");
            for (int index = 0; index < 7; index++) {
                List<List<String>> constraints = new ArrayList<>(Collections.nCopies(7, null));
                constraints.set(index, values);
                Step step = new Step(constraints.get(0), constraints.get(1), constraints.get(2), constraints.get(3),
                        constraints.get(4), constraints.get(5), null, null, constraints.get(6), false, false, null, null);

                assertThat(step.constraints()).containsExactly(values);
                assertThat(step.matches(token)).as("constraint %s with null=%s", index, includesNull)
                        .isEqualTo(includesNull != (index == 6));
            }
        }
    }

    @Test
    void baseFormSuffixMatchesAnyListedEnding() {
        List<Rule> rules = List.of(rule("r", suffixes(List.of("がる", "たい"), null, null)));

        assertThat(texts(rules, "怖がっ/怖がる/動詞")).containsExactly("怖がっ");
        assertThat(texts(rules, "読みたい/読みたい/動詞")).containsExactly("読みたい");
        assertThat(texts(rules, "がる/がる/動詞")).containsExactly("がる");
        assertThat(texts(rules, "がる人/がる人/動詞")).isEmpty();
        assertThat(texts(rules, "読ん/読む/動詞")).isEmpty();
    }

    @Test
    void surfaceSuffixMatchesAnyListedEnding() {
        List<Rule> rules = List.of(rule("r", suffixes(null, List.of("っ", "ん"), null)));

        assertThat(texts(rules, "帰っ/帰る/動詞")).containsExactly("帰っ");
        assertThat(texts(rules, "読ん/読む/動詞")).containsExactly("読ん");
        assertThat(texts(rules, "っぽい/っぽい/動詞")).isEmpty();
        assertThat(texts(rules, "帰り/帰る/動詞")).isEmpty();
    }

    @Test
    void suffixAndExactConstraintsAllApply() {
        Step step = new Step(List.of("cutting", "cut"), List.of("cut", "put", "cutting"), List.of("動詞"), null,
                null, null, List.of("ut"), List.of("ing"), null, false, false, null, null);
        List<Rule> rules = List.of(rule("r", step));

        assertThat(texts(rules, "cutting/cut/動詞")).containsExactly("cutting");
        assertThat(texts(rules, "cutting/put/動詞")).containsExactly("cutting");
        assertThat(texts(rules, "putting/cut/動詞")).isEmpty();
        assertThat(texts(rules, "cutting/shut/動詞")).isEmpty();
        assertThat(texts(rules, "cut/cut/動詞")).isEmpty();
        assertThat(texts(rules, "cutting/cutting/動詞")).isEmpty();
        assertThat(texts(rules, "cutting/cut/名詞")).isEmpty();
    }

    @Test
    void baseFormExclusionsOverrideSuffixAndExactMatches() {
        Step step = new Step(null, List.of("帰る", "走る", "戻る", "持ち帰る"), List.of("動詞"), null, null, null,
                List.of("る"), List.of("っ"), List.of("帰る", "走る"), false, false, null, null);
        List<Rule> rules = List.of(rule("r", step));

        assertThat(texts(rules, "帰っ/帰る/動詞")).isEmpty();
        assertThat(texts(rules, "走っ/走る/動詞")).isEmpty();
        assertThat(texts(rules, "戻っ/戻る/動詞")).containsExactly("戻っ");
        assertThat(texts(rules, "持ち帰っ/持ち帰る/動詞")).containsExactly("持ち帰っ");
    }

    @Test
    void posAndExclusionsMatchWithoutOtherPositiveConstraints() {
        List<Rule> rules = List.of(rule("r", suffixes(null, null, List.of("帰る"))));

        assertThat(texts(rules, "前", "戻っ/戻る/動詞")).containsExactly("戻っ");
        assertThat(texts(rules, "帰っ/帰る/動詞")).isEmpty();
        assertThat(texts(rules, "戻っ/戻る/名詞")).isEmpty();
    }

    @Test
    void firstSuffixStepIsFoundThroughItsPosAnchor() {
        List<Rule> rules = List.of(rule("r", suffixes(List.of("る"), List.of("っ"), null), surface("た")));

        assertThat(match(rules, "前", "帰っ/帰る/動詞", "た"))
                .extracting(Match::start, Match::end, Match::text)
                .containsExactly(tuple(1, 4, "帰った"));
        assertThat(texts(rules, "帰っ/帰る/名詞", "た")).isEmpty();
    }

    @Test
    void suffixStepsInsideFirstAnyOfAreFoundThroughTheirPosAnchors() {
        List<Rule> rules = List.of(rule("r", anyOf(
                List.of(suffixes(List.of("る"), null, null)),
                List.of(suffixes(null, List.of("ん"), null)))));

        assertThat(texts(rules, "前", "帰っ/帰る/動詞")).containsExactly("帰っ");
        assertThat(texts(rules, "前", "読ん/読む/動詞")).containsExactly("読ん");
    }

    @Test
    void sameAsFirstMatchesBaseFormsAcrossAGap() {
        List<Rule> rules = List.of(rule("r", surface("a"), gap(2), sameAsFirst()));

        assertThat(texts(rules, "a/shared", "b/shared")).containsExactly("ab");
        assertThat(texts(rules, "a/shared", "x/other", "b/shared", "c/shared")).containsExactly("axb");
        assertThat(texts(rules, "a/shared", "x/other", "b/different")).isEmpty();
    }

    @Test
    void sameAsFirstInsideAnyOfUsesTheRootStart() {
        List<Rule> rules = List.of(rule("r", surface("a"), anyOf(
                List.of(sameAsFirst()),
                List.of(surface("b"), gap(2), sameAsFirst()))));

        assertThat(texts(rules, "a/shared", "c/shared")).containsExactly("ac");
        assertThat(texts(rules, "a/shared", "b/branch", "x/other", "c/shared")).containsExactly("abxc");
        assertThat(texts(rules, "a/shared", "b/branch", "c/branch")).isEmpty();
    }

    @Test
    void sameAsFirstInsideDeeplyNestedAlternativesUsesNonZeroRootStart() {
        List<Rule> rules = List.of(rule("r", anyOf(
                List.of(surface("a"), anyOf(
                        List.of(surface("b"), anyOf(
                                List.of(sameAsFirst()),
                                List.of(surface("c"), sameAsFirst()))))),
                List.of(surface("z"), sameAsFirst()))));

        assertThat(match(rules, "prefix/other", "a/shared", "b/branch", "c/inner", "d/shared"))
                .extracting(Match::start, Match::end, Match::text)
                .containsExactly(tuple(6, 10, "abcd"));
        assertThat(texts(rules, "prefix/other", "a/shared", "b/branch", "d/shared")).containsExactly("abd");
        assertThat(texts(rules, "prefix/other", "a/shared", "b/branch", "c/inner", "d/branch")).isEmpty();
        assertThat(texts(rules, "prefix/other", "a/shared", "b/branch", "c/inner", "d/inner")).isEmpty();
        assertThat(texts(rules, "prefix/other", "a/shared", "b/branch", "c/inner", "d/other")).isEmpty();
    }

    @Test
    void optionalStepBetweenRootAndSameAsFirstCanBeSkippedOrConsumed() {
        List<Rule> rules = List.of(rule("r", surface("a"), optional(surface("b")), sameAsFirst()));

        assertThat(texts(rules, "prefix/other", "a/shared", "c/shared")).containsExactly("ac");
        assertThat(texts(rules, "prefix/other", "a/shared", "b/middle", "c/shared")).containsExactly("abc");
        assertThat(texts(rules, "prefix/other", "a/shared", "c/other")).isEmpty();
        assertThat(texts(rules, "prefix/other", "a/shared", "b/middle", "c/middle")).isEmpty();
    }

    @Test
    void sameAsFirstRejectsUnknownOrBlankBaseForms() {
        List<Rule> rules = List.of(rule("r", surface("a"), sameAsFirst()));

        assertThat(texts(rules, "a/*", "b/*")).isEmpty();
        assertThat(texts(rules, "a/*", "b/known")).isEmpty();
        assertThat(texts(rules, "a/known", "b/*")).isEmpty();
        assertThat(texts(rules, "a//名詞", "b//名詞")).isEmpty();
        assertThat(texts(rules, "a/ /名詞", "b/ /名詞")).isEmpty();
        assertThat(texts(rules, "a/known", "b/ /名詞")).isEmpty();
        assertThat(texts(rules, "a/ /名詞", "b/known")).isEmpty();
    }

    @Test
    void sameAsFirstUsesANonZeroMatchStart() {
        List<Rule> rules = List.of(rule("r", surface("a"), gap(1), sameAsFirst()));

        assertThat(match(rules, "prefix/other", "a/shared", "x/middle", "b/shared"))
                .extracting(Match::start, Match::end, Match::text)
                .containsExactly(tuple(6, 9, "axb"));
        assertThat(texts(rules, "prefix/other", "a/shared", "x/middle", "b/other")).isEmpty();
    }

    @Test
    void sameAsFirstAlsoRequiresTheOtherTokenConstraints() {
        Step repeated = new Step(null, null, List.of("動詞"), null, null, null,
                List.of("る"), List.of("っ"), List.of("帰る"), true, false, null, null);
        List<Rule> rules = List.of(rule("r", surface("a"), repeated));

        assertThat(texts(rules, "a/戻る", "戻っ/戻る/動詞")).containsExactly("a戻っ");
        assertThat(texts(rules, "a/帰る", "帰っ/帰る/動詞")).isEmpty();
        assertThat(texts(rules, "a/戻る", "戻り/戻る/動詞")).isEmpty();
        assertThat(texts(rules, "a/戻る", "戻っ/戻る/名詞")).isEmpty();
    }

    @Test
    void optionalStepsPreserveSuffixExclusionAndSameAsFirstConstraints() {
        Step repeated = new Step(null, null, List.of("動詞"), null, null, null,
                List.of("る"), List.of("っ"), List.of("帰る"), true, false, null, null);
        List<Rule> rules = List.of(rule("r", surface("a"), optional(repeated), surface("c")));

        assertThat(texts(rules, "a/戻る", "c")).containsExactly("ac");
        assertThat(texts(rules, "a/戻る", "戻っ/戻る/動詞", "c")).containsExactly("a戻っc");
        assertThat(texts(rules, "a/戻る", "走っ/走る/動詞", "c")).isEmpty();
        assertThat(texts(rules, "a/帰る", "帰っ/帰る/動詞", "c")).isEmpty();
        assertThat(texts(rules, "a/戻る", "戻り/戻る/動詞", "c")).isEmpty();
        assertThat(texts(rules, "a/読む", "読っ/読む/動詞", "c")).isEmpty();
    }

    @Test
    void suffixExclusionAndSameAsFirstCombineWithFollowedByAndWordCompletion() {
        Step repeated = new Step(null, null, List.of("動詞"), null, null, null,
                List.of("る"), List.of("っ"), List.of("帰る"), true, false, null, null);
        List<Rule> rules = List.of(followedBy(baseForm("た"), "r",
                suffixes(List.of("る"), List.of("っ"), List.of("帰る")), surface("て"), repeated));

        assertThat(match(rules, "前", "戻っ/戻る/動詞", "て", "戻っ/戻る/動詞/連用タ接続",
                "た/た/助動詞/基本形/特殊・タ", "。"))
                .extracting(Match::start, Match::end, Match::text)
                .containsExactly(tuple(1, 7, "戻って戻った"));
        assertThat(texts(rules, "戻っ/戻る/動詞", "て", "戻っ/戻る/動詞/連用タ接続",
                "て/て/助詞/*/*/接続助詞", "。")).isEmpty();
        assertThat(texts(rules, "帰っ/帰る/動詞", "て", "帰っ/帰る/動詞/連用タ接続",
                "た/た/助動詞/基本形/特殊・タ")).isEmpty();
        assertThat(texts(rules, "戻っ/戻る/動詞", "て", "走っ/走る/動詞/連用タ接続",
                "た/た/助動詞/基本形/特殊・タ")).isEmpty();
        assertThat(texts(rules, "戻っ/戻る/動詞", "て", "戻り/戻る/動詞/連用形",
                "た/た/助動詞/基本形/特殊・タ")).isEmpty();
    }

    @Test
    void sequenceRunningPastLastTokenDoesNotMatch() {
        List<Rule> rules = List.of(rule("r", surface("a"), surface("b")));

        assertThat(texts(rules, "x", "a")).isEmpty();
    }

    @Test
    void reportsOverlappingAndNestedMatchesSortedByStartThenLongestFirst() {
        List<Rule> rules = List.of(
                rule("b", surface("b")),
                rule("ab", surface("a"), surface("b")),
                rule("bc", surface("b"), surface("c")));

        assertThat(match(rules, "a", "b", "c"))
                .extracting(m -> m.rule().id(), Match::start, Match::end)
                .containsExactly(tuple("ab", 0, 2), tuple("bc", 1, 3), tuple("b", 1, 2));
    }

    @Test
    void dropsSubsumedMatchFullyInsideSubsumingMatch() {
        List<Rule> rules = List.of(
                rule("outer", List.of("inner"), surface("a"), surface("b"), surface("c")),
                rule("inner", surface("b")));

        assertThat(match(rules, "a", "b", "c", "b"))
                .extracting(m -> m.rule().id(), Match::start)
                .containsExactly(tuple("outer", 0), tuple("inner", 3));
    }

    @Test
    void dropsMatchNestedInsideALongerMatchOfTheSameRule() {
        List<Rule> rules = List.of(rule("r", surface("a"), gap(3), surface("b")));

        assertThat(texts(rules, "a", "a", "b")).containsExactly("aab");
    }

    @Test
    void keepsSubsumedMatchThatOnlyPartlyOverlaps() {
        List<Rule> rules = List.of(
                rule("outer", List.of("inner"), surface("a"), surface("b")),
                rule("inner", surface("b"), surface("c")));

        assertThat(match(rules, "a", "b", "c")).extracting(m -> m.rule().id()).containsExactly("outer", "inner");
    }

    @Test
    void keepsNestedMatchWhenSubsumptionIsNotDeclared() {
        List<Rule> rules = List.of(
                rule("outer", surface("a"), surface("b"), surface("c")),
                rule("inner", surface("b")));

        assertThat(match(rules, "a", "b", "c")).extracting(m -> m.rule().id()).containsExactly("outer", "inner");
    }

    @Test
    void reportsCharacterOffsetsAndMatchedText() {
        List<Rule> rules = List.of(rule("r", surface("わけ"), surface("では"), surface("ない")));

        assertThat(match(rules, "高い", "わけ", "では", "ない", "。"))
                .extracting(Match::start, Match::end, Match::text)
                .containsExactly(tuple(2, 8, "わけではない"));
    }

    @Test
    void extendsMatchOverInflectionsToCompleteTheWord() {
        List<Rule> rules = List.of(rule("r", surface("なっ")));

        assertThat(texts(rules, "なっ/なる/動詞/連用タ接続", "た/た/助動詞/基本形/特殊・タ", "。")).containsExactly("なった");
    }

    @Test
    void completesTeFormButStopsAtTheNextVerb() {
        List<Rule> rules = List.of(rule("r", surface("なっ")));

        assertThat(texts(rules, "なっ/なる/動詞/連用タ接続", "て/て/助詞/*/*/接続助詞", "き/くる/動詞/連用形", "た/た/助動詞/基本形/特殊・タ"))
                .containsExactly("なって");
    }

    @Test
    void completesPolitePastNegative() {
        List<Rule> rules = List.of(rule("r", surface("なり")));

        assertThat(texts(rules, "なり/なる/動詞/連用形", "ませ/ます/助動詞/未然形/特殊・マス", "ん/ん/助動詞/基本形/不変化型",
                "でし/です/助動詞/連用形/特殊・デス", "た/た/助動詞/基本形/特殊・タ")).containsExactly("なりませんでした");
    }

    @Test
    void stopsBeforeConditionalStemThatNeedsBa() {
        List<Rule> rules = List.of(rule("r", surface("しまわ")));

        assertThat(texts(rules, "しまわ/しまう/動詞/未然形", "なけれ/ない/助動詞/仮定形/特殊・ナイ", "ば/ば/助詞/*/*/接続助詞"))
                .containsExactly("しまわ");
    }

    @Test
    void doesNotExtendFinishedForms() {
        List<Rule> rules = List.of(rule("r", surface("ない")));

        assertThat(texts(rules, "ない/ない/助動詞/基本形/特殊・ナイ", "です/です/助動詞/基本形/特殊・デス")).containsExactly("ない");
    }

    @Test
    void gapSkipsUpToItsBoundOfArbitraryTokens() {
        List<Rule> rules = List.of(rule("r", surface("a"), gap(2), surface("b")));

        assertThat(texts(rules, "a", "b")).containsExactly("ab");
        assertThat(texts(rules, "a", "x", "b")).containsExactly("axb");
        assertThat(texts(rules, "a", "x", "y", "b")).containsExactly("axyb");
        assertThat(texts(rules, "a", "x", "y", "z", "b")).isEmpty();
    }

    @Test
    void gapEndsAtTheNearestPositionWhereTheRestOfTheSequenceMatches() {
        List<Rule> nearest = List.of(rule("r", surface("a"), gap(5), surface("b")));
        List<Rule> wholeRest = List.of(rule("r", surface("a"), gap(5), surface("b"), surface("c")));

        assertThat(texts(nearest, "a", "x", "b", "y", "b")).containsExactly("axb");
        assertThat(texts(wholeRest, "a", "b", "x", "b", "c", "b", "c")).containsExactly("abxbc");
    }

    @Test
    void gapDoesNotCrossSentenceOrClausePunctuation() {
        List<Rule> rules = List.of(rule("r", surface("a"), gap(3), surface("b")));

        assertThat(texts(rules, "a", "。/。/記号/*/*/句点", "b")).isEmpty();
        assertThat(texts(rules, "a", "x", "、/、/記号/*/*/読点", "b")).isEmpty();
        assertThat(texts(rules, "a", "「/「/記号/*/*/括弧開", "b")).isEmpty();
        assertThat(texts(rules, "a", "」/」/記号/*/*/括弧閉", "b")).isEmpty();
        assertThat(texts(rules, "a", "\n/\n/記号/*/*/空白", "b")).isEmpty();
        assertThat(texts(rules, "a", "？/？/記号/*/*/一般", "b")).isEmpty();
        assertThat(texts(rules, "a", "!/*/名詞/*/*/サ変接続", "b")).isEmpty();
        assertThat(texts(rules, "a", "…/…/記号/*/*/一般", "b")).containsExactly("a…b");
    }

    @Test
    void ruleCanAllowACommaExplicitlyBeforeAGap() {
        List<Rule> rules = List.of(rule("r", surface("a"), optional(pos2("読点")), gap(2), surface("b")));

        assertThat(texts(rules, "a", "、/、/記号/*/*/読点", "x", "b")).containsExactly("a、xb");
        assertThat(texts(rules, "a", "x", "b")).containsExactly("axb");
    }

    @Test
    void gapInsideAnyOfIsLazyWithinItsOwnAlternative() {
        List<Rule> rules = List.of(rule("r",
                surface("a"),
                anyOf(List.of(surface("b"), gap(2), surface("c")), List.of(surface("d"))),
                surface("e")));

        assertThat(texts(rules, "a", "b", "x", "c", "e")).containsExactly("abxce");
        assertThat(texts(rules, "a", "d", "e")).containsExactly("ade");
        assertThat(texts(rules, "a", "b", "c", "x", "c", "e")).isEmpty();
    }

    @Test
    void gapNeedsTheClosingStepBeforeTheTextEnds() {
        List<Rule> rules = List.of(rule("r", surface("a"), gap(4), surface("b")));

        assertThat(texts(rules, "a", "x", "y")).isEmpty();
    }

    @Test
    void followedByRequiresMatchingNextTokenOrEndOfText() {
        List<Rule> rules = List.of(followedBy(surface("。"), "r", surface("a")));

        assertThat(texts(rules, "a", "。")).containsExactly("a");
        assertThat(texts(rules, "x", "a")).containsExactly("a");
        assertThat(texts(rules, "a", "b")).isEmpty();
    }

    @Test
    void notFollowedByRejectsMatchesBeforeAnyExcludedToken() {
        List<Rule> rules = List.of(notFollowedBy(List.of(surface("b"), baseForm("c")), "r", surface("a")));

        assertThat(texts(rules, "a", "b")).isEmpty();
        assertThat(texts(rules, "a", "cc/c")).isEmpty();
    }

    @Test
    void notFollowedByKeepsMatchesBeforeOtherTokens() {
        List<Rule> rules = List.of(notFollowedBy(List.of(surface("b")), "r", surface("a")));

        assertThat(texts(rules, "a", "x")).containsExactly("a");
        assertThat(texts(rules, "a", "x", "b")).containsExactly("a");
    }

    @Test
    void notFollowedByKeepsMatchesAtEndOfText() {
        List<Rule> rules = List.of(notFollowedBy(List.of(surface("b")), "r", surface("a")));

        assertThat(texts(rules, "a")).containsExactly("a");
        assertThat(texts(rules, "x", "a")).containsExactly("a");
    }

    @Test
    void followedByAndNotFollowedByBothApply() {
        List<Rule> rules = List.of(new Rule("r", "r", Level.N5, "meaning", "explanation", List.of(),
                List.of(surface("a")), List.of(surface("b"), surface("c")), List.of(surface("c")),
                List.of(), List.of(), List.of()));

        assertThat(texts(rules, "a", "b")).containsExactly("a");
        assertThat(texts(rules, "a", "c")).isEmpty();
        assertThat(texts(rules, "a", "x")).isEmpty();
        assertThat(texts(rules, "a")).containsExactly("a");
    }

    @Test
    void notFollowedByChecksBeforeWordCompletion() {
        List<Rule> excluded = List.of(notFollowedBy(List.of(surface("た")), "r", surface("なっ")));
        List<Rule> allowed = List.of(notFollowedBy(List.of(surface("。")), "r", surface("なっ")));

        assertThat(texts(excluded, "なっ/なる/動詞/連用タ接続", "た/た/助動詞/基本形/特殊・タ", "。")).isEmpty();
        assertThat(texts(allowed, "なっ/なる/動詞/連用タ接続", "た/た/助動詞/基本形/特殊・タ", "。")).containsExactly("なった");
    }

    @Test
    void notPrecededByRejectsMatchesRightAfterAnExcludedSequence() {
        List<Rule> rules = List.of(notPrecededBy(List.of(surface("x"), surface("y")), "r", surface("a")));

        assertThat(texts(rules, "x", "y", "a")).isEmpty();
        assertThat(texts(rules, "y", "a")).containsExactly("a");
        assertThat(texts(rules, "x", "y", "z", "a")).containsExactly("a");
        assertThat(texts(rules, "a")).containsExactly("a");
    }

    @Test
    void contextStepsApplySuffixAndExclusionConstraints() {
        Step context = suffixes(List.of("る"), List.of("っ"), List.of("帰る"));
        List<Rule> followed = List.of(followedBy(context, "r", surface("a")));
        List<Rule> notFollowed = List.of(notFollowedBy(List.of(context), "r", surface("a")));
        List<Rule> notPreceded = List.of(notPrecededBy(List.of(context), "r", surface("a")));

        assertThat(texts(followed, "a", "戻っ/戻る/動詞")).containsExactly("a");
        assertThat(texts(followed, "a", "帰っ/帰る/動詞")).isEmpty();
        assertThat(texts(followed, "a", "戻り/戻る/動詞")).isEmpty();
        assertThat(texts(notFollowed, "a", "戻っ/戻る/動詞")).isEmpty();
        assertThat(texts(notFollowed, "a", "帰っ/帰る/動詞")).containsExactly("a");
        assertThat(texts(notPreceded, "戻っ/戻る/動詞", "a")).isEmpty();
        assertThat(texts(notPreceded, "帰っ/帰る/動詞", "a")).containsExactly("a");
    }

    private static Rule triedEverywhere(Rule rule) {
        Step anyToken = new Step(null, null, null, null, null, null, null, null, null, false, false, null, null);
        return new Rule(rule.id(), rule.pattern(), rule.level(), rule.meaning(), rule.explanation(), rule.subsumes(),
                List.of(anyOf(rule.steps(), List.of(anyToken, pos()))), rule.followedBy(), rule.notFollowedBy(),
                rule.notPrecededBy(), rule.examples(), rule.counterExamples());
    }

    private static List<String> texts(List<Rule> rules, String... specs) {
        return match(rules, specs).stream().map(Match::text).toList();
    }

    private static List<Match> match(List<Rule> rules, String... specs) {
        List<Token> tokens = tokens(specs);
        String text = tokens.stream().map(Token::surface).collect(Collectors.joining());
        return new PatternMatcher(rules).match(text, tokens);
    }

    private static List<Token> tokens(String... specs) {
        List<Token> tokens = new ArrayList<>();
        int offset = 0;
        for (String spec : specs) {
            String[] parts = spec.split("/");
            String surface = parts[0];
            String baseForm = parts.length > 1 ? parts[1] : surface;
            String pos = parts.length > 2 ? parts[2] : "名詞";
            String conjForm = parts.length > 3 ? parts[3] : "*";
            String conjType = parts.length > 4 ? parts[4] : "*";
            String pos2 = parts.length > 5 ? parts[5] : "*";
            tokens.add(new Token(surface, baseForm, pos, pos2, "*", conjType, conjForm, surface, offset, offset + surface.length()));
            offset += surface.length();
        }
        return tokens;
    }

    private static Rule rule(String id, Step... steps) {
        return rule(id, List.of(), steps);
    }

    private static Rule rule(String id, List<String> subsumes, Step... steps) {
        return new Rule(id, id, Level.N5, "meaning", "explanation", subsumes, List.of(steps), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Rule followedBy(Step next, String id, Step... steps) {
        return new Rule(id, id, Level.N5, "meaning", "explanation", List.of(), List.of(steps), List.of(next), List.of(), List.of(), List.of(), List.of());
    }

    private static Rule notFollowedBy(List<Step> next, String id, Step... steps) {
        return new Rule(id, id, Level.N5, "meaning", "explanation", List.of(), List.of(steps), List.of(), next, List.of(), List.of(), List.of());
    }

    private static Rule notPrecededBy(List<Step> before, String id, Step... steps) {
        return new Rule(id, id, Level.N5, "meaning", "explanation", List.of(), List.of(steps), List.of(), List.of(), List.of(before), List.of(), List.of());
    }

    private static Step surface(String... values) {
        return new Step(List.of(values), null, null, null, null, null, null, null, null, false, false, null, null);
    }

    private static Step baseForm(String... values) {
        return new Step(null, List.of(values), null, null, null, null, null, null, null, false, false, null, null);
    }

    private static Step pos2(String... values) {
        return new Step(null, null, null, List.of(values), null, null, null, null, null, false, false, null, null);
    }

    private static Step pos(String... values) {
        return new Step(null, null, List.of(values), null, null, null, null, null, null, false, false, null, null);
    }

    private static Step suffixes(List<String> baseFormSuffix, List<String> surfaceSuffix, List<String> notBaseForm) {
        return new Step(null, null, List.of("動詞"), null, null, null, baseFormSuffix, surfaceSuffix, notBaseForm,
                false, false, null, null);
    }

    private static Step sameAsFirst() {
        return new Step(null, null, null, null, null, null, null, null, null, true, false, null, null);
    }

    private static Step optional(Step step) {
        return new Step(step.surface(), step.baseForm(), step.pos(), step.pos2(), step.conjType(), step.conjForm(),
                step.baseFormSuffix(), step.surfaceSuffix(), step.notBaseForm(), step.sameAsFirst(), true, step.anyOf(), step.gap());
    }

    @SafeVarargs
    private static Step anyOf(List<Step>... alternatives) {
        return new Step(null, null, null, null, null, null, null, null, null, false, false, List.of(alternatives), null);
    }

    private static Step gap(int maxSkipped) {
        return new Step(null, null, null, null, null, null, null, null, null, false, false, null, maxSkipped);
    }
}
