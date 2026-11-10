package dev.akshat.grammarxray.learning;

import dev.akshat.grammarxray.pattern.Level;

import java.util.Map;

public record LearningSummary(String datasetVersion, String sourceRevision, String attribution, String licenseUrl,
                              String levelNotice, KindSummary kanji, KindSummary vocab,
                              Map<String, Integer> questions) {

    public record KindSummary(int total, Map<Level, Integer> byLevel, int lessons) {
    }
}
