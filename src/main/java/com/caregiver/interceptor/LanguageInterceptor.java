package com.caregiver.interceptor;

import com.caregiver.context.LanguageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LanguageInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String language = request.getHeader("Accept-Language");

        if (language == null || language.isBlank()) {
            language = "en-US";
        }

        LanguageContext.setLanguage(language);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        LanguageContext.clear();
    }
}