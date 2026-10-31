package com.bookinpass.backend.staff;

import com.bookinpass.backend.entity.StaffSession;
import com.bookinpass.backend.entity.StaffUser;
import com.bookinpass.backend.exception.StaffException;
import com.bookinpass.backend.repository.StaffSessionRepository;
import com.bookinpass.backend.repository.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Connexion du personnel : mot de passe BCrypt, blocage après 5 échecs de suite, session à jeton aléatoire. Le cookie
 * porte le jeton ; la base n'en garde que le condensé SHA-256. Une session est relue (et le compte revérifié) à chaque
 * requête : désactiver un compte ou changer son mot de passe coupe ses sessions aussitôt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffAuthService {

    /**
     * 10 caractères au moins, une lettre et un chiffre.
     */
    static final int MIN_PASSWORD_LENGTH = 10;
    /**
     * Dernière activité enregistrée au plus toutes les 5 minutes (pas une écriture par requête).
     */
    private static final Duration SEEN_RESOLUTION = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(12);
    /**
     * Comparé à un identifiant inconnu : même durée de réponse qu'un mauvais mot de passe.
     */
    private static final String DUMMY_HASH = ENCODER.encode("not-a-real-password-0");

    private final StaffUserRepository users;
    private final StaffSessionRepository sessions;
    private final StaffProperties properties;
    private final Clock clock;

    static String encode(String password) {
        return ENCODER.encode(password);
    }

    static void requireStrong(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || !password.matches(".*\\p{L}.*") || !password.matches(".*\\d.*")) {
            throw new StaffException(HttpStatus.BAD_REQUEST, "weak-password",
                    "The password needs at least " + MIN_PASSWORD_LENGTH + " characters, with a letter and a digit.");
        }
    }

    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static StaffException invalidCredentials() {
        return new StaffException(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Wrong username or password.");
    }

    @Transactional(noRollbackFor = StaffException.class)
    public Login login(String username, String password) {
        Instant now = clock.instant();
        Optional<StaffUser> found = users.findByUsername(normalize(username));
        if (found.isEmpty()) {
            ENCODER.matches(password, DUMMY_HASH);
            throw invalidCredentials();
        }
        StaffUser user = found.get();
        if (user.isLocked(now)) {
            throw new StaffException(HttpStatus.TOO_MANY_REQUESTS, "account-locked", "Too many failed attempts; try again later.");
        }
        if (!ENCODER.matches(password, user.getPasswordHash())) {
            user.loginFailed(now);
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            throw invalidCredentials();
        }
        user.loginSucceeded(now);
        String token = newToken();
        sessions.save(new StaffSession(hash(token), user, now, now.plus(properties.sessionTtl())));
        log.info("Staff {} signed in", user.getUsername());
        return new Login(token, StaffPrincipal.of(user));
    }

    /**
     * Compte de la session, s'il est actif et la session valable ; dernière activité notée.
     */
    @Transactional
    public Optional<StaffPrincipal> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findWithStaff(hash(token))
                .filter(session -> session.getExpiresAt().isAfter(now) && session.getStaff().isActive())
                .map(session -> {
                    if (session.getLastSeenAt().plus(SEEN_RESOLUTION).isBefore(now)) {
                        session.seen(now);
                    }
                    return StaffPrincipal.of(session.getStaff());
                });
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteById(hash(token));
        }
    }

    /**
     * Nouveau mot de passe choisi par la personne : les autres sessions sont fermées, celle-ci reste ouverte.
     */
    @Transactional
    public StaffPrincipal changePassword(StaffPrincipal staff, String token, String current, String next) {
        StaffUser user = users.findById(staff.id()).orElseThrow();
        if (!ENCODER.matches(current, user.getPasswordHash())) {
            throw new StaffException(HttpStatus.BAD_REQUEST, "wrong-password", "The current password is wrong.");
        }
        requireStrong(next);
        if (ENCODER.matches(next, user.getPasswordHash())) {
            throw new StaffException(HttpStatus.BAD_REQUEST, "same-password", "Choose a password different from the current one.");
        }
        user.changePassword(ENCODER.encode(next));
        sessions.deleteOthers(user.getId(), hash(token));
        return StaffPrincipal.of(user);
    }

    /**
     * Toutes les sessions d'un compte (désactivé, mot de passe réinitialisé).
     */
    @Transactional
    public void closeSessions(Integer staffId) {
        sessions.deleteByStaffId(staffId);
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void purgeExpiredSessions() {
        sessions.deleteExpired(clock.instant());
    }

    /**
     * Session ouverte : jeton du cookie (jamais stocké en clair) et compte connecté.
     */
    public record Login(String token, StaffPrincipal staff) {
    }
}
