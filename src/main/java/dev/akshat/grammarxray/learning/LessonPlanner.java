package dev.akshat.grammarxray.learning;

import dev.akshat.grammarxray.learning.Release.Kanji;
import dev.akshat.grammarxray.learning.Release.Vocab;
import dev.akshat.grammarxray.pattern.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class LessonPlanner {

    static final int KANJI_SET_SIZE = 10;
    static final int VOCAB_SET_SIZE = 15;

    private static final Comparator<Kanji> KANJI_ORDER = Comparator
            .comparing((Kanji kanji) -> kanji.freq() == null ? Integer.MAX_VALUE : kanji.freq())
            .thenComparingInt(Kanji::strokes)
            .thenComparing(Kanji::id);

    private LessonPlanner() {
    }

    static List<Lesson> kanji(List<Kanji> items) {
        List<Lesson> lessons = new ArrayList<>();
        byLevel(items, Kanji::level).forEach((level, group) -> {
            List<List<Kanji>> chunks = balancedChunks(group.stream().sorted(KANJI_ORDER).toList(), KANJI_SET_SIZE);
            for (int i = 0; i < chunks.size(); i++) {
                lessons.add(lesson(LearningKind.KANJI, level, i + 1,
                        chunks.get(i).stream().map(Kanji::id).toList()));
            }
        });
        return List.copyOf(lessons);
    }

    static List<Lesson> vocab(List<Vocab> items) {
        List<Lesson> lessons = new ArrayList<>();
        byLevel(items, Vocab::level).forEach((level, group) -> {
            List<List<Vocab>> chunks = balancedChunks(group, VOCAB_SET_SIZE);
            for (int i = 0; i < chunks.size(); i++) {
                lessons.add(lesson(LearningKind.VOCAB, level, i + 1,
                        chunks.get(i).stream().map(Vocab::id).toList()));
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

    private static Lesson lesson(LearningKind kind, Level level, int number, List<String> itemIds) {
        String id = kind.path() + "-" + level.name().toLowerCase() + "-" + number;
        return new Lesson(id, kind, level, number, "Set " + number, itemIds);
    }
}
