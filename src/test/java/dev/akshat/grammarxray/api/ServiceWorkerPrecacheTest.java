package dev.akshat.grammarxray.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ServiceWorkerPrecacheTest {

    private static final Path STATIC_ROOT = Path.of("src/main/resources/static");

    @Autowired
    private MockMvcTester mvc;

    @Test
    void precacheContainsEveryShellFile() throws IOException {
        Set<String> expected;
        try (var paths = Files.walk(STATIC_ROOT)) {
            expected = paths.filter(Files::isRegularFile)
                    .map(path -> "/" + STATIC_ROOT.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> !path.startsWith("/learning/") && !path.startsWith("/intro/")
                            && !path.equals("/sw.js"))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        expected.add("/");

        String worker = Files.readString(STATIC_ROOT.resolve("sw.js"));
        var array = Pattern.compile("\\bconst\\s+PRECACHE\\s*=\\s*\\[([^\\]]*)\\]", Pattern.DOTALL).matcher(worker);
        assertThat(array.find()).as("sw.js declares a literal PRECACHE array").isTrue();
        var strings = Pattern.compile("['\"]([^'\"]+)['\"]").matcher(array.group(1));
        Set<String> actual = new TreeSet<>();
        while (strings.find()) {
            assertThat(actual.add(strings.group(1))).as("unique precache entry %s", strings.group(1)).isTrue();
        }

        assertThat(actual).contains("/", "/index.html").isEqualTo(expected);
    }

    @Test
    void servesTheWebManifestWithItsRegisteredMimeType() {
        assertThat(mvc.get().uri("/manifest.webmanifest")).hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.parseMediaType("application/manifest+json"));
    }

    @Test
    void servesTheServiceWorkerAsJavaScript() {
        assertThat(mvc.get().uri("/sw.js")).hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.parseMediaType("text/javascript"));
    }
}
