package dev.akshat.grammarxray.pattern;

import dev.akshat.grammarxray.tokenizer.JapaneseTokenizer;
import dev.akshat.grammarxray.tokenizer.KuromojiTokenizer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MatchSpanTest {

    private static final RuleRepository rules = RuleRepository.load("classpath:patterns/*.json");
    private static final JapaneseTokenizer tokenizer = new KuromojiTokenizer();
    private static final PatternMatcher matcher = new PatternMatcher(rules.all());

    @ParameterizedTest(name = "{1} in {0} spans {2}")
    @CsvSource(delimiter = '|', textBlock = """
            日本語が話せるようになった。             | you-ni-naru      | 話せるようになった
            最近よく眠れるようになってきた。         | you-ni-naru      | 眠れるようになって
            昨日は一日中寝ていた。                   | te-iru           | ていた
            まだ食べていない。                       | te-iru           | ていない
            日本語を勉強しています。                 | te-iru           | ています
            電車に乗り遅れてしまいました。           | te-shimau        | てしまいました
            食べてしまったら困る。                   | te-shimau        | てしまったら
            書いてしまわなければならない。           | te-shimau        | てしまわ
            書いてしまわなければならない。           | nakereba-naranai | なければならない
            昨日は早く起きなければならなかった。     | nakereba-naranai | なければならなかった
            行かなきゃだめだ。                       | nakereba-naranai | なきゃだめ
            全部読んだわけではなかった。             | wake-dewa-nai    | わけではなかった
            高いわけではないです。                   | wake-dewa-nai    | わけではない
            会議は中止することになりませんでした。   | koto-ni-naru     | することになりませんでした
            行きたくなかった。                       | tai              | たくなかった
            鍵はここにあるはずだった。               | hazu-da          | はずだった
            窓を開けておいてください。               | te-oku           | ておいて
            窓を開けておいてください。               | te-kudasai       | てください
            もう行かなきゃ。                         | nakya            | なきゃ
            今日は寒いらしい。                       | rashii           | 寒いらしい
            春らしい天気ですね。                     | rashii-noun      | 春らしい
            彼しか来なかった。                       | shika-nai        | しか来なかった
            肉しか食べないし酒も飲まない。           | shika-nai        | しか食べない
            読んだり書いたり話したりする。           | tari-tari        | だり書いたり話したりする
            テストは思ったほど難しくなかった。       | hodo-nai         | 思ったほど難しくなかった
            歌も歌えば、踊りも踊る。                 | mo-ba-mo         | も歌えば、踊りも
            これさえ覚えておけば大丈夫だ。           | sae-ba           | さえ覚えておけば
            """)
    void matchCoversTheCompleteConjugatedForm(String text, String ruleId, String expected) {
        assertThat(matcher.match(text, tokenizer.tokenize(text)))
                .extracting(match -> match.rule().id(), Match::text)
                .contains(tuple(ruleId, expected));
    }
}
