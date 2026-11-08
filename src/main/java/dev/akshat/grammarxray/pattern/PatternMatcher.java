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
    private static final int[] NO_POSITIONS = new int[0];

    private final List<Rule> rules;
    private final Map<String, List<Integer>> bySurface = new HashMap<>();
    private final Map<String, List<Integer>> byBaseForm = new HashMap<>();
    private final Map<String, List<Integer>> byPos = new HashMap<>();
    private final Map<String, List<Integer>> byPos2 = new HashMap<>();
    private final List<Integer> unanchored = new ArrayList<>();
    private final List<List<Step>> starters = new ArrayList<>();
    private final Map<String, List<Integer>> requiredBySurface = new HashMap<>();
    private final Map<String, List<Integer>> requiredByBaseForm = new HashMap<>();
    private final int[] literalRequirements;
    private final int[] maxMatchTokens;
    private final int requirementCount;
    private final Map<String, Set<String>> subsumers = new HashMap<>();

    public PatternMatcher(List<Rule> rules) {
        this.rules = List.copyOf(rules);
        literalRequirements = new int[this.rules.size()];
        Arrays.fill(literalRequirements, -1);
        maxMatchTokens = new int[this.rules.size()];
        Map<LiteralRequirement, Integer> requirementIds = new HashMap<>();
        for (int ordinal = 0; ordinal < this.rules.size(); ordinal++) {
            index(ordinal);
            indexRequirement(ordinal, requirementIds);
        }
        requirementCount = requirementIds.size();
        for (Rule outer : this.rules) {
            outer.subsumes().forEach(inner -> subsumers.computeIfAbsent(inner, id -> new HashSet<>()).add(outer.id()));
        }
    }

    public List<Match> match(String text, List<Token> tokens) {
        List<Match> matches = new ArrayList<>();
        int[] lastTried = new int[rules.size()];
        Arrays.fill(lastTried, -1);
        LiteralOccurrences literals = findRequiredLiterals(tokens);
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            tryCandidates(bySurface.get(token.surface()), text, tokens, i, lastTried, literals, matches);
            tryCandidates(byBaseForm.get(token.baseForm()), text, tokens, i, lastTried, literals, matches);
            tryCandidates(byPos.get(token.pos1()), text, tokens, i, lastTried, literals, matches);
            tryCandidates(byPos2.get(token.pos2()), text, tokens, i, lastTried, literals, matches);
            tryCandidates(unanchored, text, tokens, i, lastTried, literals, matches);
        }
        return withoutSubsumed(matches).stream().sorted(ORDER).toList();
    }

    private void tryCandidates(List<Integer> candidates, String text, List<Token> tokens, int first,
                               int[] lastTried, LiteralOccurrences literals, List<Match> matches) {
        if (candidates == null) {
            return;
        }
        Token token = tokens.get(first);
        for (int index = 0; index < candidates.size(); index++) {
            int ordinal = candidates.get(index);
            if (lastTried[ordinal] != first) {
                lastTried[ordinal] = first;
                if (literals.contains(literalRequirements[ordinal], first, maxMatchTokens[ordinal]) && canStart(ordinal, token)) {
                    tryRule(rules.get(ordinal), text, tokens, first, matches);
                }
            }
        }
    }

    private boolean canStart(int ordinal, Token token) {
        List<Step> steps = starters.get(ordinal);
        if (steps.isEmpty()) {
            return true;
        }
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).matches(token)) {
                return true;
            }
        }
        return false;
    }

    private void index(int ordinal) {
        List<Step> firstSteps = new ArrayList<>();
        if (collectStarters(rules.get(ordinal).steps(), firstSteps) != StartState.NON_NULLABLE || firstSteps.isEmpty()) {
            starters.add(List.of());
            unanchored.add(ordinal);
            return;
        }
        starters.add(List.copyOf(firstSteps));
        for (Step anchor : firstSteps) {
            if (anchor.surface() == null && anchor.baseForm() == null && anchor.pos() == null && anchor.pos2() == null) {
                unanchored.add(ordinal);
                return;
            }
        }
        for (Step anchor : firstSteps) {
            Map<String, List<Integer>> index;
            List<String> keys;
            if (anchor.surface() != null) {
                index = bySurface;
                keys = anchor.surface();
            } else if (anchor.baseForm() != null) {
                index = byBaseForm;
                keys = anchor.baseForm();
            } else if (anchor.pos() != null) {
                index = byPos;
                keys = anchor.pos();
            } else {
                index = byPos2;
                keys = anchor.pos2();
            }
            keys.forEach(key -> index.computeIfAbsent(key, k -> new ArrayList<>()).add(ordinal));
        }
    }

    private static StartState collectStarters(List<Step> sequence, List<Step> starters) {
        for (Step step : sequence) {
            if (step.isGap() || step.sameAsFirst()) {
                return StartState.UNKNOWN;
            }
            boolean nullable = step.optional();
            if (step.isAlternation()) {
                for (List<Step> alternative : step.anyOf()) {
                    StartState state = collectStarters(alternative, starters);
                    if (state == StartState.UNKNOWN) {
                        return state;
                    }
                    nullable |= state == StartState.NULLABLE;
                }
            } else {
                starters.add(step);
            }
            if (!nullable) {
                return StartState.NON_NULLABLE;
            }
        }
        return StartState.NULLABLE;
    }

    private enum StartState {
        NON_NULLABLE, NULLABLE, UNKNOWN
    }

    private void indexRequirement(int ordinal, Map<LiteralRequirement, Integer> requirementIds) {
        List<Step> sequence = rules.get(ordinal).steps();
        LiteralRequirement requirement = requiredLiteral(sequence);
        if (requirement == null) {
            return;
        }
        Integer id = requirementIds.get(requirement);
        if (id == null) {
            id = requirementIds.size();
            requirementIds.put(requirement, id);
            for (String surface : requirement.surfaces()) {
                requiredBySurface.computeIfAbsent(surface, key -> new ArrayList<>()).add(id);
            }
            for (String baseForm : requirement.baseForms()) {
                requiredByBaseForm.computeIfAbsent(baseForm, key -> new ArrayList<>()).add(id);
            }
        }
        literalRequirements[ordinal] = id;
        maxMatchTokens[ordinal] = maxTokens(sequence);
    }

    private static LiteralRequirement requiredLiteral(List<Step> sequence) {
        LiteralRequirement best = null;
        for (Step step : sequence) {
            if (step.optional() || step.isGap()) {
                continue;
            }
            LiteralRequirement candidate = null;
            if (step.isAlternation()) {
                Set<String> surfaces = new HashSet<>();
                Set<String> baseForms = new HashSet<>();
                boolean required = true;
                for (List<Step> alternative : step.anyOf()) {
                    LiteralRequirement literal = requiredLiteral(alternative);
                    if (literal == null) {
                        required = false;
                        break;
                    }
                    surfaces.addAll(literal.surfaces());
                    baseForms.addAll(literal.baseForms());
                }
                if (required) {
                    candidate = new LiteralRequirement(surfaces, baseForms);
                }
            } else if (step.surface() != null) {
                candidate = new LiteralRequirement(new HashSet<>(step.surface()), Set.of());
            } else if (step.baseForm() != null) {
                candidate = new LiteralRequirement(Set.of(), new HashSet<>(step.baseForm()));
            }
            if (candidate != null && (best == null || candidate.size() < best.size())) {
                best = candidate;
            }
        }
        return best;
    }

    private static int maxTokens(List<Step> sequence) {
        int length = 0;
        for (Step step : sequence) {
            int consumed = 1;
            if (step.isGap()) {
                consumed = step.gap();
                if (consumed < 0) {
                    return Integer.MAX_VALUE;
                }
            } else if (step.isAlternation()) {
                consumed = 0;
                for (List<Step> alternative : step.anyOf()) {
                    consumed = Math.max(consumed, maxTokens(alternative));
                }
            }
            length = (int) Math.min(Integer.MAX_VALUE, (long) length + consumed);
        }
        return length;
    }

    private LiteralOccurrences findRequiredLiterals(List<Token> tokens) {
        int[][] positions = new int[requirementCount][];
        int[] sizes = new int[requirementCount];
        if (requirementCount > 0) {
            for (int index = 0; index < tokens.size(); index++) {
                Token token = tokens.get(index);
                addOccurrences(requiredBySurface.get(token.surface()), index, positions, sizes);
                addOccurrences(requiredByBaseForm.get(token.baseForm()), index, positions, sizes);
            }
        }
        for (int id = 0; id < requirementCount; id++) {
            positions[id] = sizes[id] == 0 ? NO_POSITIONS : Arrays.copyOf(positions[id], sizes[id]);
        }
        return new LiteralOccurrences(positions, new int[requirementCount]);
    }

    private static void addOccurrences(List<Integer> requirements, int token, int[][] positions, int[] sizes) {
        if (requirements == null) {
            return;
        }
        for (int index = 0; index < requirements.size(); index++) {
            int id = requirements.get(index);
            int size = sizes[id];
            int[] found = positions[id];
            if (size > 0 && found[size - 1] == token) {
                continue;
            }
            if (found == null) {
                found = new int[8];
                positions[id] = found;
            } else if (size == found.length) {
                found = Arrays.copyOf(found, found.length * 2);
                positions[id] = found;
            }
            found[size] = token;
            sizes[id] = size + 1;
        }
    }

    private record LiteralRequirement(Set<String> surfaces, Set<String> baseForms) {
        private int size() {
            return surfaces.size() + baseForms.size();
        }
    }

    private record LiteralOccurrences(int[][] positions, int[] cursors) {
        private boolean contains(int requirement, int first, int maxTokens) {
            if (requirement < 0) {
                return true;
            }
            int[] found = positions[requirement];
            int cursor = cursors[requirement];
            while (cursor < found.length && found[cursor] < first) {
                cursor++;
            }
            cursors[requirement] = cursor;
            return cursor < found.length && found[cursor] - first < maxTokens;
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
        if (next == tokens.size()) {
            return true;
        }
        for (Step excluded : rule.notFollowedBy()) {
            if (excluded.matches(tokens.get(next))) {
                return false;
            }
        }
        for (Step allowed : rule.followedBy()) {
            if (allowed.matches(tokens.get(next))) {
                return true;
            }
        }
        return rule.followedBy().isEmpty();
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
        for (int index = 0; index < step.anyOf().size(); index++) {
            next.or(advance(step.anyOf().get(index), tokens, start, offsets));
        }
        return next;
    }

    private static BitSet consume(Step step, List<Token> tokens, int start, BitSet offsets) {
        BitSet next = new BitSet();
        String firstBaseForm = step.sameAsFirst() ? tokens.get(start).baseForm() : null;
        if (step.sameAsFirst() && (firstBaseForm == null || firstBaseForm.isBlank() || "*".equals(firstBaseForm))) {
            return next;
        }
        for (int offset = offsets.nextSetBit(0); offset >= 0 && start + offset < tokens.size(); offset = offsets.nextSetBit(offset + 1)) {
            Token token = tokens.get(start + offset);
            if (step.matches(token) && (!step.sameAsFirst() || firstBaseForm.equals(token.baseForm()))) {
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
