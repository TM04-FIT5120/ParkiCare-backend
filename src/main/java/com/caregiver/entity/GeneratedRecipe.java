package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "generated_recipes")
public class GeneratedRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "input_foods", columnDefinition = "TEXT")
    private String inputFoods;

    @Column(name = "recipe_title")
    private String recipeTitle;

    @Column(name = "ingredients", columnDefinition = "TEXT")
    private String ingredients;

    @Column(name = "steps", columnDefinition = "TEXT")
    private String steps;

    @Column(name = "suitable_desc", columnDefinition = "TEXT")
    private String suitableDesc;

    @Column(name = "unsuitable_desc", columnDefinition = "TEXT")
    private String unsuitableDesc;

    @Column(name = "health_tip", columnDefinition = "TEXT")
    private String healthTip;

    @Column(name = "high_protein_warning", columnDefinition = "TEXT")
    private String highProteinWarning;

    @Column(name = "reference_source", columnDefinition = "TEXT")
    private String referenceSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
