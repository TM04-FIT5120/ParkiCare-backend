package com.caregiver.advice;

import com.caregiver.service.TranslationService;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@Component
@RestControllerAdvice
public class TranslateRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private final TranslationService translationService;

    public TranslateRequestBodyAdvice(TranslationService translationService) {
        this.translationService = translationService;
    }

    @Override
    public boolean supports(MethodParameter methodParameter,
                            Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return translationService.isTranslationAvailable();
    }

    @Override
    public Object afterBodyRead(Object body,
                                HttpInputMessage inputMessage,
                                MethodParameter parameter,
                                Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {

        if (body == null) {
            return body;
        }

        String lang = inputMessage.getHeaders().getFirst("Accept-Language");

        if (lang == null || lang.isBlank() || lang.toLowerCase().startsWith("en")) {
            return body;
        }

        try {
            translationService.translateObjectToEnglish(body, lang);
        } catch (Exception e) {
            // Non-fatal — store English as-is when translation fails.
        }

        return body;
    }
}
