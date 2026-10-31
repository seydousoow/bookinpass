package com.bookinpass.backend.staff;

import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.entity.StaffUser;
import com.bookinpass.backend.repository.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Premier administrateur ({@code BOOTSTRAP_ADMIN_USERNAME} / {@code BOOTSTRAP_ADMIN_PASSWORD}), créé au démarrage tant
 * qu'il n'existe aucun administrateur. Mot de passe provisoire : à changer à la première connexion ; les variables
 * peuvent ensuite être retirées.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StaffBootstrap implements ApplicationRunner {

    private final StaffUserRepository users;
    private final StaffProperties properties;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        String username = StaffAuthService.normalize(properties.bootstrapAdminUsername());
        String password = properties.bootstrapAdminPassword();
        if (username.isEmpty() || password == null || password.isEmpty() || users.existsByRole(StaffRole.ADMIN)) {
            return;
        }
        StaffAuthService.requireStrong(password);
        users.save(new StaffUser(username, StaffAuthService.encode(password), "Administrateur", null, StaffRole.ADMIN, null));
        log.warn("First back-office administrator '{}' created: sign in and change the password", username);
    }
}
