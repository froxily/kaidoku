package dev.akshat.grammarxray.pattern;

import java.util.List;

public record Rule(String id, String pattern, Level level, String meaning, String explanation,
                   List<String> subsumes, List<Step> steps, List<Step> followedBy, List<List<Step>> notPrecededBy,
                   List<String> examples, List<String> counterExamples) {

    public Rule {
        subsumes = subsumes == null ? List.of() : List.copyOf(subsumes);
        followedBy = followedBy == null ? List.of() : List.copyOf(followedBy);
        notPrecededBy = notPrecededBy == null ? List.of() : notPrecededBy.stream().map(List::copyOf).toList();
    }
}
