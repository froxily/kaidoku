package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.Token;

import java.util.AbstractList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.stream.Stream;

public record Step(List<String> surface, List<String> baseForm, List<String> pos, List<String> pos2,
                   List<String> conjType, List<String> conjForm, List<String> baseFormSuffix,
                   List<String> surfaceSuffix, List<String> notBaseForm, boolean sameAsFirst,
                   boolean optional, List<List<Step>> anyOf, Integer gap) {

    public Step {
        surface = indexed(surface);
        baseForm = indexed(baseForm);
        pos = indexed(pos);
        pos2 = indexed(pos2);
        conjType = indexed(conjType);
        conjForm = indexed(conjForm);
        notBaseForm = indexed(notBaseForm);
    }

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
        return values.contains(value);
    }

    private static List<String> indexed(List<String> values) {
        return values == null || values instanceof ValueList ? values : new ValueList(values);
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

    private static final class ValueList extends AbstractList<String> implements RandomAccess {
        private final String[] elements;
        private final Set<String> lookup;

        private ValueList(List<String> values) {
            elements = values.toArray(String[]::new);
            lookup = HashSet.newHashSet(elements.length);
            for (String element : elements) {
                lookup.add(element);
            }
        }

        @Override
        public String get(int index) {
            return elements[index];
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public boolean contains(Object value) {
            return lookup.contains(value);
        }
    }
}
