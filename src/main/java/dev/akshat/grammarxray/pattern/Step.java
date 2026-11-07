package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.Token;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record Step(List<String> surface, List<String> baseForm, List<String> pos, List<String> pos2,
                   List<String> conjType, List<String> conjForm, List<String> baseFormSuffix,
                   List<String> surfaceSuffix, List<String> notBaseForm, boolean sameAsFirst,
                   boolean optional, List<List<Step>> anyOf, Integer gap) {

    public boolean isAlternation() {
        return anyOf != null;
    }

    public boolean isGap() {
        return gap != null;
    }

    public List<List<String>> constraints() {
        return Stream.of(surface, baseForm, pos, pos2, conjType, conjForm, baseFormSuffix, surfaceSuffix, notBaseForm)
                .filter(Objects::nonNull).toList();
    }

    public boolean matches(Token token) {
        return allows(surface, token.surface())
                && allows(baseForm, token.baseForm())
                && allows(pos, token.pos1())
                && allows(pos2, token.pos2())
                && allows(conjType, token.conjType())
                && allows(conjForm, token.conjForm())
                && endsWithAny(baseFormSuffix, token.baseForm())
                && endsWithAny(surfaceSuffix, token.surface())
                && (notBaseForm == null || !contains(notBaseForm, token.baseForm()));
    }

    private static boolean allows(List<String> allowed, String value) {
        return allowed == null || contains(allowed, value);
    }

    private static boolean contains(List<String> values, String value) {
        for (int index = 0; index < values.size(); index++) {
            if (Objects.equals(values.get(index), value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithAny(List<String> suffixes, String value) {
        if (suffixes == null) {
            return true;
        }
        if (value != null) {
            for (int index = 0; index < suffixes.size(); index++) {
                if (value.endsWith(suffixes.get(index))) {
                    return true;
                }
            }
        }
        return false;
    }
}
