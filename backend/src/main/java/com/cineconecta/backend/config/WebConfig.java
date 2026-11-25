package com.cineconecta.backend.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.cineconecta.backend.security.CurrentProfileArgumentResolver;
import com.cineconecta.backend.service.ReferenceValidationService;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ReferenceValidationService references;

    public WebConfig(ReferenceValidationService references) {
        this.references = references;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentProfileArgumentResolver(references));
    }
}
