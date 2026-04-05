package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "drug_base")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrugBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "drug_id")
    private Long drugId;

    @Column(name = "drug_name", nullable = false, unique = true, length = 50)
    private String drugName;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "manufacturer_name", length = 100)
    private String manufacturerName;

    @Column(name = "dosage", length = 30)
    private String dosage;

    @Column(name = "frequency", length = 20)
    private String frequency;

    @Column(name = "is_valid")
    private Integer isValid = 1;

    @Column(name = "remark", length = 200)
    private String remark;
}