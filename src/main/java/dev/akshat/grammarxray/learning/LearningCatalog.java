package dev.akshat.grammarxray.learning;

import dev.akshat.grammarxray.learning.LearningSummary.KindSummary;
import dev.akshat.grammarxray.learning.Release.ExternalWord;
import dev.akshat.grammarxray.learning.Release.Kanji;
import dev.akshat.grammarxray.learning.Release.KanjiDocument;
import dev.akshat.grammarxray.learning.Release.KanjiPosition;
import dev.akshat.grammarxray.learning.Release.Links;
import dev.akshat.grammarxray.learning.Release.Manifest;
import dev.akshat.grammarxray.learning.Release.Question;
import dev.akshat.grammarxray.learning.Release.QuestionDocument;
import dev.akshat.grammarxray.learning.Release.Vocab;
import dev.akshat.grammarxray.learning.Release.VocabDocument;
import dev.akshat.grammarxray.learning.Release.VocabPosition;
import dev.akshat.grammarxray.pattern.Level;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class LearningCatalog {

    static final String POSITION_UNIT = "Unicode code points, zero based, in source_form";
    private static final List<Level> LEVELS = List.of(Level.N5, Level.N4, Level.N3);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final LearningSummary summary;
    private final Map<LearningKind, List<Lesson>> lessons;

    private LearningCatalog(LearningSummary summary, Map<LearningKind, List<Lesson>> lessons) {
        this.summary = summary;
        this.lessons = lessons;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : -1;
    }

    public static LearningCatalog load(String location) {
        var loader = new DefaultResourceLoader();
        Map<String, byte[]> files = new HashMap<>();
        byte[] manifestBytes = readResource(loader.getResource(location + "manifest.json"));
        Manifest manifest = read("manifest.json", manifestBytes, Manifest.class);
        for (String name : manifest.artifacts().keySet()) {
            files.put(name, readResource(loader.getResource(location + name)));
        }
        verifyManifest(manifest, files);
        return parse(files, manifest.datasetVersion(), manifest.counts());
    }

    static void verifyManifest(Manifest manifest, Map<String, byte[]> files) {
        require("PASS".equals(manifest.status()), "manifest.json: status is " + manifest.status() + ", not PASS");
        require(manifest.artifacts().keySet().containsAll(Release.DATA_FILES), "manifest.json: missing a data artifact");
        manifest.artifacts().forEach((name, artifact) -> {
            byte[] bytes = files.get(name);
            require(bytes != null, name + ": listed in the manifest but not found");
            require(bytes.length == artifact.bytes(), name + ": " + bytes.length + " bytes, manifest says " + artifact.bytes());
            require(sha256(bytes).equals(artifact.sha256()), name + ": SHA-256 does not match the manifest");
        });
    }

    static LearningCatalog parse(Map<String, byte[]> files, String datasetVersion) {
        return parse(files, datasetVersion, null);
    }

    static LearningCatalog parse(Map<String, byte[]> files, String datasetVersion, Map<String, Object> manifestCounts) {
        KanjiDocument kanji = read("kanji.json", files.get("kanji.json"), KanjiDocument.class);
        VocabDocument vocab = read("vocab.json", files.get("vocab.json"), VocabDocument.class);
        Links links = read("links.json", files.get("links.json"), Links.class);
        QuestionDocument questions = read("questions.json", files.get("questions.json"), QuestionDocument.class);

        Map<String, Kanji> kanjiById = validateKanji(kanji);
        Map<String, Vocab> vocabById = validateVocab(vocab);
        validateLinks(links, kanjiById, vocabById);
        validateQuestions(questions, kanjiById, vocabById);
        if (manifestCounts != null) {
            require(number(manifestCounts.get("kanji")) == kanji.items().size()
                    && number(manifestCounts.get("vocab")) == vocab.items().size()
                    && number(manifestCounts.get("questions")) == questions.questions().size(),
                    "manifest.json: counts differ from the data files");
        }

        Map<LearningKind, List<Lesson>> lessons = Map.of(
                LearningKind.KANJI, LessonPlanner.kanji(kanji.items()),
                LearningKind.VOCAB, LessonPlanner.vocab(vocab.items()));
        Map<String, Object> metadata = vocab.metadata();
        LearningSummary summary = new LearningSummary(
                datasetVersion,
                (String) metadata.get("source_revision"),
                (String) metadata.get("attribution"),
                (String) metadata.get("license_url"),
                (String) metadata.get("jlpt_level_notice"),
                kindSummary(kanji.items(), Kanji::level, lessons.get(LearningKind.KANJI).size()),
                kindSummary(vocab.items(), Vocab::level, lessons.get(LearningKind.VOCAB).size()),
                Map.copyOf(questions.counts()));
        return new LearningCatalog(summary, lessons);
    }

    public LearningSummary summary() {
        return summary;
    }

    public List<Lesson> lessons(LearningKind kind, Level level) {
        List<Lesson> all = lessons.get(kind);
        return level == null ? all : all.stream().filter(lesson -> lesson.level() == level).toList();
    }

    private static Map<String, Kanji> validateKanji(KanjiDocument document) {
        require(document.items() != null && !document.items().isEmpty(), "kanji.json: no items");
        requireNoExamples("kanji.json", document.metadata());
        Map<String, Kanji> byId = index("kanji.json", document.items(), Kanji::id);
        for (Kanji kanji : document.items()) {
            String where = "kanji.json " + kanji.id();
            require(kanji.id().equals(kanji.character()), where + ": id differs from character");
            require(kanji.character().codePointCount(0, kanji.character().length()) == 1, where + ": not one character");
            require(LEVELS.contains(kanji.level()), where + ": level " + kanji.level() + " is outside N5–N3");
            require(kanji.strokes() > 0, where + ": no stroke count");
            require(notEmpty(kanji.meanings()), where + ": no meanings");
            require(kanji.onyomi() != null && kanji.kunyomi() != null && kanji.onyomiDisplay() != null
                    && kanji.kunyomiDisplay() != null && kanji.readingsSearch() != null, where + ": missing readings");
            require(notBlank(kanji.radical()), where + ": missing radical");
        }
        checkCounts("kanji.json", document.metadata(), document.items(), Kanji::level);
        return byId;
    }

    private static Map<String, Vocab> validateVocab(VocabDocument document) {
        require(document.items() != null && !document.items().isEmpty(), "vocab.json: no items");
        requireNoExamples("vocab.json", document.metadata());
        Map<String, Vocab> byId = index("vocab.json", document.items(), Vocab::id);
        for (Vocab vocab : document.items()) {
            String where = "vocab.json " + vocab.id();
            require(notBlank(vocab.word()) && notBlank(vocab.reading()) && notBlank(vocab.readingHiragana())
                    && notBlank(vocab.romaji()), where + ": missing word, reading or romaji");
            require(LEVELS.contains(vocab.level()), where + ": level " + vocab.level() + " is outside N5–N3");
            require(notEmpty(vocab.meanings()), where + ": no meanings");
            require(vocab.pos() != null, where + ": no part of speech");
        }
        checkCounts("vocab.json", document.metadata(), document.items(), Vocab::level);
        return byId;
    }

    private static void validateLinks(Links links, Map<String, Kanji> kanji, Map<String, Vocab> vocab) {
        require(POSITION_UNIT.equals(links.positionUnit()), "links.json: unexpected position unit " + links.positionUnit());
        require(links.vocabToKanji().keySet().equals(vocab.keySet()), "links.json: vocab_to_kanji keys differ from vocab ids");
        require(links.kanjiToVocab().keySet().equals(kanji.keySet()), "links.json: kanji_to_vocab keys differ from kanji ids");
        links.vocabToKanji().forEach((vocabId, entries) -> {
            String word = vocab.get(vocabId).word();
            for (KanjiPosition entry : entries) {
                require(kanji.containsKey(entry.kanji()), "links.json " + vocabId + ": unknown kanji " + entry.kanji());
                checkPositions("links.json " + vocabId, word, entry.kanji(), entry.positions());
            }
        });
        links.kanjiToVocab().forEach((character, entries) -> {
            for (VocabPosition entry : entries) {
                String where = "links.json " + character + " → " + entry.vocabId();
                Vocab word = vocab.get(entry.vocabId());
                require(word != null, where + ": unknown vocab id");
                boolean known = switch (Objects.requireNonNullElse(entry.relation(), "")) {
                    case "headword" -> word.word().equals(entry.sourceForm());
                    case "other_form" -> word.otherForms() != null && word.otherForms().contains(entry.sourceForm());
                    default -> false;
                };
                require(known, where + ": source form " + entry.sourceForm() + " does not belong to the word");
                checkPositions(where, entry.sourceForm(), character, entry.positions());
            }
        });
        links.kanjiExternalWords().forEach((character, words) -> {
            require(kanji.containsKey(character), "links.json: external words for unknown kanji " + character);
            for (ExternalWord word : words) {
                require(word.level() == Level.N2 || word.level() == Level.N1,
                        "links.json " + character + ": external word " + word.word() + " is not N2/N1");
            }
        });
    }

    private static void validateQuestions(QuestionDocument document, Map<String, Kanji> kanji, Map<String, Vocab> vocab) {
        require(document.questions() != null && !document.questions().isEmpty(), "questions.json: no questions");
        index("questions.json", document.questions(), Question::id);
        Map<String, Integer> counts = new HashMap<>();
        for (Question question : document.questions()) {
            String where = "questions.json " + question.id();
            require(Release.QUESTION_TYPES.contains(question.type()), where + ": unknown type " + question.type());
            require(question.verified() && Release.VERIFICATION.equals(question.verification()), where + ": not verified");
            require(LEVELS.contains(question.level()), where + ": level outside N5–N3");
            List<String> options = question.options();
            require(options != null && options.size() >= 2 && new HashSet<>(options).size() == options.size(),
                    where + ": options must be at least two distinct values");
            require(question.answerIndex() >= 0 && question.answerIndex() < options.size()
                    && options.get(question.answerIndex()).equals(question.answer()), where + ": answer_index does not point at answer");
            boolean sourceKnown = question.type().equals("vocab_reading")
                    ? vocab.containsKey(question.sourceId()) : kanji.containsKey(question.sourceId());
            require(sourceKnown, where + ": unknown source " + question.sourceId());
            counts.merge(question.type(), 1, Integer::sum);
        }
        require(counts.equals(document.counts()), "questions.json: counts " + counts + " differ from declared " + document.counts());
    }

    private static void requireNoExamples(String file, Map<String, Object> metadata) {
        require(metadata == null || !metadata.containsKey("examples"), file + ": metadata carries examples");
    }

    private static void checkPositions(String where, String form, String character, List<Integer> positions) {
        require(notEmpty(positions), where + ": no positions");
        int[] codePoints = form.codePoints().toArray();
        int expected = character.codePointAt(0);
        for (int position : positions) {
            require(position >= 0 && position < codePoints.length && codePoints[position] == expected,
                    where + ": position " + position + " in " + form + " is not " + character);
        }
    }

    private static <T> void checkCounts(String file, Map<String, Object> metadata, List<T> items, Function<T, Level> level) {
        require(metadata != null && metadata.get("counts") instanceof Map<?, ?>, file + ": metadata.counts missing");
        Map<?, ?> declared = (Map<?, ?>) metadata.get("counts");
        Map<Level, Long> actual = items.stream().collect(Collectors.groupingBy(level, Collectors.counting()));
        for (Level each : LEVELS) {
            Object count = declared.get(each.name());
            require(count instanceof Number number && number.longValue() == actual.getOrDefault(each, 0L),
                    file + ": " + each + " count " + actual.getOrDefault(each, 0L) + " differs from metadata " + count);
        }
        require(declared.get("total") instanceof Number total && total.longValue() == items.size(),
                file + ": total differs from metadata");
    }

    private static <T> KindSummary kindSummary(List<T> items, Function<T, Level> level, int lessons) {
        Map<Level, Integer> byLevel = new LinkedHashMap<>();
        LEVELS.forEach(each -> byLevel.put(each, 0));
        items.forEach(item -> byLevel.merge(level.apply(item), 1, Integer::sum));
        return new KindSummary(items.size(), byLevel, lessons);
    }

    private static <T> Map<String, T> index(String file, List<T> items, Function<T, String> id) {
        Map<String, T> byId = new LinkedHashMap<>();
        for (T item : items) {
            String key = id.apply(item);
            require(notBlank(key), file + ": item without id");
            require(byId.put(key, item) == null, file + ": duplicate id " + key);
        }
        return byId;
    }

    private static <T> T read(String name, byte[] bytes, Class<T> type) {
        require(bytes != null, name + ": not found");
        try {
            return MAPPER.readValue(bytes, type);
        } catch (JacksonException e) {
            throw new InvalidLearningDataException("Cannot parse " + name + ": " + e.getOriginalMessage(), e);
        }
    }

    private static byte[] readResource(Resource resource) {
        try (var in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource.getDescription(), e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean notEmpty(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidLearningDataException(message);
        }
    }
}
