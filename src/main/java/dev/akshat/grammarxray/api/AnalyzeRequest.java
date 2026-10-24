package dev.akshat.grammarxray.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(@NotBlank @Size(max = AnalyzeRequest.MAX_TEXT_LENGTH) String text) {

    public static final int MAX_TEXT_LENGTH = 10_000;
}
