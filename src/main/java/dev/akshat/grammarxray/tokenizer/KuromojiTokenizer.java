package dev.akshat.grammarxray.tokenizer;

import com.atilika.kuromoji.ipadic.Tokenizer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KuromojiTokenizer implements JapaneseTokenizer {

    private final Tokenizer tokenizer = new Tokenizer();

    @Override
    public List<Token> tokenize(String text) {
        return tokenizer.tokenize(text).stream().map(KuromojiTokenizer::toToken).toList();
    }

    private static Token toToken(com.atilika.kuromoji.ipadic.Token token) {
        int start = token.getPosition();
        return new Token(
                token.getSurface(),
                token.getBaseForm(),
                token.getPartOfSpeechLevel1(),
                token.getPartOfSpeechLevel2(),
                token.getPartOfSpeechLevel3(),
                token.getConjugationType(),
                token.getConjugationForm(),
                token.getReading(),
                start,
                start + token.getSurface().length());
    }
}
