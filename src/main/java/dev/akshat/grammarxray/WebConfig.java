package dev.akshat.grammarxray;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/grammar").setViewName("forward:/grammar.html");
        registry.addViewController("/about").setViewName("forward:/about.html");
        for (String root : List.of("/kanji", "/vocab")) {
            registry.addViewController(root).setViewName("forward:/learn.html");
            registry.addViewController(root + "/**").setViewName("forward:/learn.html");
        }
    }
}
