package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.StaffResetPasswordRequest;
import com.bookinpass.backend.dto.request.StaffUserRequest;
import com.bookinpass.backend.dto.response.StaffUserResponse;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.staff.StaffAccess;
import com.bookinpass.backend.staff.StaffPrincipal;
import com.bookinpass.backend.staff.StaffUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Comptes du personnel : administrateurs (tous) et managers (chefs de gare, contrôleurs).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAFF)
@StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER})
@RegisterReflection(classes = {StaffUserRequest.class, StaffResetPasswordRequest.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "staff/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffUserController {

    private final StaffUserService users;

    @GetMapping
    @Operation(summary = "Personnel", description = "Un manager ne voit que chefs de gare et contrôleurs.")
    public List<StaffUserResponse> list(StaffPrincipal staff) {
        return users.list(staff);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer un compte", description = "Mot de passe provisoire, à changer à la première connexion.")
    public StaffUserResponse create(StaffPrincipal staff, @Valid @RequestBody StaffUserRequest request) {
        return users.create(staff, request);
    }

    @PatchMapping(path = "{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier un compte", description = "Nom, téléphone, rôle, gare, actif. Désactivation ou changement de rôle : sessions fermées.")
    public StaffUserResponse update(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffUserRequest request) {
        return users.update(staff, id, request);
    }

    @PostMapping(path = "{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Donner un mot de passe provisoire", description = "Sessions fermées, blocage levé ; à changer à la connexion.")
    public StaffUserResponse resetPassword(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffResetPasswordRequest request) {
        return users.resetPassword(staff, id, request.temporaryPassword());
    }
}
