package com.bookinpass.backend.config.ids;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicIdArgumentResolverTest {

    private final PublicIdArgumentResolver resolver = new PublicIdArgumentResolver();

    @SuppressWarnings("unused")
    static class Endpoints {
        void path(@PathVariable("id") @PublicId Integer id) {
        }

        void pathByParameterName(@PathVariable @PublicId Integer bookingId) {
        }

        void query(@RequestParam("ref") @PublicId Integer ref) {
        }

        void notPublic(@PathVariable("id") Integer id) {
        }

        void publicWithoutBinding(@PublicId Integer id) {
        }
    }

    /** As at runtime: a {@link HandlerMethod} parameter, with synthesized annotations and name discovery. */
    private static MethodParameter param(String method) {
        Method m = findMethod(method);
        return new HandlerMethod(new Endpoints(), m).getMethodParameters()[0];
    }

    private static Method findMethod(String name) {
        for (Method m : Endpoints.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static NativeWebRequest withPathVariables(Map<String, String> variables) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables);
        return new ServletWebRequest(request);
    }

    private static NativeWebRequest withQuery(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(name, value);
        return new ServletWebRequest(request);
    }

    private Object resolve(MethodParameter parameter, NativeWebRequest request) {
        return resolver.resolveArgument(parameter, null, request, null);
    }

    @Test
    void supportsOnlyPublicIdBoundToPathOrQuery() {
        assertThat(resolver.supportsParameter(param("path"))).isTrue();
        assertThat(resolver.supportsParameter(param("query"))).isTrue();
        assertThat(resolver.supportsParameter(param("notPublic"))).isFalse();
        assertThat(resolver.supportsParameter(param("publicWithoutBinding"))).isFalse();
    }

    @Test
    void aPathVariableIsDecoded() {
        Object id = resolve(param("path"), withPathVariables(Map.of("id", PublicIds.encode(42))));
        assertThat(id).isEqualTo(42);
    }

    @Test
    void aPathVariableFallsBackToTheParameterName() {
        Object id = resolve(param("pathByParameterName"), withPathVariables(Map.of("bookingId", PublicIds.encode(7))));
        assertThat(id).isEqualTo(7);
    }

    @Test
    void aRequestParamIsDecoded() {
        assertThat(resolve(param("query"), withQuery("ref", PublicIds.encode(99)))).isEqualTo(99);
    }

    @Test
    void aMissingOrBlankValueResolvesToNull() {
        assertThat(resolve(param("path"), withPathVariables(Map.of()))).isNull();
        assertThat(resolve(param("path"), new ServletWebRequest(new MockHttpServletRequest()))).isNull();
        assertThat(resolve(param("query"), new ServletWebRequest(new MockHttpServletRequest()))).isNull();
        assertThat(resolve(param("query"), withQuery("ref", "  "))).isNull();
    }

    @Test
    void aRawTechnicalKeyOrGarbageIsRejected() {
        assertThatThrownBy(() -> resolve(param("path"), withPathVariables(Map.of("id", "42"))))
                .isInstanceOfSatisfying(MethodArgumentTypeMismatchException.class, e -> {
                    assertThat(e.getName()).isEqualTo("id");
                    assertThat(e.getValue()).isEqualTo("42");
                    assertThat(e.getRequiredType()).isEqualTo(Integer.class);
                });
        assertThatThrownBy(() -> resolve(param("query"), withQuery("ref", "!!!!!!!!!!!!")))
                .isInstanceOf(MethodArgumentTypeMismatchException.class);
    }

    @Test
    void anUndeterminableNameResolvesToNull() {
        // No name in the annotation and none from the compiler (built without '-parameters').
        MethodParameter unnamed = new MethodParameter(findMethod("pathByParameterName"), 0) {
            @Override
            public String getParameterName() {
                return null;
            }
        };
        assertThat(resolve(unnamed, withPathVariables(Map.of("bookingId", PublicIds.encode(7))))).isNull();
    }
}
