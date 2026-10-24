package dev.akshat.grammarxray.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

@ControllerAdvice
public class RequestBodySizeLimit extends RequestBodyAdviceAdapter {

    public static final int MAX_BODY_BYTES = 64 * 1024;

    @Override
    public boolean supports(MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage message, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        if (message.getHeaders().getContentLength() > MAX_BODY_BYTES) {
            throw tooLarge();
        }
        byte[] body = message.getBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw tooLarge();
        }
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return message.getHeaders();
            }
        };
    }

    private static ErrorResponseException tooLarge() {
        HttpStatus status = HttpStatus.CONTENT_TOO_LARGE;
        return new ErrorResponseException(status,
                ProblemDetail.forStatusAndDetail(status, "Request body must not exceed " + MAX_BODY_BYTES + " bytes"), null);
    }
}
