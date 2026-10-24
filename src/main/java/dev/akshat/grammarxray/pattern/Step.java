package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.Token;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record Step(List<String> surface, List<String> baseForm, List<String> pos, List<String> pos2,
                   List<String> conjType, List<String> conjForm, boolean optional, List<List<Step>> anyOf,
                   Integer gap) {

    public boolean isAlternation() {
        return anyOf != null;
    }

    public boolean isGap() {
        return gap != null;
    }

    public List<List<String>> constraints() {
        return Stream.of(surface, baseForm, pos, pos2, conjType, conjForm).filter(Objects::nonNull).toList();
    }

    public boolean matches(Token token) {
        return allows(surface, token.surface())
                && allows(baseForm, token.baseForm())
                && allows(pos, token.pos1())
                && allows(pos2, token.pos2())
                && allows(conjType, token.conjType())
                && allows(conjForm, token.conjForm());
    }

    private static boolean allows(List<String> allowed, String value) {
        return allowed == null || allowed.contains(value);
    }
}
