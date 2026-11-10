package dev.akshat.grammarxray.learning;

import dev.akshat.grammarxray.pattern.Level;

import java.util.List;
import java.util.Map;

final class Release {

    static final String VERIFICATION = "single_answer_against_all_pinned_source_readings";
    static final List<String> QUESTION_TYPES = List.of("vocab_reading", "kanji_on_reading");
    static final List<String> DATA_FILES = List.of("kanji.json", "vocab.json", "links.json", "questions.json");

    private Release() {
    }

    record Manifest(Map<String, Artifact> artifacts, String attribution, Map<String, Object> counts,
                    String datasetVersion, boolean grammarIncluded, String licenseUrl, String policy,
                    String schemaVersion, String scope, String sourceAuditStatus, String sourceLicense,
                    String sourceRevision, String status, List<String> withheld) {
    }

    record Artifact(long bytes, String sha256) {
    }

    record VocabDocument(Map<String, Object> metadata, List<Vocab> items) {
    }

    record KanjiDocument(Map<String, Object> metadata, List<Kanji> items) {
    }

    record Vocab(String id, String word, String reading, String readingHiragana, String readingKatakana,
                 String romaji, List<String> meanings, List<String> pos, Level level, long jmdictId,
                 List<String> otherForms, List<String> otherReadings) {
    }

    record Kanji(String id, String character, Level level, Integer grade, int strokes, String radical,
                 int radicalNumber, Integer freq, List<String> onyomi, List<String> onyomiDisplay,
                 List<String> kunyomi, List<String> kunyomiDisplay, List<String> nanori, List<String> meanings,
                 List<String> readingsSearch, List<String> words, Boolean supplementary) {
    }

    record Links(String schemaVersion, String sourceRevision, String positionUnit,
                 Map<String, List<KanjiPosition>> vocabToKanji, Map<String, List<VocabPosition>> kanjiToVocab,
                 Map<String, List<ExternalWord>> kanjiExternalWords, List<String> orphanKanji) {
    }

    record KanjiPosition(String kanji, List<Integer> positions) {
    }

    record VocabPosition(String vocabId, String relation, String sourceForm, List<Integer> positions) {
    }

    record ExternalWord(String word, Level level) {
    }

    record QuestionDocument(String schemaVersion, String sourceRevision, String policy, Map<String, Integer> counts,
                            List<Question> questions) {
    }

    record Question(String id, String type, Level level, String instruction, Map<String, String> prompt,
                    List<String> options, int answerIndex, String answer, List<String> distractors, String sourceId,
                    boolean verified, String verification) {
    }
}
