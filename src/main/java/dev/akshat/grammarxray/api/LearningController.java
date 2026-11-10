package dev.akshat.grammarxray.api;

import dev.akshat.grammarxray.learning.LearningCatalog;
import dev.akshat.grammarxray.learning.LearningKind;
import dev.akshat.grammarxray.learning.LearningSummary;
import dev.akshat.grammarxray.learning.Lesson;
import dev.akshat.grammarxray.pattern.Level;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningCatalog catalog;

    public LearningController(LearningCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/summary")
    public LearningSummary summary() {
        return catalog.summary();
    }

    @GetMapping("/{kind}/lessons")
    public List<Lesson> lessons(@PathVariable String kind, @RequestParam(required = false) Level level) {
        LearningKind learningKind = LearningKind.fromPath(kind)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown learning kind: " + kind));
        return catalog.lessons(learningKind, level);
    }
}
