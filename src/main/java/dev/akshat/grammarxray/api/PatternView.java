package dev.akshat.grammarxray.api;

import dev.akshat.grammarxray.pattern.Level;
import dev.akshat.grammarxray.pattern.Rule;

import java.util.List;

public record PatternView(String id, String pattern, Level level, String meaning, String explanation, List<String> examples) {

    static PatternView of(Rule rule) {
        return new PatternView(rule.id(), rule.pattern(), rule.level(), rule.meaning(), rule.explanation(), rule.examples());
    }
}
