package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "food_nutrition")
public class FoodNutrition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Food name
    @Column(name = "food_name", nullable = false)
    private String foodName;

    // Food category
    @Column(name = "category", nullable = false)
    private String category;

    // Safety status: Recommended / Avoid / Caution
    @Column(name = "safety_status", nullable = false)
    private String safetyStatus;

    // Serving unit
    @Column(name = "measure", nullable = false)
    private String measure;

    // Serving weight in grams
    @Column(name = "grams", nullable = false)
    private BigDecimal grams;

    // Calories per serving
    @Column(name = "calories", nullable = false)
    private Integer calories;

    // Protein per 100g
    @Column(name = "protein_100g", nullable = false)
    private BigDecimal protein100g;

    // Saturated fat per 100g
    @Column(name = "saturated_fats_100g", nullable = false)
    private BigDecimal saturatedFats100g;

    // Total fat per 100g
    @Column(name = "fat_100g", nullable = false)
    private BigDecimal fat100g;

    // Dietary fiber per 100g
    @Column(name = "fiber_100g", nullable = false)
    private BigDecimal fiber100g;

    // Carbohydrates per 100g
    @Column(name = "carbs_100g", nullable = false)
    private BigDecimal carbs100g;

    // Health guidance for Parkinson patients
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    // Authoritative nutrition data source
    @Column(name = "source")
    private String source;

    // Static image path for front-end
    @Column(name = "image_url")
    private String imageUrl;
}
