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

        // 只处理 Controller 里面带 @RequestBody 的入参
        return true;
    }

    @Override
    public Object afterBodyRead(Object body,
                                HttpInputMessage inputMessage,
                                MethodParameter parameter,
                                Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {

        System.out.println("========== RequestBodyAdvice triggered ==========");

        if (body == null) {
            System.out.println("Request body is null");
            return body;
        }

        String lang = inputMessage.getHeaders().getFirst("Accept-Language");

        System.out.println("Accept-Language = " + lang);
        System.out.println("Body class = " + body.getClass().getName());
        System.out.println("Body before translate = " + body);

        if (lang == null || lang.isBlank() || lang.toLowerCase().startsWith("en")) {
            System.out.println("Skip request translation");
            return body;
        }

        try {
            translationService.translateObjectToEnglish(body, lang);
            System.out.println("Body after translate = " + body);
        } catch (Exception e) {
            System.out.println("Request translation failed: " + e.getMessage());
            e.printStackTrace();
        }

        return body;
    }
}