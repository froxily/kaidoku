package dev.akshat.grammarxray.tokenizer;

import java.util.List;

public interface JapaneseTokenizer {

    List<Token> tokenize(String text);
}
