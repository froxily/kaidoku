package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.Token;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
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
        Step anyConjugation = new Step(null, null, null, null, null, List.of("*"), false, null, null);
        List<Rule> rules = List.of(rule("r", anyConjugation, surface("た")));

        assertThat(texts(rules, "x", "y", "た")).containsExactly("yた");
    }

    @Test
    void triesEachCandidateRuleOncePerToken() {
        List<Rule> rules = List.of(rule("r", anyOf(List.of(surface("a")), List.of(baseForm("a")), List.of(pos("名詞")))));

        assertThat(texts(rules, "a")).containsExactly("a");
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
    void notPrecededByRejectsMatchesRightAfterAnExcludedSequence() {
        List<Rule> rules = List.of(notPrecededBy(List.of(surface("x"), surface("y")), "r", surface("a")));

        assertThat(texts(rules, "x", "y", "a")).isEmpty();
        assertThat(texts(rules, "y", "a")).containsExactly("a");
        assertThat(texts(rules, "x", "y", "z", "a")).containsExactly("a");
        assertThat(texts(rules, "a")).containsExactly("a");
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
        return new Rule(id, id, Level.N5, "meaning", "explanation", subsumes, List.of(steps), List.of(), List.of(), List.of(), List.of());
    }

    private static Rule followedBy(Step next, String id, Step... steps) {
        return new Rule(id, id, Level.N5, "meaning", "explanation", List.of(), List.of(steps), List.of(next), List.of(), List.of(), List.of());
    }

    private static Rule notPrecededBy(List<Step> before, String id, Step... steps) {
        return new Rule(id, id, Level.N5, "meaning", "explanation", List.of(), List.of(steps), List.of(), List.of(before), List.of(), List.of());
    }

    private static Step surface(String... values) {
        return new Step(List.of(values), null, null, null, null, null, false, null, null);
    }

    private static Step baseForm(String... values) {
        return new Step(null, List.of(values), null, null, null, null, false, null, null);
    }

    private static Step pos2(String... values) {
        return new Step(null, null, null, List.of(values), null, null, false, null, null);
    }

    private static Step pos(String... values) {
        return new Step(null, null, List.of(values), null, null, null, false, null, null);
    }

    private static Step optional(Step step) {
        return new Step(step.surface(), step.baseForm(), step.pos(), step.pos2(), step.conjType(), step.conjForm(), true, step.anyOf(), step.gap());
    }

    @SafeVarargs
    private static Step anyOf(List<Step>... alternatives) {
        return new Step(null, null, null, null, null, null, false, List.of(alternatives), null);
    }

    private static Step gap(int maxSkipped) {
        return new Step(null, null, null, null, null, null, false, null, maxSkipped);
    }
}
