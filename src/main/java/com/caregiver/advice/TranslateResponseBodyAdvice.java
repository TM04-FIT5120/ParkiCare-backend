package com.caregiver.advice;

import com.caregiver.service.TranslationService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TranslateResponseBodyAdvice.class);

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
        String path = request.getRequestURI();
        if (path != null) {
            if (path.startsWith("/api/foods")) {
                return false;
            }
            if (path.startsWith("/api/recipe/history")) {
                return false;
            }
        }
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

        if (!translationService.isTranslationAvailable()) {
            return body;
        }

        try {
            translationService.translateObjectToTargetLanguage(body, lang);
        } catch (Exception ex) {
            log.warn("Response translation skipped: {}", ex.getMessage());
        }

        return body;
    }
}