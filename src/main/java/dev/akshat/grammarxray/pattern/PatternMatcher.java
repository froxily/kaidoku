package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PatternMatcher {

    private static final Comparator<Match> ORDER = Comparator.comparingInt(Match::start)
            .thenComparing(Comparator.comparingInt(Match::end).reversed())
            .thenComparing(match -> match.rule().id());
    private static final Set<String> OPEN_FORMS = Set.of("連用形", "連用タ接続", "連用テ接続", "未然形", "未然ウ接続");
    private static final Set<String> TE_FORMS = Set.of("連用形", "連用タ接続", "連用テ接続");
    private static final Set<String> TE = Set.of("て", "で");
    private static final Set<String> INFLECTIONS = Set.of("ます", "ない", "ぬ", "ん", "う", "よう", "まい", "たい");
    private static final Set<String> BOUNDARY_POS2 = Set.of("句点", "読点", "括弧開", "括弧閉", "空白");
    private static final Set<String> BOUNDARY_SURFACES = Set.of("！", "？", "!", "?", ",", ".", "(", ")");

    private final List<Rule> rules;
    private final Map<String, List<Integer>> bySurface = new HashMap<>();
    private final Map<String, List<Integer>> byBaseForm = new HashMap<>();
    private final Map<String, List<Integer>> byPos = new HashMap<>();
    private final List<Integer> unanchored = new ArrayList<>();
    private final Map<String, Set<String>> subsumers = new HashMap<>();

    public PatternMatcher(List<Rule> rules) {
        this.rules = List.copyOf(rules);
        for (int ordinal = 0; ordinal < this.rules.size(); ordinal++) {
            index(ordinal);
        }
        for (Rule outer : this.rules) {
            outer.subsumes().forEach(inner -> subsumers.computeIfAbsent(inner, id -> new HashSet<>()).add(outer.id()));
        }
    }

    public List<Match> match(String text, List<Token> tokens) {
        List<Match> matches = new ArrayList<>();
        int[] lastTried = new int[rules.size()];
        Arrays.fill(lastTried, -1);
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            for (List<Integer> candidates : List.of(
                    bySurface.getOrDefault(token.surface(), List.of()),
                    byBaseForm.getOrDefault(token.baseForm(), List.of()),
                    byPos.getOrDefault(token.pos1(), List.of()),
                    unanchored)) {
                for (int ordinal : candidates) {
                    if (lastTried[ordinal] != i) {
                        lastTried[ordinal] = i;
                        tryRule(rules.get(ordinal), text, tokens, i, matches);
                    }
                }
            }
        }
        return withoutSubsumed(matches).stream().sorted(ORDER).toList();
    }

    private void index(int ordinal) {
        List<Step> anchors = new ArrayList<>();
        collectAnchors(rules.get(ordinal).steps().getFirst(), anchors);
        if (anchors.stream().anyMatch(step -> step.surface() == null && step.baseForm() == null && step.pos() == null)) {
            unanchored.add(ordinal);
            return;
        }
        for (Step anchor : anchors) {
            Map<String, List<Integer>> index;
            List<String> keys;
            if (anchor.surface() != null) {
                index = bySurface;
                keys = anchor.surface();
            } else if (anchor.baseForm() != null) {
                index = byBaseForm;
                keys = anchor.baseForm();
            } else {
                index = byPos;
                keys = anchor.pos();
            }
            keys.forEach(key -> index.computeIfAbsent(key, k -> new ArrayList<>()).add(ordinal));
        }
    }

    private static void collectAnchors(Step first, List<Step> anchors) {
        if (first.isAlternation()) {
            first.anyOf().forEach(alternative -> collectAnchors(alternative.getFirst(), anchors));
        } else {
            anchors.add(first);
        }
    }

    private static void tryRule(Rule rule, String text, List<Token> tokens, int first, List<Match> matches) {
        if (!isPrecededCorrectly(rule, tokens, first)) {
            return;
        }
        int length = longestMatch(rule.steps(), tokens, first);
        if (length > 0 && isFollowedCorrectly(rule, tokens, first + length)) {
            int start = tokens.get(first).start();
            int end = tokens.get(wordEnd(tokens, first + length - 1)).end();
            matches.add(new Match(rule, start, end, text.substring(start, end)));
        }
    }

    private static boolean isPrecededCorrectly(Rule rule, List<Token> tokens, int first) {
        for (List<Step> excluded : rule.notPrecededBy()) {
            if (endsBefore(excluded, tokens, first)) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsBefore(List<Step> sequence, List<Token> tokens, int first) {
        int start = first - sequence.size();
        if (start < 0) {
            return false;
        }
        for (int index = 0; index < sequence.size(); index++) {
            if (!sequence.get(index).matches(tokens.get(start + index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFollowedCorrectly(Rule rule, List<Token> tokens, int next) {
        return rule.followedBy().isEmpty()
                || next == tokens.size()
                || rule.followedBy().stream().anyMatch(step -> step.matches(tokens.get(next)));
    }

    private static int wordEnd(List<Token> tokens, int last) {
        int end = last;
        while (end + 1 < tokens.size() && continuesWord(tokens.get(end), tokens.get(end + 1))) {
            end++;
        }
        return end;
    }

    private static boolean continuesWord(Token previous, Token next) {
        if ("ん".equals(previous.baseForm()) && "です".equals(next.baseForm()) && "連用形".equals(next.conjForm())) {
            return true;
        }
        if (!OPEN_FORMS.contains(previous.conjForm())) {
            return false;
        }
        if ("助詞".equals(next.pos1())) {
            return "接続助詞".equals(next.pos2()) && TE.contains(next.surface()) && TE_FORMS.contains(previous.conjForm());
        }
        boolean past = "特殊・タ".equals(next.conjType());
        return "助動詞".equals(next.pos1())
                && (past || INFLECTIONS.contains(next.baseForm()))
                && (past || !"仮定形".equals(next.conjForm()));
    }

    private static int longestMatch(List<Step> steps, List<Token> tokens, int start) {
        BitSet initial = new BitSet();
        initial.set(0);
        return advance(steps, tokens, start, initial).length() - 1;
    }

    private static BitSet advance(List<Step> steps, List<Token> tokens, int start, BitSet offsets) {
        for (int index = 0; index < steps.size(); index++) {
            Step step = steps.get(index);
            if (step.isGap()) {
                return bridge(step.gap(), steps.subList(index + 1, steps.size()), tokens, start, offsets);
            }
            BitSet next = step.isAlternation()
                    ? alternatives(step, tokens, start, offsets)
                    : consume(step, tokens, start, offsets);
            if (step.optional()) {
                next.or(offsets);
            }
            if (next.isEmpty()) {
                return next;
            }
            offsets = next;
        }
        return offsets;
    }

    private static BitSet bridge(int maxSkipped, List<Step> rest, List<Token> tokens, int start, BitSet offsets) {
        BitSet next = new BitSet();
        for (int offset = offsets.nextSetBit(0); offset >= 0; offset = offsets.nextSetBit(offset + 1)) {
            next.or(nearest(maxSkipped, rest, tokens, start, offset));
        }
        return next;
    }

    private static BitSet nearest(int maxSkipped, List<Step> rest, List<Token> tokens, int start, int offset) {
        for (int skipped = 0; ; skipped++) {
            BitSet from = new BitSet();
            from.set(offset + skipped);
            BitSet reached = advance(rest, tokens, start, from);
            int skippable = start + offset + skipped;
            if (!reached.isEmpty() || skipped == maxSkipped || skippable >= tokens.size() || isBoundary(tokens.get(skippable))) {
                return reached;
            }
        }
    }

    private static boolean isBoundary(Token token) {
        return "記号".equals(token.pos1()) && BOUNDARY_POS2.contains(token.pos2()) || BOUNDARY_SURFACES.contains(token.surface());
    }

    private static BitSet alternatives(Step step, List<Token> tokens, int start, BitSet offsets) {
        BitSet next = new BitSet();
        step.anyOf().forEach(alternative -> next.or(advance(alternative, tokens, start, offsets)));
        return next;
    }

    private static BitSet consume(Step step, List<Token> tokens, int start, BitSet offsets) {
        BitSet next = new BitSet();
        for (int offset = offsets.nextSetBit(0); offset >= 0 && start + offset < tokens.size(); offset = offsets.nextSetBit(offset + 1)) {
            if (step.matches(tokens.get(start + offset))) {
                next.set(offset + 1);
            }
        }
        return next;
    }

    private List<Match> withoutSubsumed(List<Match> matches) {
        Map<String, List<Match>> byRule = matches.stream().collect(Collectors.groupingBy(match -> match.rule().id()));
        return matches.stream().filter(match -> !isSubsumed(match, byRule)).toList();
    }

    private boolean isSubsumed(Match inner, Map<String, List<Match>> byRule) {
        return Stream.concat(Stream.of(inner.rule().id()), subsumers.getOrDefault(inner.rule().id(), Set.of()).stream())
                .flatMap(outer -> byRule.getOrDefault(outer, List.of()).stream())
                .anyMatch(outer -> outer != inner && outer.start() <= inner.start() && inner.end() <= outer.end());
    }
}
