package dev.akshat.grammarxray.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestBodySizeLimitTest {

    private final RequestBodySizeLimit limit = new RequestBodySizeLimit();

    @Test
    void rejectsOversizedBodyWithoutContentLength() {
        HttpInputMessage chunked = message(new byte[RequestBodySizeLimit.MAX_BODY_BYTES + 1]);

        assertThatThrownBy(() -> limit.beforeBodyRead(chunked, null, String.class, null))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
    }

    @Test
    void passesBodyWithinLimitThroughUnchanged() throws Exception {
        byte[] body = "{\"text\": \"日本語\"}".getBytes();

        HttpInputMessage result = limit.beforeBodyRead(message(body), null, String.class, null);

        assertThat(result.getBody().readAllBytes()).isEqualTo(body);
    }

    private static HttpInputMessage message(byte[] body) {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
    }
}
