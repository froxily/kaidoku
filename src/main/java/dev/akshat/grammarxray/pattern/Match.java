package dev.akshat.grammarxray.pattern;

public record Match(Rule rule, int start, int end, String text) {
}
