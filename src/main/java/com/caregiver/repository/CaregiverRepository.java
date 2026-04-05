package com.caregiver.repository;

import com.caregiver.entity.Caregiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CaregiverRepository extends JpaRepository<Caregiver, Long> {

    Optional<Caregiver> findByUniqueId(String uniqueId);

    boolean existsByNickname(String nickname);

    boolean existsByUniqueId(String uniqueId);

    @Query(value = "SELECT MAX(CAST(unique_id AS UNSIGNED)) FROM caregiver", nativeQuery = true)
    Long findMaxUniqueIdNumber();
}