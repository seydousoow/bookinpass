package com.bookinpass.backend.staff;

import com.bookinpass.backend.dto.request.StaffUserRequest;
import com.bookinpass.backend.dto.response.StaffStationResponse;
import com.bookinpass.backend.dto.response.StaffUserResponse;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.entity.StaffUser;
import com.bookinpass.backend.entity.TransportStation;
import com.bookinpass.backend.exception.StaffException;
import com.bookinpass.backend.repository.StaffUserRepository;
import com.bookinpass.backend.repository.TransportStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Comptes du personnel. Un manager ne gère que chefs de gare et contrôleurs ; personne ne change son propre rôle ni
 * ne se désactive (le dernier administrateur ne peut pas s'enfermer dehors).
 */
@Service
@RequiredArgsConstructor
public class StaffUserService {

    private final StaffUserRepository users;
    private final TransportStationRepository stations;
    private final StaffAuthService auth;

    /** Son propre compte (profil du back-office). */
    @Transactional(readOnly = true)
    public StaffUserResponse me(StaffPrincipal staff) {
        return response(users.findById(staff.id()).orElseThrow(StaffUserService::notFound));
    }

    @Transactional(readOnly = true)
    public List<StaffUserResponse> list(StaffPrincipal by) {
        return users.findAllWithStation().stream()
                .filter(user -> by.role() == StaffRole.ADMIN || !user.getRole().allStations())
                .map(StaffUserService::response)
                .toList();
    }

    @Transactional
    public StaffUserResponse create(StaffPrincipal by, StaffUserRequest request) {
        StaffRole role = StaffRole.of(request.role());
        requireManageable(by, role);
        String username = StaffAuthService.normalize(request.username());
        if (username.length() < 3) {
            throw invalid("username-required", "A username of at least 3 characters is required.");
        }
        if (users.existsByUsername(username)) {
            throw new StaffException(HttpStatus.CONFLICT, "username-taken", "This username is already used.");
        }
        StaffAuthService.requireStrong(request.temporaryPassword());
        StaffUser user = users.save(new StaffUser(username, StaffAuthService.encode(request.temporaryPassword()), request.fullName().trim(),
                request.phone(), role, station(role, request.stationId())));
        return response(user);
    }

    @Transactional
    public StaffUserResponse update(StaffPrincipal by, Integer id, StaffUserRequest request) {
        StaffUser user = users.findById(id).orElseThrow(StaffUserService::notFound);
        StaffRole role = StaffRole.of(request.role());
        requireManageable(by, user.getRole());
        requireManageable(by, role);
        boolean active = request.active() == null || request.active();
        if (Objects.equals(user.getId(), by.id()) && (role != user.getRole() || !active)) {
            throw invalid("own-account", "You cannot change your own role or deactivate yourself.");
        }
        boolean revoke = !active || role != user.getRole();
        user.update(request.fullName().trim(), request.phone(), role, station(role, request.stationId()), active);
        if (revoke) {
            auth.closeSessions(user.getId());
        }
        return response(user);
    }

    /** Mot de passe provisoire : sessions fermées, blocage levé, changement exigé à la connexion. */
    @Transactional
    public StaffUserResponse resetPassword(StaffPrincipal by, Integer id, String temporaryPassword) {
        StaffUser user = users.findById(id).orElseThrow(StaffUserService::notFound);
        requireManageable(by, user.getRole());
        StaffAuthService.requireStrong(temporaryPassword);
        user.resetPassword(StaffAuthService.encode(temporaryPassword));
        auth.closeSessions(user.getId());
        return response(user);
    }

    static StaffUserResponse response(StaffUser user) {
        return new StaffUserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getPhone(), user.getRole().dbValue(),
                user.getStation() == null ? null : station(user.getStation()), user.isActive(), user.isMustChangePassword(),
                user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString());
    }

    public static StaffStationResponse station(TransportStation station) {
        return new StaffStationResponse(station.getId(), station.getCode(), station.getName(), station.placeName(), station.getAddress(),
                station.getPhone());
    }

    private TransportStation station(StaffRole role, Integer stationId) {
        if (role.allStations()) {
            return null;
        }
        if (stationId == null) {
            throw invalid("station-required", "Station chiefs and inspectors work in a station.");
        }
        return stations.findById(stationId).orElseThrow(() -> invalid("station-not-found", "Unknown station."));
    }

    /** Admin : tous les rôles ; manager : chefs de gare et contrôleurs. */
    private static void requireManageable(StaffPrincipal by, StaffRole role) {
        if (by.role() != StaffRole.ADMIN && role.allStations()) {
            throw new StaffException(HttpStatus.FORBIDDEN, "forbidden", "Only an administrator manages administrators and managers.");
        }
    }

    private static StaffException notFound() {
        return new StaffException(HttpStatus.NOT_FOUND, "staff-not-found", "Unknown staff member.");
    }

    private static StaffException invalid(String code, String message) {
        return new StaffException(HttpStatus.BAD_REQUEST, code, message);
    }
}
