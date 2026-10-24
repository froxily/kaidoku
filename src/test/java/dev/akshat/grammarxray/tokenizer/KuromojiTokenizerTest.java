package dev.akshat.grammarxray.tokenizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class KuromojiTokenizerTest {

    private static final JapaneseTokenizer tokenizer = new KuromojiTokenizer();

    @ParameterizedTest
    @ValueSource(strings = {
            "日本語を勉強しています。",
            "高いわけではない。",
            "全部読んでしまった。",
            "明日までに出さなければならない。",
            "東京 タワー\n𠮟られた。"
    })
    void tokensCoverTextWithCorrectOffsets(String text) {
        List<Token> tokens = tokenizer.tokenize(text);
        print(text, tokens);

        int expectedStart = 0;
        for (Token token : tokens) {
            assertThat(token.start()).isEqualTo(expectedStart);
            assertThat(text.substring(token.start(), token.end())).isEqualTo(token.surface());
            expectedStart = token.end();
        }
        assertThat(expectedStart).isEqualTo(text.length());
    }

    @Test
    void splitsTeIruPoliteForm() {
        assertThat(tokenizer.tokenize("日本語を勉強しています。"))
                .extracting(Token::surface, Token::baseForm, Token::pos1, Token::start, Token::end)
                .containsExactly(
                        tuple("日本語", "日本語", "名詞", 0, 3),
                        tuple("を", "を", "助詞", 3, 4),
                        tuple("勉強", "勉強", "名詞", 4, 6),
                        tuple("し", "する", "動詞", 6, 7),
                        tuple("て", "て", "助詞", 7, 8),
                        tuple("い", "いる", "動詞", 8, 9),
                        tuple("ます", "ます", "助動詞", 9, 11),
                        tuple("。", "。", "記号", 11, 12));
    }

    @Test
    void tokenizesConcurrentlyWithSameResult() throws Exception {
        List<String> texts = List.of("日本語を勉強しています。", "高いわけではない。", "全部読んでしまった。", "明日までに出さなければならない。");
        Map<String, List<Token>> expected = texts.stream().collect(Collectors.toMap(Function.identity(), tokenizer::tokenize));

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> results = IntStream.range(0, 2_000)
                    .mapToObj(i -> texts.get(i % texts.size()))
                    .map(text -> executor.submit(() -> tokenizer.tokenize(text).equals(expected.get(text))))
                    .toList();
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        }
    }

    private static void print(String text, List<Token> tokens) {
        StringBuilder out = new StringBuilder("\n").append(text.replace("\n", "\\n")).append('\n');
        for (Token t : tokens) {
            out.append(String.format("  [%2d,%2d) %-6s base=%-6s pos=%s/%s/%s conj=%s/%s reading=%s%n",
                    t.start(), t.end(), t.surface().replace("\n", "\\n"), t.baseForm(),
                    t.pos1(), t.pos2(), t.pos3(), t.conjType(), t.conjForm(), t.reading()));
        }
        System.out.print(out);
    }
}
