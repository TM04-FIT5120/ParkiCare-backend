package com.caregiver.repository;

import com.caregiver.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    List<Patient> findByCaregiverId(Long caregiverId);
}