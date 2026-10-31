package com.bookinpass.backend.staff;

import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.entity.StaffUser;

/**
 * Membre du personnel connecté, lu à chaque requête depuis sa session.
 *
 * @param stationId gare de travail ; {@code null} pour admin et manager (toutes les gares)
 */
public record StaffPrincipal(Integer id, String username, String fullName, StaffRole role, Integer stationId, boolean mustChangePassword) {

    static StaffPrincipal of(StaffUser user) {
        return new StaffPrincipal(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.getStation() == null ? null : user.getStation().getId(), user.isMustChangePassword());
    }

    public boolean allStations() {
        return role.allStations();
    }

    /** Gare visible : toutes pour admin et manager, sinon la sienne. */
    public boolean canSee(Integer station) {
        return allStations() || stationId != null && stationId.equals(station);
    }
}
