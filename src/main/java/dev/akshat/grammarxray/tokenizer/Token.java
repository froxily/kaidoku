package dev.akshat.grammarxray.tokenizer;

public record Token(String surface, String baseForm, String pos1, String pos2, String pos3,
                    String conjType, String conjForm, String reading, int start, int end) {
}
