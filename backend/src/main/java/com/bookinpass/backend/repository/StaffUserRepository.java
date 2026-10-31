package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.entity.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, Integer> {

    Optional<StaffUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(StaffRole role);

    @Query("select u from StaffUser u left join fetch u.station s left join fetch s.city order by u.fullName")
    List<StaffUser> findAllWithStation();
}
