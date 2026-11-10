package dev.akshat.grammarxray.learning;

import dev.akshat.grammarxray.learning.Release.Kanji;
import dev.akshat.grammarxray.learning.Release.Vocab;
import dev.akshat.grammarxray.pattern.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class LessonPlanner {

    static final int KANJI_LESSON_SIZE = 10;
    static final int VOCAB_LESSON_SIZE = 12;

    private static final Comparator<Kanji> KANJI_ORDER = Comparator
            .comparing((Kanji kanji) -> kanji.freq() == null ? Integer.MAX_VALUE : kanji.freq())
            .thenComparingInt(Kanji::strokes)
            .thenComparing(Kanji::id);

    enum Category {
        VERBS("Verbs"), I_ADJECTIVES("い-adjectives"), NA_ADJECTIVES("な-adjectives"), NOUNS("Nouns"),
        ADVERBS("Adverbs"), EXPRESSIONS("Expressions and more");

        private static final Set<String> VERB_TAGS = Set.of("vk", "vs-s", "vs-i", "vs-c", "vz", "vn", "vr");
        private static final Set<String> NOUN_TAGS = Set.of("n", "pn", "num", "ctr", "n-suf", "n-pref", "n-adv", "n-t");

        final String label;

        Category(String label) {
            this.label = label;
        }

        String slug() {
            return name().toLowerCase().replace('_', '-');
        }

        static Category of(List<String> pos) {
            if (pos.stream().anyMatch(tag -> tag.startsWith("v1") || tag.startsWith("v5") || VERB_TAGS.contains(tag))) {
                return VERBS;
            }
            if (pos.contains("adj-i") || pos.contains("adj-ix")) {
                return I_ADJECTIVES;
            }
            if (pos.contains("adj-na")) {
                return NA_ADJECTIVES;
            }
            if (pos.stream().anyMatch(NOUN_TAGS::contains)) {
                return NOUNS;
            }
            if (pos.contains("adv") || pos.contains("adv-to")) {
                return ADVERBS;
            }
            return EXPRESSIONS;
        }
    }

    private LessonPlanner() {
    }

    static List<Lesson> kanji(List<Kanji> items) {
        List<Lesson> lessons = new ArrayList<>();
        byLevel(items, Kanji::level).forEach((level, group) -> {
            List<List<Kanji>> chunks = balancedChunks(group.stream().sorted(KANJI_ORDER).toList(), KANJI_LESSON_SIZE);
            for (int i = 0; i < chunks.size(); i++) {
                lessons.add(lesson(LearningKind.KANJI, level, i + 1, "Lesson " + (i + 1), null,
                        chunks.get(i).stream().map(Kanji::id).toList()));
            }
        });
        return List.copyOf(lessons);
    }

    static List<Lesson> vocab(List<Vocab> items) {
        List<Lesson> lessons = new ArrayList<>();
        byLevel(items, Vocab::level).forEach((level, group) -> {
            Map<Category, List<Vocab>> byCategory = new EnumMap<>(Category.class);
            group.forEach(vocab -> byCategory.computeIfAbsent(Category.of(vocab.pos()), key -> new ArrayList<>()).add(vocab));
            int number = 0;
            for (var entry : byCategory.entrySet()) {
                List<List<Vocab>> chunks = balancedChunks(entry.getValue(), VOCAB_LESSON_SIZE);
                for (int i = 0; i < chunks.size(); i++) {
                    lessons.add(lesson(LearningKind.VOCAB, level, ++number, entry.getKey().label + " " + (i + 1),
                            entry.getKey().slug(), chunks.get(i).stream().map(Vocab::id).toList()));
                }
            }
        });
        return List.copyOf(lessons);
    }

    static <T> List<List<T>> balancedChunks(List<T> items, int target) {
        if (items.isEmpty()) {
            return List.of();
        }
        int count = (items.size() + target - 1) / target;
        int base = items.size() / count;
        int extra = items.size() % count;
        List<List<T>> chunks = new ArrayList<>(count);
        int from = 0;
        for (int i = 0; i < count; i++) {
            int to = from + base + (i < extra ? 1 : 0);
            chunks.add(List.copyOf(items.subList(from, to)));
            from = to;
        }
        return chunks;
    }

    private static <T> Map<Level, List<T>> byLevel(List<T> items, Function<T, Level> level) {
        Map<Level, List<T>> groups = new EnumMap<>(Level.class);
        items.forEach(item -> groups.computeIfAbsent(level.apply(item), key -> new ArrayList<>()).add(item));
        return groups;
    }

    private static Lesson lesson(LearningKind kind, Level level, int number, String title, String category,
                                 List<String> itemIds) {
        String id = kind.path() + "-" + level.name().toLowerCase() + "-" + number;
        return new Lesson(id, kind, level, number, title, category, itemIds);
    }
}
