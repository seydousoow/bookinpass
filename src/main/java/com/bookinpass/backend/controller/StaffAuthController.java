package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.StaffLoginRequest;
import com.bookinpass.backend.dto.request.StaffPasswordRequest;
import com.bookinpass.backend.dto.response.StaffUserResponse;
import com.bookinpass.backend.staff.StaffAccess;
import com.bookinpass.backend.staff.StaffAuthService;
import com.bookinpass.backend.staff.StaffPrincipal;
import com.bookinpass.backend.staff.StaffSessions;
import com.bookinpass.backend.staff.StaffUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connexion du personnel : cookie de session HttpOnly, posé à la connexion et effacé à la déconnexion. */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAFF)
@RegisterReflection(classes = {StaffLoginRequest.class, StaffPasswordRequest.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "staff/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffAuthController {

    private final StaffAuthService auth;
    private final StaffUserService users;
    private final StaffSessions sessions;

    @PostMapping(path = "login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @StaffAccess(anonymous = true)
    @Operation(summary = "Se connecter", description = "Ouvre une session de 12 h (cookie HttpOnly). Cinq échecs de suite bloquent le compte 15 minutes.")
    @ApiResponse(responseCode = "401", description = "Identifiant ou mot de passe faux (`invalid-credentials`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Compte bloqué (`account-locked`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public StaffUserResponse login(@Valid @RequestBody StaffLoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        StaffAuthService.Login login = auth.login(request.username(), request.password());
        sessions.open(http, response, login.token());
        return users.me(login.staff());
    }

    @PostMapping("logout")
    @StaffAccess(beforePasswordChange = true)
    @Operation(summary = "Se déconnecter")
    public ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response) {
        auth.logout(sessions.token(http));
        sessions.close(http, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("me")
    @StaffAccess(beforePasswordChange = true)
    @Operation(summary = "Mon compte", description = "`must_change_password` : seul le changement de mot de passe est ouvert.")
    @ApiResponse(responseCode = "401", description = "Non connecté (`not-signed-in`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public StaffUserResponse me(StaffPrincipal staff) {
        return users.me(staff);
    }

    @PostMapping(path = "password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @StaffAccess(beforePasswordChange = true)
    @Operation(summary = "Changer mon mot de passe", description = "10 caractères au moins, une lettre et un chiffre. Les autres sessions sont fermées.")
    @ApiResponse(responseCode = "400", description = "Mot de passe actuel faux (`wrong-password`), trop faible (`weak-password`) ou inchangé (`same-password`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public StaffUserResponse password(StaffPrincipal staff, @Valid @RequestBody StaffPasswordRequest request, HttpServletRequest http) {
        return users.me(auth.changePassword(staff, sessions.token(http), request.currentPassword(), request.newPassword()));
    }
}
