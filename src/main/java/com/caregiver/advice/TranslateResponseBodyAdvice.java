package com.caregiver.advice;

import com.caregiver.service.TranslationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

@RestControllerAdvice
public class TranslateResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final TranslationService translationService;
    private final HttpServletRequest request;

    public TranslateResponseBodyAdvice(TranslationService translationService,
                                       HttpServletRequest request) {
        this.translationService = translationService;
        this.request = request;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest serverHttpRequest,
                                  ServerHttpResponse serverHttpResponse) {

        if (body == null) {
            return body;
        }

        // 跳过错误响应、Map 响应，避免反射 HashMap / LinkedHashMap
        if (body instanceof Map<?, ?>) {
            return body;
        }

        String lang = request.getHeader("Accept-Language");

        if (lang == null || lang.toLowerCase().startsWith("en")) {
            return body;
        }

        translationService.translateObjectToTargetLanguage(body, lang);

        return body;
    }
}