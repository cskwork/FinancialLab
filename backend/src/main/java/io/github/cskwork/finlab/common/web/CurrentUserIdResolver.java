package io.github.cskwork.finlab.common.web;

import io.github.cskwork.finlab.common.error.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public final class CurrentUserIdResolver implements HandlerMethodArgumentResolver {
    @Override public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && (parameter.getParameterType() == long.class || parameter.getParameterType() == Long.class);
    }
    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                             NativeWebRequest request, WebDataBinderFactory binderFactory) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try { return Long.parseLong(jwt.getSubject()); }
            catch (NumberFormatException ignored) { /* A malformed subject is not an application failure. */ }
        }
        throw new ApiException(401, "UNAUTHORIZED", "인증이 필요합니다");
    }
}
