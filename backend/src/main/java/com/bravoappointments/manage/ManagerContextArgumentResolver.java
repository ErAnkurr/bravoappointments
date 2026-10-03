package com.bravoappointments.manage;

import com.bravoappointments.common.ApiException;
import com.bravoappointments.tenant.AppUser;
import com.bravoappointments.tenant.AppUserRepository;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controller methods declare a {@link ManagerContext} parameter. */
@Component
public class ManagerContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final AppUserRepository users;

    public ManagerContextArgumentResolver(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ManagerContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
        }
        AppUser user = users.findByAuthProviderId(token.getToken().getSubject())
                .orElseThrow(() -> ApiException.forbidden("NOT_ONBOARDED", "Finish setting up your business first"));
        return new ManagerContext(user.getId(), user.getTenantId(), user.getRole());
    }
}
