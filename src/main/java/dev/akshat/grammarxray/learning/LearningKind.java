package dev.akshat.grammarxray.learning;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

public enum LearningKind {
    KANJI("kanji"), VOCAB("vocab");

    private final String path;

    LearningKind(String path) {
        this.path = path;
    }

    @JsonValue
    public String path() {
        return path;
    }

    public static Optional<LearningKind> fromPath(String path) {
        return Arrays.stream(values()).filter(kind -> kind.path.equals(path)).findFirst();
    }
}
