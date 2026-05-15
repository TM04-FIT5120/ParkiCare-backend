package com.caregiver.repository;

import com.caregiver.entity.GeneratedRecipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedRecipeRepository extends JpaRepository<GeneratedRecipe, Long> {
    List<GeneratedRecipe> findByCaregiverIdOrderByCreatedAtDesc(Long caregiverId);
}
