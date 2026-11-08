# Kaidoku (解読)

**Japanese Grammar Analysis Engine**

[![CI](https://github.com/froxily/kaidoku/actions/workflows/ci.yml/badge.svg)](https://github.com/froxily/kaidoku/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Paste any Japanese text, such as a news article, a manga page, a subtitle line or an email, and Kaidoku highlights the grammar patterns it recognises. Each one is colour-coded by JLPT level (N5–N3), and clicking a highlight explains the pattern.

*Kaidoku* (解読) is Japanese for "deciphering". It is a **rule-based Java 21 / Spring Boot service, not an LLM**. Kuromoji splits the text into morphemes, and a small matching engine runs 341 hand-written JSON grammar rules over the token stream. The same input always gives the same output, it works offline, and every rule is backed by tests.

![Kaidoku highlighting 13 grammar patterns in a Japanese paragraph, with the explanation card for ～ば～ほど open](docs/screenshot.png)

<!-- demo-gif: record it with docs/DEMO.md, save it as docs/demo.gif, then replace this comment with ![Demo](docs/demo.gif) -->

| | |
|---|---|
| Grammar patterns | **341** (36 N5 · 118 N4 · 187 N3), covering every in-scope N5 and N4 point and all 62 N3 points of an openly licensed JLPT grammar checklist, with restrictions ([coverage](#coverage)) |
| Tests | **3,323** unit and integration tests + **8** browser tests, run in CI on every push to `main` |
| Accuracy | **0.992 precision / 0.989 recall** on 655 regression sentences for the original 199 rules; a first run on a pre-registered held-out set for the 142 new rules scored 0.991 / 0.944 ([methodology](#evaluation)) |
| Speed | about **24 ms** to analyze 10,000 characters of grammar-dense text ([details](#performance)) |
| Stack | Java 21, Spring Boot 4.1, Kuromoji (IPADIC), vanilla JS, Docker, GitHub Actions, Playwright |

## Engineering highlights

- **Data-driven rule engine.** Grammar lives in JSON, not code. The loader validates every rule at startup and refuses to start on unknown fields, malformed steps, missing examples or a bad cross-reference.
- **NFA-style matcher.** Rules are indexed by their first token, and each match tracks the set of reachable token offsets, so optional steps, alternatives and bounded gaps never cause exponential backtracking.
- **Example-driven testing.** Each rule ships with its own examples and near-misses, a reviewed snapshot catches any rule that starts matching another rule's sentences, and a separate evaluation set measures precision and recall.
- **Production basics.** RFC 9457 error responses, input and body-size limits, a multi-stage Docker image that runs as a non-root user, and CI with a container smoke test.

## Why

Grammar patterns are the hard part of reading Japanese, because they span several words and change shape: わけではない can appear as わけじゃない, わけではありません or わけではなかった, and ている shrinks to てる or comes out as てた or てます. Dictionary tools look up words. I wanted something that marks the multi-word grammar in a whole text and says what level it is, mostly for my own N3 reading practice.

I couldn't find an open-source, rule-based tool that scans arbitrary Japanese text and labels grammar patterns by JLPT level, so I built one.

### Related tools

Each of these does something different. The descriptions come from each tool's own site, repository or store listing (checked 24 Oct 2026).

| Tool | What it does |
|---|---|
| [Bunpro](https://bunpro.jp) | Grammar study with spaced repetition; its example sentences are written in house |
| [Jisho](https://jisho.org/about) | Japanese–English dictionary; you can paste a whole paragraph, and it parses the text with MeCab to explain its words |
| [ichi.moe](https://ichi.moe) ([Ichiran](https://github.com/tshatrov/ichiran)) | Segments Japanese text into words and shows their JMdict meanings |
| [Yomitan](https://github.com/yomidevs/yomitan) | Pop-up dictionary browser extension and successor to Yomichan, for which community [grammar dictionaries](https://github.com/aiko-tanaka/Grammar-Dictionaries) exist |
| [Te-to-Te Reading Checker](https://www.intersc.tsukuba.ac.jp/~kyoten/en/?p=224) (University of Tsukuba) | Checks pasted text against several databases, including the Hagoromo function-word database, and charts the levels of its words and expressions |
| LLM-based analyzers, such as [Wakatta!](https://apps.apple.com/app/id6757565022) | AI-generated sentence breakdowns with grammar notes and JLPT tags |

Kaidoku's angle is narrower: a deterministic, open-source scan of a whole text that marks every grammar pattern it knows with a level, usable offline or through a REST API.

## Features

- **Variants of each pattern.** Rules cover the polite, casual, contracted and conjugated forms that apply. ～なければならない also matches なければなりません, なくてはいけない, なきゃならない, なくちゃだめ and more.
- **Whole conjugated words.** A highlight runs to the end of the word, so ようになった, てしまいました and ませんでした are marked in full.
- **Split patterns.** しか～ない, あまり～ない, ～たり～たりする, ～も～ば～も and わけでは決してない are matched across the words in between. The gap is bounded and never crosses a comma or the end of a sentence.
- **Overlapping patterns.** Nested matches are all shown; a rule can *subsume* a smaller one so it isn't reported twice, and a "Hide nested patterns" switch keeps only the outermost matches.
- **Honest about ambiguity.** When forms are identical at the token level, such as らしい after a noun ('seems' or 'typical of'), ～ませんか (invitation or negative question) or ～れる/～られる (passive, potential or respect), the card explains each reading instead of guessing.
- **Web page and REST API.** Level filters, explanation cards, a pattern list with counts, dark mode and a mobile layout, on top of an API that also works on its own.

<details>
<summary>All 341 patterns</summary>

- **N5 (36):** ～ている · ～てください · ～ないでください · ～たい · ～ましょう · ～でしょう · ～たことがある · ～前に · ～後で · ～から (reason) · ～ませんか · ～てから · ～に行く / ～に来る · ～がほしい · ～とき · ～たり～たりする · しか～ない · あまり～ない · 全然～ない · ～んです / ～のです · ～のが好きだ / ～のが上手だ · まだ～ていない · ～より～のほうが · ～の中で～が一番 · ～けど / ～けれど / ～けれども / だけど / ～が · ～だけ · （動詞）～まで · 疑問詞＋か · 疑問詞＋も～ない · ～はどうですか / ～はどう？ / ～はいかがですか · ～をください · 一番～ / いちばん～ · （数量）くらい / ぐらい・（時刻）ごろ · （名詞）にする / にします · ～より～（比較） · ～か～ないか
- **N4 (118):** ～てしまう · ～なければならない · ～なきゃ / ～なくちゃ · ～ば · ～ようにする · ～ようになる · ～によると · ～ながら · ～ておく · ～てみる · ～てもいい · ～てはいけない · ～かもしれない · ～そうだ (looks like) · ～そうだ (I hear) · ～たら · ～ほうがいい · ～つもりだ · ～すぎる · ～はずだ · ～ことができる · ～てあげる · ～てくれる · ～てもらう · ～てある · ～ていく · ～てくる · ～やすい · ～にくい · ～ようと思う · ～ないといけない · ～なくてもいい · ～ので · ～のに · ～と (conditional) · ～なら · ～ばよかった · ～てほしい · ～始める · ～終わる · ～続ける · ～させる (causative) · ～れる / ～られる · ～みたいだ · ～くなる / ～になる · ～ほど～ない · ～し · ～に～回 · ～がる · ～たらどうですか · (number)も · ～(よ)う · ～ていただけませんか · ～といい · ～てすみません · ～なさい · ～ても · ～てくれてありがとう · ～てよかった · ～ないで · ～かどうか · ～という · ～間 / ～間に · ～くする / ～にする · ～のような / ～のように · ～させられる · ～方 (way of ~ing) · ～ばいい / ～たらいい · お～になる / ご～になる · ～場合（は） · ～だけで · ～が必要だ / ～必要がある / ～必要はない · 音・匂い・味など＋がする · ～までに · ～頃 / ～ごろ / ～ころ · ～予定だ / ～予定です · ございます / でございます · いらっしゃる / おっしゃる / なさる / 召し上がる / くださる · ～ておる / ～ております · お～ください / ご～ください · ～かな / ～かなあ / ～かしら · ～かい · question word + でも · AとBと、どちらが～ · ～と思う · ～と聞いた · ～と言われている · ～と言ってもいい · adjective + さ · ～化する · ～風 (style) · ～づらい · ～でできている / ～からできている / ～から作る · (dictionary/ない form) + ことがある · ～など / ～なんか · ～に気がつく / ～に気づく · 何 + counter + か · ～の一つだ · もし～たら / もし～ば / もし～なら / もしも · ～ますように · ～とか～とか · ～させてください · ～じゃないか / ～ではないか / ～じゃない？ · いたす / 申す / 参る / 伺う / 拝見する / いただく / おる · （辞書形）な · ～く見える / ～に見える / ～ように見える · ～のは～だ / ～のは～です · お～する / ご～する / ご～いたす · ～って / ～だって / 何て · ～なくて · 毎～のように · ～ない～はない / ～ない～はいない · 動詞の命令形 · 疑問詞～か＋知る・分かる・教えるなど · （名詞）でも（提案） · ～でも～でも · （辞書形）のに（目的・用途） · ～たように / ～ているように / ～てあるように
- **N3 (187):** ～わけではない · ～ことになる · ～たばかり · ～ば～ほど · ～によって · ～わけがない · ～はずがない · ～ことにする · ～ばかり · ～ところだ · ～にとって · ～おかげで · ～せいで · ～ために · ～ためになる · ～らしい · ～らしい (after a noun) · ～っぽい · ～わりに · ～最中に · ～ようだ · ～に違いない · ～に決まっている · ～について · ～に対して · ～として · ～に関して · ～に比べて · ～にしては · ～たびに · ～うちに · ～ずに · ～たとたん · ～べきだ · ～っけ · ～なんて · ～がち · ～だらけ · ～出す · ～ようとする · ～ことはない · ～ついでに · ～かわりに · 決して～ない · ～も～ば～も · ～さえ～ば · とても～ない · ～て初めて · ～で有名 · ～をはじめ · ～的 · ～くらい / ～ぐらい (extent) · ～ほど (extent) · ～まま · ～としたら / ～とすれば · ～たて · ～きり / ～っきり · ～ふりをする · ～まい · ～さえ · なかなか～ない · ～ず · ～ように · ～こそ · ～に限る · ～とともに · ～にすぎない · ～において / ～における · ～げ · ～しかない · ～につれて · ～にしたがって · ～わけにはいかない · ～てたまらない / ～てしかたがない · ～くせに · たとえ～ても · ～おきに · ～わけだ · ～とおりに · ～をこめて · ～を通じて / ～を通して · ～だけでなく · わざわざ · まさか · いったい · どうやら · さらに · すでに · つい · むしろ · どうしても · せいぜい · ～とは限らない · ～ないことはない · ～から～にかけて · ～気味 · ～向け · ～をもとに · ～に反して · ～としても · ～ばかりか · ～ということだ · ～というより · ～に代わって · ～にしても · ～気がする · ～てからでないと · もしかしたら / もしかすると / もしかして · ～ようがない / ～ようもない · ～ずにはいられない · あまり～て / あまりに～て / ～のあまり · ～と言えば / ～といえば / ～というと / ～と言うと · ～と言っても / ～といっても / ～と言いましても · ～てもしかたがない / ～てもしょうがない · ～は別として / ～は別にして · ～そうもない / ～そうにない / ～そうにもない · あまりにも / あまりに · ～ぶりに / ～ぶりの / 久しぶり · めったに～ない · 必ずしも～ない / 必ずしも～とは限らない · ～ずつ · できるだけ / できる限り · ～ごとに / ～ごとの · まるで～ようだ / ～みたいだ / ～ような / ～ように · なぜなら（ば）～からだ / ～のだ / ～ためだ · どんなに～ても / いくら～ても / ～でも · ～た結果 / ～の結果 · ～という理由で / ～という理由から / ～という理由により · ～ばいいのに / ～ばよかったのに / ～たらいいのに / ～たらよかったのに · ～でよければ / ～でよかったら · ～一方だ · ～ながらも / ～ながら（逆接） · ～ではなくて / ～じゃなくて / ～ではなく · ～と同じで / ～と同じように / ～と違って / ～とは違って · ～と並んで / ～と並び / ～と並ぶ · ～というのは / ～っていうのは / ～ってのは / 「～」って · ～と言える / ～と言えます / ～と言えない · ～とみえる / ～とみえて / ～と見える · ～はもちろん / ～はもちろんのこと · ～んだって / ～なんだって · ～向き · ～途中で / ～途中に / ～途中だ · ～際に / ～際は / ～際には · ～を中心に / ～を中心として / ～を中心とした / ～を中心にして · ～に合わせて / ～に合わせた / ～に合った / ～に合わせる · ～に当たる / ～に当たります / ～に当たり · ～同士 · ～てごらん / ～でごらん / ～てごらんなさい · ～ている場合じゃない / ～ている場合ではない / ～てる場合じゃない · ～ても始まらない / ～でも始まらない / ～ても始まりません · ～がたい / ～難い · ～か何か / ～かなにか · ～である / ～であった / ～であり / ～であろう / ～であれば · ～させてもらう / ～させていただく · 別に～ない · なかなか～ / なかなかの～ · ～たものだ / ～たもんだ · ～んじゃない / ～のではないか · ～に慣れる · かえって / 却って · ～た上で / ～の上で（は） / ～る上で · ～上に · （数量・単位）当たり / あたり · ～ること / ～ないこと（指示） · ～ことから · ～ないと（文末） · ～たって / ～だって · ～て済む / ～で済む · ～からの / ～への / ～での / ～との / ～までの · ～かというと / ～かといえば · できれば / できたら / できることなら · （辞書形）には · ～のだろうか / ～のでしょうか / ～んだろうか / ～んでしょうか · ～つもりで / ～たつもりで · ～でもある / ～でもあり · ～かける / ～かけの / ～かけだ · ～切る / ～切れる / ～切れない · ～込む / ～こむ · ～直す / ～なおす · ～合う / ～あう · ～上げる / ～上がる · ～通す / ～とおす · ～っぱなし / ～っ放し · ～中（ちゅう / じゅう） · ～ても～なくても · ～ことは～が / ～ことは～けど · ～には～の～がある

</details>

### Coverage

The checklist is the N5, N4 and N3 grammar lists of the Wikibooks [JLPT Guide](https://en.wikibooks.org/wiki/JLPT_Guide). Basic particles and sentence frames from the N5 list (は, を, に, で, も, と, や, です, ～があります, ～がいます) aren't highlighted, because they would mark almost every word.

| Level | Points on the list | In scope | Covered |
|---|---|---|---|
| N5 | 40 | 29 | 29 |
| N4 | 50 | 50 | 50 |
| N3 | 62 | 62 | 62 |

All 62 N3 points have a rule, with restrictions for ambiguous forms: ～たものだ requires a recollection cue, ～に慣れる matches kanji 慣れる only, and kana かえって is restricted near destinations and motion verbs. See [Limitations](#limitations) for details. Other rules cover common N4 and N3 points the list leaves out, such as ～につれて, ～わけにはいかない, ～くせに and ～ばかりか.

## Quick start

You need Java 21 and Git. On Windows, use `mvnw.cmd` instead of `./mvnw`.

```bash
git clone https://github.com/froxily/kaidoku.git
```

```bash
cd kaidoku
```

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080 and click **Load example**. The port comes from the `PORT` environment variable and defaults to 8080.

Or with Docker:

```bash
docker build -t kaidoku .
```

```bash
docker run -p 8080:8080 kaidoku
```

Run the tests:

```bash
./mvnw test
```

```bash
./mvnw verify -Pe2e
```

The second command runs the 8 browser tests on their own (the `e2e` profile selects only them). They use an installed Edge by default (`-De2e.browser.channel=chrome` for Chrome) and never download a browser. For a scripted walkthrough and how to record a demo GIF, see [docs/DEMO.md](docs/DEMO.md).

## API

```bash
curl -s -X POST http://localhost:8080/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"text": "高いわけではない。"}'
```

```json
{
  "tokens": [
    { "surface": "高い", "baseForm": "高い", "pos": "形容詞", "reading": "タカイ", "start": 0, "end": 2 }
  ],
  "matches": [
    {
      "ruleId": "wake-dewa-nai",
      "pattern": "～わけではない",
      "level": "N3",
      "meaning": "it's not that ~ / it doesn't necessarily mean ~",
      "explanation": "Denies a conclusion the listener might reasonably draw, …",
      "start": 2,
      "end": 8,
      "text": "わけではない"
    }
  ]
}
```

The token list is shortened here. `start` and `end` are UTF-16 offsets, so they line up with JavaScript string indices, and matches come back in text order, longer first.

| Endpoint | Purpose |
|---|---|
| `POST /api/analyze` | Tokens and grammar matches for a text |
| `GET /api/patterns?level=N3` | The rules, without their matching steps; `level` is optional |
| `GET /api/tokens?text=…` | Raw Kuromoji tokens, for writing rules |

Errors use RFC 9457 `application/problem+json`: **400** for blank text, more than 10,000 characters, malformed JSON or an unknown level, and **413** for a body over 64 KiB, which is rejected before the JSON is parsed.

## How it works

```mermaid
flowchart LR
    T[Text] --> K["JapaneseTokenizer<br/>(Kuromoji + IPADIC)"]
    K --> TK[Tokens]
    J[("patterns/*.json")] --> R["RuleRepository<br/>(validated at startup)"]
    R --> M[PatternMatcher]
    TK --> M
    M --> MA[Matches]
    MA --> API[REST API]
    API --> UI[Web page]
```

1. **Tokenize.** Kuromoji splits the text into tokens with surface form, base form, part of speech, conjugation, reading and offsets. It sits behind a small `JapaneseTokenizer` interface, so another analyzer can replace it.
2. **Index.** Each rule is indexed by the surface form, base form or part of speech of the steps that can match its first token, so at each token only the rules that could start there are tried. Suffix values never become index keys; a first step with only POS and suffix constraints uses its POS anchor. Two prefilters, derived automatically from each rule, skip hopeless attempts: the token must match one of the rule's possible first steps, and a word that every match must contain (such as より or 当たる) must occur within the rule's maximum span. One pass over the text records where those words occur.
3. **Match.** A rule is a sequence of steps. A token step combines exact values, suffixes and exclusions, and `sameAsFirst` can require its base form to repeat the match's first token. Other steps offer alternative sub-sequences (`anyOf`) or skip a bounded number of tokens (`gap`) for split patterns. A gap is lazy, so しか pairs with the first ない after it, and it never crosses 。、, brackets, line breaks or ！？. Rules can also require the next token (`followedBy`, used to tell sentence-final なきゃ from the conditional), reject certain single tokens immediately after the match (`notFollowedBy`), or reject certain preceding tokens (`notPrecededBy`, which keeps ～ていく off どうやって行きますか). The longest match wins.
4. **Complete the word.** The span extends over the rest of the conjugated word: endings such as た, ます, ない, たい and ん, a て that completes a te-form, and ませんでした.
5. **Subsume and sort.** A match inside a longer match of a rule that declares `subsumes` (or of the same rule) is dropped, and the rest come back in text order.

A real rule from `src/main/resources/patterns/n3.json`, with its explanation and examples shortened:

```json
{
  "id": "wake-dewa-nai",
  "pattern": "～わけではない",
  "level": "N3",
  "meaning": "it's not that ~ / it doesn't necessarily mean ~",
  "explanation": "Denies a conclusion the listener might reasonably draw, …",
  "steps": [
    { "surface": ["わけ", "訳"], "pos": "名詞", "pos2": "非自立" },
    { "anyOf": [
      [ { "surface": "で", "baseForm": "だ" }, { "surface": "は", "pos": "助詞" } ],
      [ { "surface": "じゃ" } ]
    ] },
    { "gap": 1 },
    { "anyOf": [
      [ { "baseForm": "ない" } ],
      [ { "baseForm": "ある" }, { "baseForm": "ます" }, { "surface": "ん" } ]
    ] }
  ],
  "examples": ["高いわけではない。", "嫌いなわけじゃないよ。", "行きたくないわけではありません。", "全部読んだわけではなかった。"],
  "counterExamples": ["そんなわけがない。", "わけを教えてください。", "だから遅れたわけです。"]
}
```

Token constraints use OR within a list and AND across fields. `baseFormSuffix` and `surfaceSuffix` list allowed endings of fused compounds such as 読みかける and 使い切る, and require `pos`; `notBaseForm` lists forbidden base forms, overrides positive matches, and needs a positive constraint alongside it. These lists must be nonempty with no blank entries and belong on token steps, including context steps. `sameAsFirst: true` requires the base form at the root match start, even across a gap or inside an alternative; blank and `*` base forms fail. It checks repetition in patterns such as 静かなら静かなほど and 行くか行かないか. It may stand alone on a token step, but cannot appear on any token step that can consume the rule's first token (including inside leading alternatives), on an `anyOf` or `gap` step itself, or in context steps. Fused compounds whose suffix is unreliable (～出す, ～方, ～がる and kana ～こむ) use reviewed word lists instead.

To add a rule: dump the tokens of its example sentences with `/api/tokens` first (tokenization often surprises you: in では the で is the copula だ), write the steps against that output with at least two examples and one near-miss, then run `./mvnw test` and review the snapshot diff it reports before accepting it with `-Dcrosscheck.update=true`.

## Quality

### Tests

| Test class | Tests | What it covers |
|---|---|---|
| `PatternExamplesTest` | 3,058 | Every rule's examples match it, and every counter-example doesn't |
| `RuleRepositoryTest` | 124 | Rule loading and each fail-fast validation |
| `MatchSpanTest` | 27 | Exact highlight spans on real sentences |
| `PatternMatcherTest` | 73 | Optional steps, `anyOf`, gaps, longest match, overlaps, subsumption, offsets, word completion, suffixes, exclusions, `sameAsFirst`, `followedBy`, `notFollowedBy`, `notPrecededBy`, and that the candidate prefilters and subsumption index never change a result |
| `AnalyzeControllerTest`, `TokenControllerTest`, `PatternControllerTest`, `RequestBodySizeLimitTest` | 24 | API contract, validation, limits and error format |
| `KuromojiTokenizerTest` | 7 | Offsets cover the whole text, exact token output, thread safety |
| `RuleConsistencyTest` | 3 | Cross-rule checks (below) |
| `EvaluationTest` | 3 | Accuracy on the evaluation set (below) |
| `ExpansionEvaluationTest` | 3 | reports precision and recall of newly added rules on separate held-out sets (evaluation/heldout/), without a floor |
| `PatternMatcherPerformanceTest` | 1 | Twenty analyses of a text just under the 10,000-character limit within a time budget |
| `KaidokuE2eTest` (`-Pe2e`) | 8 | In a real browser: highlighting, cards, level filter, overlaps, hiding nested patterns, mobile layout, invalid input |

GitHub Actions runs three jobs on every push to `main` and every pull request: the test suite, the browser tests (in Chrome), and a Docker build that starts the container and checks it answers correctly as a non-root user.

**Cross-rule checks.** `RuleConsistencyTest` keeps every match on 2,941 distinct example and counterexample sentences in a reviewed snapshot, so a rule that starts matching another rule's sentences fails the build until the change is reviewed. It also checks that every declared `subsumes` actually suppresses a match, and that no two rules highlight exactly the same span.

### Evaluation

Development sentences and evaluation sets are kept apart:

| Set | Size | Written by | What it is for |
|---|---|---|---|
| **Development examples**: each rule's `examples` and `counterExamples` | 1,969 examples and 1,089 counterexamples | Written together with the rules | Building and regression-testing the rules. They pass by construction, so they say nothing about accuracy |
| **Regression set**: `src/test/resources/evaluation/cases.json` | 655 sentences, 195 of them near-misses | Drafted and labelled by a separate model (OpenAI Codex) | Measuring precision and recall. A test fails if any of them repeats a development example |

The regression set was built in two rounds for the original 199 rules:
- **Round 1:** 220 sentences (60 near-misses) for the first 49 rules. Codex worked in the project checkout, so it was not technically prevented from reading the rules.
- **Round 2:** 435 sentences (300 positives, 135 near-misses) for the other 150 rules, plus their labels on the first 220. Codex was given only each rule's name, meaning and explanation and told not to open the rules or their examples; that was an instruction, not a sandbox restriction.

| Run | Rules | Precision | Recall | F1 | Exact sentences |
|---|---|---|---|---|---|
| Round 1, blind first run | 49 | 0.973 | 0.984 | 0.978 | 212 / 220 |
| Round 2, blind first run | 199 | 0.987 | 0.971 | 0.979 | 629 / 655 |
| ↳ the 150 rules evaluated for the first time | 150 | 0.985 | 0.956 | 0.970 | |
| **Current**, after fixes | 199 | **0.992** | **0.989** | **0.991** | 643 / 655 |

"Blind" means the first run, before any rule was changed in response. The errors each round found led to targeted rule fixes, so the current numbers are no longer blind for those sentences, and in round 2 the 49 older rules had already been tuned on round 1. Two round-2 labels were corrected afterwards, because a subsumption added later hides the ～と they listed.

The evaluation is deterministic: `./mvnw test -Dtest=EvaluationTest` writes per-rule counts and every error to `target/evaluation-report.md`, and it fails if precision or recall drops below 0.98. The historical runs were re-run from clean checkouts of the development history and reproduced exactly; the current numbers reproduce from this repository with the command above.

The 12 remaining errors on the regression set are limits of token-level matching:
- **5 false positives:** 念のため ('just in case') read as ～ために, ところ meaning 'place', お世話になる read as 'become', 有名 after a place marked with で, and 診せた, which the dictionary splits like a causative.
- **7 misses:** 見え方 with its verb stem tagged as a noun, a conditional と after れる tagged as a case particle, two ～に行く matches, のに split into two tokens, 吹き出す excluded as ambiguous, and 魚 tagged as a suffix.

#### Held-out sets for the 142 new rules

The held-out sets are in `src/test/resources/evaluation/heldout/batch1..9.json`. Before implementation, each rule got a spec card with its ID, pattern, meaning, required forms and near-misses, but no matching steps or examples. A separate OpenAI Codex model generated sentences from those cards alone in an empty directory outside the repository. Another Codex model reviewed naturalness and labels without seeing the rules. Disagreements were settled by hand, every sentence was checked against the rule examples and the regression set for repeats, and each set was frozen with a SHA-256 hash before implementation. Implementers never saw the sets; each was committed only after its first scored run. The sets are **model-generated, model-reviewed**, with no native-speaker review.

The table records the first run on a pre-registered held-out set for each batch:

| Batch | Rules | Sentences | Precision | Recall |
|---|---|---|---|---|
| 1 | 10 | 114 | 1.000 | 0.955 |
| 2 | 20 | 196 | 1.000 | 1.000 |
| 3 | 21 | 198 | 1.000 | 0.978 |
| 4 | 19 | 176 | 1.000 | 0.911 |
| 5 | 19 | 175 | 1.000 | 0.949 |
| 6 | 19 | 181 | 1.000 | 0.921 |
| 7 | 21 | 198 | 0.938 | 0.938 |
| 8 | 9 | 139 | 1.000 | 0.866 |
| 9 | 4 | 66 | 1.000 | 0.969 |

Batch 2 had 196 sentences at its first run; one was removed afterwards because it repeated a regression-set sentence, which leaves 195 (and 1,442 in total). Batch 4 recall was 0.923 after one label error was corrected. All six batch 7 false positives came from ambiguous rules that were then restricted. The combined first run covered 1,443 sentences and scored **0.991 precision / 0.944 recall / 0.967 F1**. The current result, after fixes and therefore **not blind**, is **1.000 precision / 0.975 recall / 0.987 F1**, with 1,425 of 1,442 sentences exact. `ExpansionEvaluationTest` reports these held-out results without a floor.

A spot check on 120 new natural sentences and 253 plain-prose sentences found no notable false positives from the new rules.

## Performance

Measured on one laptop (Intel Core i5-12450H, 12 threads) with JDK 21, as the median of 100 runs after JVM warm-up. The dense input is 9,990 characters built by packing example sentences together, far denser in grammar than real prose: 6,291 tokens and 963 matches. The two prose inputs are about 9,000 characters of ordinary noun-heavy and verb-heavy text with few grammar points.

| Input | Tokenize (Kuromoji) | Match (341 rules) | Match p90 |
|---|---|---|---|
| Dense (9,990 chars) | 9.4 ms | 13.2 ms | 14.7 ms |
| Noun-heavy prose (8,916 chars) | 7.2 ms | 5.9 ms | 6.7 ms |
| Verb-heavy prose (9,260 chars) | 6.9 ms | 11.2 ms | 12.7 ms |

Without the two prefilters, the same 341 rules took over three times as long on the same machine and load. A worst case of 10,000 characters repeating one pattern (5,000 matches) matches in about 14 ms. Your numbers will vary with the machine and its load.

## Limitations

- **JLPT levels are unofficial.** The JLPT hasn't published official grammar lists since 2010, so the levels follow commonly used N5–N3 study lists.
- **Split patterns are bounded.** A gap spans at most a few tokens (set per rule, at most 8) and stops at punctuation, so a pattern spread over a long clause, or across a comma the rule doesn't expect, is missed.
- **Some N3 checklist forms are restricted.** ～たものだ is marked only with a recollection cue such as 昔, よく, 子供のころ, 時代 or たび; without one, it has the same tokens as 'a thing that was ~'. ～に慣れる matches kanji 慣れる only, because kana なれる tokenizes like the potential of なる ('can become'). Kanji 却って is matched, but kana かえって is not marked after a destination such as 家に, へ or まで, or before a motion verb such as きた, where it can be 帰って.
- **Some readings can't be told apart from tokens alone.** The card says so instead of guessing, but a few cases still produce false positives (see [Evaluation](#evaluation)). Some fused compounds use reviewed word lists, so unlisted words can be missed. Tokenization and ambiguity also cause misses, including 吹き出す and 見え方.
- **Bare なきゃ / なくちゃ is only matched at the end of a sentence.** Mid-sentence, the same form usually means 'if (you) don't'.
- **Inline furigana breaks tokenization.** Text copied with readings mixed in (気象庁きしょうちょう) confuses the tokenizer, so copy it without furigana.

## Roadmap

- N2 patterns
- Furigana from token readings
- Click a word to look it up in JMdict
- A sentence miner for subtitle files (.srt) that finds sentences with exactly one unknown word
- Export the patterns found to Anki

## Acknowledgements

- [Kuromoji](https://github.com/atilika/kuromoji) by Atilika (Apache License 2.0), with its bundled IPADIC dictionary, does the morphological analysis.
- The N5–N3 coverage checklist is the grammar lists of the Wikibooks [JLPT Guide](https://en.wikibooks.org/wiki/JLPT_Guide) (compiled by Jonathan Waller, CC BY-SA 4.0). Only pattern names and levels were used as a checklist; no text from it is included here.
- The rules' examples and explanations were written for this project. The evaluation sentences were drafted and, for the held-out sets, reviewed by OpenAI Codex models, as described under [Evaluation](#evaluation).

## License

[MIT](LICENSE) © 2026 Akshat. Kuromoji and its bundled IPADIC dictionary are distributed under their own licenses.
