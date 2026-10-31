package com.bookinpass.backend.staff;

import com.bookinpass.backend.exception.StaffException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;

/**
 * Garde de {@code /staff/**} :
 * <ol>
 *   <li>point d'accès sans {@link StaffAccess} : refusé (fermé par défaut) ;</li>
 *   <li>écriture : en-tête {@code Origin} parmi les origines du back-office (protection CSRF, avec le cookie SameSite) ;</li>
 *   <li>session du cookie valable, mot de passe définitif, rôle admis.</li>
 * </ol>
 * Le membre du personnel connecté est ensuite injecté en paramètre ({@link StaffPrincipal}).
 */
@RequiredArgsConstructor
class StaffInterceptor implements HandlerInterceptor {

    static final String PRINCIPAL = StaffInterceptor.class.getName() + ".principal";
    static final String TOKEN = StaffInterceptor.class.getName() + ".token";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final StaffAuthService auth;
    private final StaffProperties properties;

    private static StaffException forbidden() {
        return new StaffException(HttpStatus.FORBIDDEN, "forbidden", "Your role does not allow this.");
    }

    @Override
    @NullMarked
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        StaffAccess access = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), StaffAccess.class);
        if (access == null) {
            access = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), StaffAccess.class);
        }
        if (access == null) {
            throw forbidden();
        }
        if (!SAFE_METHODS.contains(request.getMethod()) && !properties.origins().contains(request.getHeader("Origin"))) {
            throw new StaffException(HttpStatus.FORBIDDEN, "origin-not-allowed", "This request must come from the back-office.");
        }
        if (access.anonymous()) {
            return true;
        }
        String token = StaffCookies.read(request, properties.cookieName());
        StaffPrincipal staff = auth.authenticate(token)
                .orElseThrow(() -> new StaffException(HttpStatus.UNAUTHORIZED, "not-signed-in", "Sign in to the back-office."));
        if (staff.mustChangePassword() && !access.beforePasswordChange()) {
            throw new StaffException(HttpStatus.FORBIDDEN, "password-change-required", "Choose a new password first.");
        }
        if (Arrays.stream(access.value()).noneMatch(role -> role == staff.role())) {
            throw forbidden();
        }
        request.setAttribute(PRINCIPAL, staff);
        request.setAttribute(TOKEN, token);
        return true;
    }
}
