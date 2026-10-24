package dev.akshat.grammarxray.api;

import dev.akshat.grammarxray.tokenizer.JapaneseTokenizer;
import dev.akshat.grammarxray.tokenizer.Token;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TokenController {

    private final JapaneseTokenizer tokenizer;

    public TokenController(JapaneseTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @GetMapping("/tokens")
    public List<Token> tokens(@RequestParam @NotBlank @Size(max = AnalyzeRequest.MAX_TEXT_LENGTH) String text) {
        return tokenizer.tokenize(text);
    }
}
