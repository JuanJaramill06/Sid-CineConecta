package com.cineconecta.backend.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.cineconecta.backend.exception.ForbiddenOperationException;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.service.ReferenceValidationService;

/** Resolves {@link CurrentProfile} parameters. Registered in WebConfig. */
public class CurrentProfileArgumentResolver implements HandlerMethodArgumentResolver {

    private final ReferenceValidationService references;

    public CurrentProfileArgumentResolver(ReferenceValidationService references) {
        this.references = references;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentProfile.class)
                && Profile.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Profile resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory)
            throws ServletRequestBindingException {
        String header = webRequest.getHeader(CurrentProfile.HEADER);
        if (header == null || header.isBlank()) {
            throw new MissingRequestHeaderException(CurrentProfile.HEADER, parameter);
        }
        int profileId;
        try {
            profileId = Integer.parseInt(header.trim());
        } catch (NumberFormatException e) {
            throw new ServletRequestBindingException(CurrentProfile.HEADER + " debe ser un número");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)
                || user.role() != Role.CLIENT) {
            throw new ForbiddenOperationException("Seleccionar un perfil requiere una cuenta de cliente");
        }
        return references.requireActiveProfile(user.accountId(), profileId);
    }
}
