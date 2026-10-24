package dev.akshat.grammarxray.api;

import dev.akshat.grammarxray.pattern.Level;
import dev.akshat.grammarxray.pattern.Match;
import dev.akshat.grammarxray.tokenizer.Token;

import java.util.List;

public record AnalyzeResponse(List<TokenView> tokens, List<MatchView> matches) {

    public static AnalyzeResponse of(List<Token> tokens, List<Match> matches) {
        return new AnalyzeResponse(
                tokens.stream().map(TokenView::of).toList(),
                matches.stream().map(MatchView::of).toList());
    }

    public record TokenView(String surface, String baseForm, String pos, String reading, int start, int end) {

        static TokenView of(Token token) {
            return new TokenView(token.surface(), token.baseForm(), token.pos1(), token.reading(), token.start(), token.end());
        }
    }

    public record MatchView(String ruleId, String pattern, Level level, String meaning, String explanation,
                            int start, int end, String text) {

        static MatchView of(Match match) {
            var rule = match.rule();
            return new MatchView(rule.id(), rule.pattern(), rule.level(), rule.meaning(), rule.explanation(),
                    match.start(), match.end(), match.text());
        }
    }
}
