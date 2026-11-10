package dev.akshat.grammarxray.learning;

import dev.akshat.grammarxray.pattern.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LearningCatalogTest {

    private static final String LOCATION = "classpath:static/learning/";
    private static LearningCatalog catalog;

    @BeforeAll
    static void loadRelease() {
        catalog = LearningCatalog.load(LOCATION);
    }

    @Test
    void releaseCountsMatchTheApprovedDataset() {
        LearningSummary summary = catalog.summary();

        assertThat(summary.datasetVersion()).isEqualTo("5e55150ef8c2657d1ecf6dd54e66e7eb17c5de0615607127e9968f0f80fe407b");
        assertThat(summary.kanji().total()).isEqualTo(640);
        assertThat(summary.kanji().byLevel()).containsExactly(
                Map.entry(Level.N5, 84), Map.entry(Level.N4, 169), Map.entry(Level.N3, 387));
        assertThat(summary.vocab().total()).isEqualTo(2963);
        assertThat(summary.vocab().byLevel()).containsExactly(
                Map.entry(Level.N5, 674), Map.entry(Level.N4, 630), Map.entry(Level.N3, 1659));
        assertThat(summary.questions()).containsOnly(Map.entry("vocab_reading", 2294), Map.entry("kanji_on_reading", 632));
        assertThat(summary.attribution()).startsWith("Contains data from OpenJLPT (CC BY-SA 4.0)");
        assertThat(summary.levelNotice()).contains("community estimates");
    }

    @ParameterizedTest
    @EnumSource(LearningKind.class)
    void lessonsCoverEveryItemExactlyOnce(LearningKind kind) {
        List<Lesson> lessons = catalog.lessons(kind, null);
        List<String> ids = lessons.stream().flatMap(lesson -> lesson.itemIds().stream()).toList();
        int total = kind == LearningKind.KANJI ? catalog.summary().kanji().total() : catalog.summary().vocab().total();

        assertThat(ids).hasSize(total).doesNotHaveDuplicates();
        assertThat(lessons).extracting(Lesson::id).doesNotHaveDuplicates();
        assertThat(lessons).hasSize(kind == LearningKind.KANJI
                ? catalog.summary().kanji().lessons() : catalog.summary().vocab().lessons());
    }

    @ParameterizedTest
    @EnumSource(LearningKind.class)
    void lessonsAreNumberedPerLevelAndStayWithinTheTargetSize(LearningKind kind) {
        int target = kind == LearningKind.KANJI ? LessonPlanner.KANJI_SET_SIZE : LessonPlanner.VOCAB_SET_SIZE;
        for (Level level : List.of(Level.N5, Level.N4, Level.N3)) {
            List<Lesson> lessons = catalog.lessons(kind, level);
            assertThat(lessons).isNotEmpty().allMatch(lesson -> lesson.level() == level && lesson.kind() == kind);
            assertThat(lessons).extracting(Lesson::number)
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, lessons.size()).boxed().toList());
            assertThat(lessons).allSatisfy(lesson -> {
                assertThat(lesson.title()).isEqualTo("Set " + lesson.number());
                assertThat(lesson.itemIds()).hasSizeBetween(target / 2, target);
            });
        }
    }

    @Test
    void kanjiLessonsStartWithTheMostFrequentCharacters() {
        Lesson first = catalog.lessons(LearningKind.KANJI, Level.N5).getFirst();

        assertThat(first.id()).isEqualTo("kanji-n5-1");
        assertThat(first.itemIds()).startsWith("日", "一", "国", "人");
    }

    @Test
    void vocabSetsKeepSourceOrder() {
        Lesson first = catalog.lessons(LearningKind.VOCAB, Level.N5).getFirst();

        assertThat(first.itemIds()).startsWith("ada066edfd");
    }

    @Test
    void vocabSetsUseBalancedTargetSizes() {
        List<Lesson> lessons = catalog.lessons(LearningKind.VOCAB, Level.N5);

        assertThat(lessons).hasSize(45);
        assertThat(lessons.getFirst().id()).isEqualTo("vocab-n5-1");
        assertThat(lessons.getFirst().title()).isEqualTo("Set 1");
        assertThat(lessons.getFirst().itemIds()).hasSize(15);
        assertThat(lessons).extracting(lesson -> lesson.itemIds().size()).containsOnly(14, 15);
    }

    @Test
    void chunksAreBalanced() {
        List<Integer> items = IntStream.range(0, 25).boxed().toList();

        assertThat(LessonPlanner.balancedChunks(items, 10)).extracting(List::size).containsExactly(9, 8, 8);
        assertThat(LessonPlanner.balancedChunks(items.subList(0, 20), 10)).extracting(List::size).containsExactly(10, 10);
        assertThat(LessonPlanner.balancedChunks(items.subList(0, 1), 10)).extracting(List::size).containsExactly(1);
        assertThat(LessonPlanner.balancedChunks(List.of(), 10)).isEmpty();
    }

    @Test
    void fixtureIsValid() {
        assertThatCode(() -> parse(fixture())).doesNotThrowAnyException();
    }

    @Test
    void rejectsExampleSentences() {
        assertRejected(vocab(json -> json.replace("\"jmdict_id\":1", "\"jmdict_id\":1,\"examples\":[]")), "Cannot parse vocab.json");
    }

    @Test
    void rejectsExamplesInMetadata() {
        assertRejected(vocab(json -> json.replace("\"metadata\":{", "\"metadata\":{\"examples\":[],")), "metadata carries examples");
    }

    @Test
    void rejectsKanjiWithoutSearchReadings() {
        assertRejected(kanji(json -> json.replace(",\"readings_search\":[\"ひ\",\"ニチ\"]", "")), "missing readings");
    }

    @Test
    void rejectsDataThatDisagreesWithTheManifestCounts() {
        Map<String, byte[]> bytes = new HashMap<>();
        fixture().forEach((name, json) -> bytes.put(name, json.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> LearningCatalog.parse(bytes, "test", Map.of("kanji", 640, "vocab", 1, "questions", 2)))
                .isInstanceOf(InvalidLearningDataException.class).hasMessageContaining("counts differ");
        assertThatCode(() -> LearningCatalog.parse(bytes, "test", Map.of("kanji", 1, "vocab", 1, "questions", 2)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnverifiedQuestions() {
        assertRejected(questions(json -> json.replace("\"verified\":true", "\"verified\":false")), "not verified");
    }

    @Test
    void rejectsAnswerIndexThatDoesNotPointAtTheAnswer() {
        assertRejected(questions(json -> json.replace("\"answer_index\":0", "\"answer_index\":1")), "answer_index");
    }

    @Test
    void rejectsKanjiKunReadingAsDistractor() {
        assertRejected(questions(json -> json.replace("\"ゴ\"", "\"ヒ\"")), "q1: ambiguous option");
    }

    @Test
    void rejectsVocabHomographReadingAsDistractor() {
        Map<String, String> files = vocab(json -> json
                .replace("\"N5\":1", "\"N5\":2")
                .replace("\"total\":1", "\"total\":2")
                .replace("\"jmdict_id\":1}", """
                        "jmdict_id":1},{"id":"v2","word":"日","reading":"か",\
                        "reading_hiragana":"か","reading_katakana":"カ","romaji":"ka","meanings":["day"],\
                        "pos":["n"],"level":"N5","jmdict_id":2}"""));
        files.computeIfPresent("links.json", (name, json) -> json
                .replace("\"vocab_to_kanji\":{", "\"vocab_to_kanji\":{\"v2\":[{\"kanji\":\"日\",\"positions\":[0]}],")
                .replace("\"kanji_to_vocab\":{\"日\":[", """
                        "kanji_to_vocab":{"日":[{"vocab_id":"v2","relation":"headword","source_form":"日","positions":[0]},"""));

        assertRejected(files, "q2: ambiguous option");

        files.computeIfPresent("vocab.json", (name, json) -> json.replace("\"id\":\"v2\",\"word\":\"日\"",
                "\"id\":\"v2\",\"word\":\"か\",\"other_forms\":[\"日\"]"));
        files.computeIfPresent("links.json", (name, json) -> json
                .replace("\"v2\":[{\"kanji\":\"日\",\"positions\":[0]}]", "\"v2\":[]")
                .replace("\"vocab_id\":\"v2\",\"relation\":\"headword\"", "\"vocab_id\":\"v2\",\"relation\":\"other_form\""));

        assertRejected(files, "q2: ambiguous option");
    }

    @Test
    void rejectsVocabAnswerThatIsNotAReading() {
        assertRejected(questions(json -> json
                .replace("\"options\":[\"ひ\",\"か\"]", "\"options\":[\"ご\",\"か\"]")
                .replace("\"answer\":\"ひ\"", "\"answer\":\"ご\"")), "q2: answer is not a reading");
    }

    @Test
    void rejectsKanjiAnswerThatIsNotAnOnReading() {
        assertRejected(questions(json -> json.replace("\"ニチ\"", "\"ヒ\"")), "q1: answer is not an on-reading");
    }

    @Test
    void rejectsQuestionPromptsThatDoNotExactlyMatchTheirSource() {
        assertRejected(questions(json -> json.replace("\"prompt\":{\"word\":\"日\"}",
                "\"prompt\":{\"word\":\"月\"}")), "q2: prompt");
        assertRejected(questions(json -> json.replace("\"prompt\":{\"word\":\"日\"}",
                "\"prompt\":{\"word\":\"日\",\"reading\":\"ひ\"}")), "q2: prompt");
        assertRejected(questions(json -> json.replace("\"prompt\":{\"character\":\"日\"}",
                "\"prompt\":{\"character\":\"月\"}")), "q1: prompt");
        assertRejected(questions(json -> json.replace("\"prompt\":{\"character\":\"日\"}",
                "\"prompt\":{\"character\":\"日\",\"reading\":\"ニチ\"}")), "q1: prompt");
    }

    @Test
    void acceptsVocabAnswerInKatakana() {
        assertThatCode(() -> parse(questions(json -> json.replace("\"ひ\"", "\"ヒ\""))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAlternativeVocabReadingAsDistractor() {
        assertRejected(vocab(json -> json.replace("\"jmdict_id\":1", "\"jmdict_id\":1,\"other_readings\":[\"カ\"]")),
                "q2: ambiguous option");
    }

    @Test
    void normalizesKanjiReadingPrefixesAndOkurigana() {
        assertThatCode(() -> parse(kanji(json -> json.replace("\"onyomi\":[\"ニチ\"]", "\"onyomi\":[\"-にち.び\"]"))))
                .doesNotThrowAnyException();
        assertRejected(kanji(json -> json.replace("\"kunyomi\":[\"ひ\"]", "\"kunyomi\":[\"-ご.す\"]")),
                "q1: ambiguous option");
    }

    @Test
    void rejectsItemsOutsideN5ToN3() {
        assertRejected(kanji(json -> json.replace("\"level\":\"N5\"", "\"level\":\"N2\"")), "outside N5–N3");
    }

    @Test
    void rejectsCountsThatDisagreeWithMetadata() {
        assertRejected(kanji(json -> json.replace("\"total\":1", "\"total\":2")), "total differs");
    }

    @Test
    void rejectsExternalWordsThatAreNotHigherLevel() {
        assertRejected(links(json -> json.replace("\"level\":\"N1\"", "\"level\":\"N3\"")), "is not N2/N1");
    }

    @Test
    void linkPositionsCountCodePointsNotUtf16Units() {
        Map<String, String> files = fixture();
        files.replaceAll((name, json) -> json.replace("\"word\":\"日\"", "\"word\":\"𠮟日\""));
        files.computeIfPresent("links.json", (name, json) -> json
                .replace("\"source_form\":\"日\"", "\"source_form\":\"𠮟日\"")
                .replace("\"positions\":[0]", "\"positions\":[1]"));
        assertThatCode(() -> parse(files)).doesNotThrowAnyException();

        files.computeIfPresent("links.json", (name, json) -> json.replace("\"positions\":[1]", "\"positions\":[2]"));
        assertThatThrownBy(() -> parse(files)).isInstanceOf(InvalidLearningDataException.class).hasMessageContaining("position 2");
    }

    @Test
    void rejectsSourceFormsThatDoNotBelongToTheWord() {
        assertRejected(links(json -> json.replace("\"source_form\":\"日\"", "\"source_form\":\"月\"")), "does not belong");
    }

    @Test
    void rejectsTamperedArtifacts() throws IOException {
        Release.Manifest manifest = manifest();
        Map<String, byte[]> files = releaseFiles(manifest);
        byte[] kanji = files.get("kanji.json").clone();
        kanji[kanji.length / 2] ^= 1;
        files.put("kanji.json", kanji);

        assertThatThrownBy(() -> LearningCatalog.verifyManifest(manifest, files))
                .isInstanceOf(InvalidLearningDataException.class).hasMessageContaining("SHA-256");
    }

    @Test
    void rejectsManifestThatDidNotPass() throws IOException {
        Release.Manifest passed = manifest();
        Release.Manifest building = new Release.Manifest(passed.artifacts(), passed.attribution(), passed.counts(),
                passed.datasetVersion(), passed.grammarIncluded(), passed.licenseUrl(), passed.policy(),
                passed.schemaVersion(), passed.scope(), passed.sourceAuditStatus(), passed.sourceLicense(),
                passed.sourceRevision(), "BUILDING", passed.withheld());

        assertThatThrownBy(() -> LearningCatalog.verifyManifest(building, releaseFiles(passed)))
                .isInstanceOf(InvalidLearningDataException.class).hasMessageContaining("not PASS");
    }

    @Test
    void everyQuestionIsServableAsIs() {
        assertThat(new HashSet<>(Release.QUESTION_TYPES)).containsExactlyInAnyOrderElementsOf(catalog.summary().questions().keySet());
    }

    private static Release.Manifest manifest() throws IOException {
        try (InputStream in = LearningCatalogTest.class.getResourceAsStream("/static/learning/manifest.json")) {
            return JsonMapper.builder()
                    .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                    .build().readValue(in, Release.Manifest.class);
        }
    }

    private static Map<String, byte[]> releaseFiles(Release.Manifest manifest) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        for (String name : manifest.artifacts().keySet()) {
            try (InputStream in = LearningCatalogTest.class.getResourceAsStream("/static/learning/" + name)) {
                files.put(name, in.readAllBytes());
            }
        }
        return files;
    }

    private static void assertRejected(Map<String, String> files, String message) {
        assertThatThrownBy(() -> parse(files)).isInstanceOf(InvalidLearningDataException.class).hasMessageContaining(message);
    }

    private static Map<String, String> kanji(UnaryOperator<String> change) {
        return changed("kanji.json", change);
    }

    private static Map<String, String> vocab(UnaryOperator<String> change) {
        return changed("vocab.json", change);
    }

    private static Map<String, String> links(UnaryOperator<String> change) {
        return changed("links.json", change);
    }

    private static Map<String, String> questions(UnaryOperator<String> change) {
        return changed("questions.json", change);
    }

    private static Map<String, String> changed(String name, UnaryOperator<String> change) {
        Map<String, String> files = fixture();
        files.computeIfPresent(name, (key, json) -> change.apply(json));
        return files;
    }

    private static LearningCatalog parse(Map<String, String> files) {
        Map<String, byte[]> bytes = new HashMap<>();
        files.forEach((name, json) -> bytes.put(name, json.getBytes(StandardCharsets.UTF_8)));
        return LearningCatalog.parse(bytes, "test");
    }

    private static Map<String, String> fixture() {
        Map<String, String> files = new HashMap<>();
        files.put("kanji.json", """
                {"metadata":{"counts":{"N5":1,"N4":0,"N3":0,"total":1}},"items":[{"id":"日","character":"日",\
                "level":"N5","grade":1,"strokes":4,"radical":"日","radical_number":72,"freq":1,"onyomi":["ニチ"],\
                "onyomi_display":["ニチ"],"kunyomi":["ひ"],"kunyomi_display":["ひ"],"meanings":["day"],\
                "readings_search":["ひ","ニチ"],"words":["日"]}]}""");
        files.put("vocab.json", """
                {"metadata":{"counts":{"N5":1,"N4":0,"N3":0,"total":1},"attribution":"a","license_url":"u",\
                "jlpt_level_notice":"n","source_revision":"r"},"items":[{"id":"v1","word":"日","reading":"ひ",\
                "reading_hiragana":"ひ","reading_katakana":"ヒ","romaji":"hi","meanings":["day"],"pos":["n"],\
                "level":"N5","jmdict_id":1}]}""");
        files.put("links.json", """
                {"schema_version":"2.0.0","source_revision":"r","position_unit":"Unicode code points, zero based, in source_form",\
                "vocab_to_kanji":{"v1":[{"kanji":"日","positions":[0]}]},\
                "kanji_to_vocab":{"日":[{"vocab_id":"v1","relation":"headword","source_form":"日","positions":[0]}]},\
                "kanji_external_words":{"日":[{"word":"日没","level":"N1"}]},"orphan_kanji":[]}""");
        files.put("questions.json", """
                {"schema_version":"2.0.0","source_revision":"r","policy":"p","counts":{"kanji_on_reading":1,"vocab_reading":1},\
                "questions":[{"id":"q1","type":"kanji_on_reading","level":"N5","instruction":"i",\
                "prompt":{"character":"日"},"options":["ニチ","ゴ"],"answer_index":0,"answer":"ニチ",\
                "distractors":["ゴ"],"source_id":"日","verified":true,\
                "verification":"single_answer_against_all_pinned_source_readings"},\
                {"id":"q2","type":"vocab_reading","level":"N5","instruction":"i",\
                "prompt":{"word":"日"},"options":["ひ","か"],"answer_index":0,"answer":"ひ",\
                "distractors":["か"],"source_id":"v1","verified":true,\
                "verification":"single_answer_against_all_pinned_source_readings"}]}""");
        return files;
    }
}
