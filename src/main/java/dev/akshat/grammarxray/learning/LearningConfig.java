package dev.akshat.grammarxray.learning;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LearningConfig {

    @Bean
    public LearningCatalog learningCatalog() {
        return LearningCatalog.load("classpath:static/learning/");
    }
}
