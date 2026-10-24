package dev.akshat.grammarxray.api;

import dev.akshat.grammarxray.pattern.Level;
import dev.akshat.grammarxray.pattern.Rule;
import dev.akshat.grammarxray.pattern.RuleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PatternController {

    private final List<PatternView> patterns;

    public PatternController(RuleRepository rules) {
        this.patterns = rules.all().stream()
                .sorted(Comparator.comparing(Rule::level).thenComparing(Rule::id))
                .map(PatternView::of)
                .toList();
    }

    @GetMapping("/patterns")
    public List<PatternView> patterns(@RequestParam(required = false) Level level) {
        return level == null ? patterns : patterns.stream().filter(pattern -> pattern.level() == level).toList();
    }
}
