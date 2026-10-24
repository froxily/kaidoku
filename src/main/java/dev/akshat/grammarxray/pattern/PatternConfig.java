package dev.akshat.grammarxray.pattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PatternConfig {

    @Bean
    public RuleRepository ruleRepository() {
        return RuleRepository.load("classpath:patterns/*.json");
    }

    @Bean
    public PatternMatcher patternMatcher(RuleRepository ruleRepository) {
        return new PatternMatcher(ruleRepository.all());
    }
}
