package dev.akshat.grammarxray.api;

import dev.akshat.grammarxray.pattern.PatternMatcher;
import dev.akshat.grammarxray.tokenizer.JapaneseTokenizer;
import dev.akshat.grammarxray.tokenizer.Token;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final JapaneseTokenizer tokenizer;
    private final PatternMatcher matcher;

    public AnalyzeController(JapaneseTokenizer tokenizer, PatternMatcher matcher) {
        this.tokenizer = tokenizer;
        this.matcher = matcher;
    }

    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        List<Token> tokens = tokenizer.tokenize(request.text());
        return AnalyzeResponse.of(tokens, matcher.match(request.text(), tokens));
    }
}
