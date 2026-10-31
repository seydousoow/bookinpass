package com.bookinpass.backend.staff;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Garde de {@code /staff/**} et injection du membre du personnel connecté dans les contrôleurs. Garde construite ici
 * plutôt que composant : les tranches de test MVC (contrôleurs publics) n'ont pas les services du personnel.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class StaffWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<StaffAuthService> auth;
    private final ObjectProvider<StaffProperties> properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        StaffAuthService service = auth.getIfAvailable();
        StaffProperties staff = properties.getIfAvailable();
        if (service != null && staff != null) {
            registry.addInterceptor(new StaffInterceptor(service, staff)).addPathPatterns("/staff/**");
        }
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == StaffPrincipal.class;
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                          WebDataBinderFactory binderFactory) {
                return request.getAttribute(StaffInterceptor.PRINCIPAL, RequestAttributes.SCOPE_REQUEST);
            }
        });
    }
}
