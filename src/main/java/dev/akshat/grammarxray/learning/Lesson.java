package dev.akshat.grammarxray.learning;

import dev.akshat.grammarxray.pattern.Level;

import java.util.List;

public record Lesson(String id, LearningKind kind, Level level, int number, String title, String category,
                     List<String> itemIds) {

    public Lesson {
        itemIds = List.copyOf(itemIds);
    }
}
