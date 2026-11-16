package com.bookinpass.backend.config.ids;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * {@code @PathVariable @PublicId Integer id} ou {@code @RequestParam @PublicId Integer …} : l'identifiant public reçu
 * devient la clé technique. Placé avant les résolveurs de Spring ({@link PublicIdConfig}) : leur conversion retomberait
 * sur l'éditeur des entiers et accepterait une clé technique en clair. Invalide : 400.
 */
class PublicIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(PublicId.class)
                && (parameter.hasParameterAnnotation(PathVariable.class) || parameter.hasParameterAnnotation(RequestParam.class));
    }

    @Override
    public @Nullable Object resolveArgument(@NonNull MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                            @NonNull NativeWebRequest request, @Nullable WebDataBinderFactory binderFactory) {
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        String raw;
        String name;
        if (path != null) {
            name = StringUtils.hasText(path.name()) ? path.name() : parameter.getParameterName();
            @SuppressWarnings("unchecked")
            Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                    RequestAttributes.SCOPE_REQUEST);
            raw = variables == null ? null : variables.get(name);
        } else {
            RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
            name = param != null && StringUtils.hasText(param.name()) ? param.name() : parameter.getParameterName();
            raw = request.getParameter(name);
        }
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw;
        return PublicIds.decode(value).orElseThrow(() -> new MethodArgumentTypeMismatchException(value, Integer.class, name, parameter,
                new IllegalArgumentException("Invalid identifier.")));
    }
}
