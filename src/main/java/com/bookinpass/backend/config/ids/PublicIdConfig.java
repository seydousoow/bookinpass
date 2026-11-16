package com.bookinpass.backend.config.ids;

import org.jspecify.annotations.NonNull;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Identifiants publics dans les URL : {@link PublicIdArgumentResolver} en tête des résolveurs (avant ceux de
 * {@code @PathVariable} et {@code @RequestParam}). {@link WebMvcConfigurer} : chargée aussi par {@code @WebMvcTest}. Jackson instancie ses (dé)sérialiseurs par réflexion (image native).
 */
@Configuration(proxyBeanMethods = false)
@RegisterReflection(classes = {PublicIdSerializer.class, PublicIdDeserializer.class}, memberCategories = MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
class PublicIdConfig implements WebMvcConfigurer {

    @Bean
    static BeanPostProcessor publicIdArgumentResolverFirst() {
        return new BeanPostProcessor() {
            @Override
            public @NonNull Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String name) {
                if (bean instanceof RequestMappingHandlerAdapter adapter && adapter.getArgumentResolvers() != null) {
                    List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(adapter.getArgumentResolvers());
                    resolvers.addFirst(new PublicIdArgumentResolver());
                    adapter.setArgumentResolvers(resolvers);
                }
                return bean;
            }
        };
    }
}
