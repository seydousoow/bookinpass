package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.StaffSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface StaffSessionRepository extends JpaRepository<StaffSession, String> {

    /**
     * Session et compte en une requête : chaque appel du back-office la lit.
     */
    @Query("select s from StaffSession s join fetch s.staff u left join fetch u.station where s.tokenHash = :hash")
    Optional<StaffSession> findWithStaff(@Param("hash") String tokenHash);

    /**
     * Toutes les sessions d'un compte (désactivation, nouveau mot de passe).
     */
    @Modifying
    @Query("delete from StaffSession s where s.staff.id = :staffId")
    int deleteByStaffId(@Param("staffId") Integer staffId);

    /**
     * Sessions d'un compte sauf {@code keep} (celle qui vient de changer le mot de passe).
     */
    @Modifying
    @Query("delete from StaffSession s where s.staff.id = :staffId and s.tokenHash <> :keep")
    int deleteOthers(@Param("staffId") Integer staffId, @Param("keep") String keep);

    @Modifying
    @Query("delete from StaffSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
