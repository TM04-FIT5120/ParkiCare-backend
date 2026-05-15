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
    @Column(name = "foodname")
    private String foodName;

    // Normalized food name
    @Column(name = "normalized_foodname")
    private String normalizedFoodname;

    // Food category
    @Column(name = "category")
    private String category;

    // Safety status: Safe / Recommended / Avoid / Caution
    @Column(name = "safetystatus")
    private String safetyStatus;

    // Serving unit
    @Column(name = "measure")
    private String measure;

    // Serving weight in grams
    @Column(name = "grams")
    private BigDecimal grams;

    // Calories per serving
    @Column(name = "calories")
    private BigDecimal calories;

    // Protein per 100g
    @Column(name = "protein_100g")
    private BigDecimal protein100g;

    // Saturated fat per 100g
    @Column(name = "saturatedfats_100g")
    private BigDecimal saturatedFats100g;

    // Total fat per 100g
    @Column(name = "fat_100g")
    private BigDecimal fat100g;

    // Dietary fiber per 100g
    @Column(name = "fiber_100g")
    private BigDecimal fiber100g;

    // Carbohydrates per 100g
    @Column(name = "carbs_100g")
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
