package dev.akshat.grammarxray.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

    @Autowired
    private MockMvcTester mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/grammar", "/kanji/library", "/api/patterns", "/api/learning/summary",
            "/learning/kanji.json", "/api/learning/nope/lessons"})
    void everyResponseCarriesTheSecurityHeaders(String path) {
        var result = mvc.get().uri(path).exchange();

        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY)
                .contains("default-src 'self'", "object-src 'none'", "frame-ancestors 'none'")
                .doesNotContain("unsafe-inline", "unsafe-eval");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(result.getResponse().getHeader("Permissions-Policy")).contains("camera=()");
    }
}
