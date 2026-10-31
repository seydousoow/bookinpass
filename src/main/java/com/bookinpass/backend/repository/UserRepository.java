package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByMatchKey(String matchKey);

    /** Un client Duffel déjà connu pour cet e-mail (Duffel n'en accepte qu'un par e-mail). */
    Optional<User> findFirstByEmailIgnoreCaseAndDuffelUserIdIsNotNull(String email);
}
