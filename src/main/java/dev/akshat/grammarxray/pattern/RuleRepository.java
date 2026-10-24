package dev.akshat.grammarxray.pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RuleRepository {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    private static final TypeReference<List<Rule>> RULE_LIST = new TypeReference<>() {
    };
    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MIN_EXAMPLES = 2;
    private static final int MIN_COUNTER_EXAMPLES = 1;
    private static final int MAX_GAP = 8;

    private final List<Rule> rules;
    private final Map<String, Rule> byId;

    public RuleRepository(List<Rule> rules) {
        validate(rules);
        this.rules = List.copyOf(rules);
        this.byId = rules.stream().collect(Collectors.toUnmodifiableMap(Rule::id, Function.identity()));
    }

    public static RuleRepository load(String locationPattern) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            if (resources.length == 0) {
                throw new InvalidRuleException("No rule files found at " + locationPattern);
            }
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
            List<Rule> rules = new ArrayList<>();
            for (Resource resource : resources) {
                rules.addAll(parse(resource.getFilename(), resource.getContentAsString(StandardCharsets.UTF_8)));
            }
            return new RuleRepository(rules);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read rules from " + locationPattern, e);
        }
    }

    static List<Rule> parse(String source, String json) {
        try {
            return MAPPER.readValue(json, RULE_LIST);
        } catch (JacksonException e) {
            throw new InvalidRuleException("Cannot parse " + source + ": " + e.getOriginalMessage(), e);
        }
    }

    public List<Rule> all() {
        return rules;
    }

    public Optional<Rule> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    private static void validate(List<Rule> rules) {
        Set<String> ids = new HashSet<>();
        for (Rule rule : rules) {
            require(rule.id() != null && RULE_ID.matcher(rule.id()).matches(), rule, "id must be kebab-case");
            require(ids.add(rule.id()), rule, "duplicate id");
            require(isPresent(rule.pattern()), rule, "pattern is required");
            require(rule.level() != null, rule, "level is required");
            require(isPresent(rule.meaning()), rule, "meaning is required");
            require(isPresent(rule.explanation()), rule, "explanation is required");
            validateSequence(rule, rule.steps());
            require(rule.followedBy().stream().allMatch(RuleRepository::isTokenStep),
                    rule, "followedBy steps need token constraints and cannot be optional, a gap or use anyOf");
            require(rule.notPrecededBy().stream().allMatch(sequence -> !sequence.isEmpty() && sequence.stream().allMatch(RuleRepository::isTokenStep)),
                    rule, "notPrecededBy sequences must not be empty, and their steps need token constraints and cannot be optional, a gap or use anyOf");
            require(rule.examples() != null && rule.examples().size() >= MIN_EXAMPLES, rule,
                    "needs at least " + MIN_EXAMPLES + " examples");
            require(rule.counterExamples() != null && rule.counterExamples().size() >= MIN_COUNTER_EXAMPLES, rule,
                    "needs at least " + MIN_COUNTER_EXAMPLES + " counter-example");
        }
        for (Rule rule : rules) {
            for (String subsumed : rule.subsumes()) {
                require(ids.contains(subsumed) && !subsumed.equals(rule.id()), rule, "cannot subsume '" + subsumed + "'");
            }
        }
    }

    private static void validateSequence(Rule rule, List<Step> steps) {
        require(steps != null && !steps.isEmpty(), rule, "step sequences must not be empty");
        require(!steps.getFirst().optional(), rule, "step sequences must not start with an optional step");
        require(!steps.getFirst().isGap() && !steps.getLast().isGap(), rule, "step sequences must not start or end with a gap");
        for (Step step : steps) {
            List<List<String>> constraints = step.constraints();
            long kinds = Stream.of(!constraints.isEmpty(), step.isAlternation(), step.isGap()).filter(Boolean::booleanValue).count();
            require(kinds == 1, rule, "each step needs exactly one of token constraints, anyOf or gap");
            require(constraints.stream().noneMatch(List::isEmpty), rule, "constraint lists must not be empty");
            if (step.isGap()) {
                require(step.gap() >= 1 && step.gap() <= MAX_GAP, rule, "gap must be between 1 and " + MAX_GAP);
                require(!step.optional(), rule, "a gap cannot be optional");
            }
            if (step.isAlternation()) {
                require(!step.anyOf().isEmpty(), rule, "anyOf must not be empty");
                step.anyOf().forEach(alternative -> validateSequence(rule, alternative));
            }
        }
    }

    private static boolean isTokenStep(Step step) {
        return !step.isAlternation() && !step.isGap() && !step.optional()
                && !step.constraints().isEmpty() && step.constraints().stream().noneMatch(List::isEmpty);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, Rule rule, String message) {
        if (!condition) {
            throw new InvalidRuleException("Rule '" + rule.id() + "': " + message);
        }
    }
}
