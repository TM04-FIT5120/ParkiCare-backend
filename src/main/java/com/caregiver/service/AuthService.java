package com.caregiver.service;

import com.caregiver.dto.LoginRequest;
import com.caregiver.dto.LoginResponse;
import com.caregiver.dto.RegisterRequest;
import com.caregiver.dto.RegisterResponse;
import com.caregiver.entity.Caregiver;
import com.caregiver.repository.CaregiverRepository;
import com.caregiver.util.UtilPackage;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final CaregiverRepository caregiverRepository;

    public AuthService(CaregiverRepository caregiverRepository) {
        this.caregiverRepository = caregiverRepository;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String nickname = request.getNickname().trim();

        if (caregiverRepository.existsByNickname(nickname)) {
            throw new RuntimeException("Nickname already exists");
        }

        Caregiver caregiver = new Caregiver();
        caregiver.setNickname(nickname);
        caregiver.setPassword(UtilPackage.encode(request.getPassword()));
        caregiver.setUniqueId(generateNextUniqueId());
        caregiver.setIsActive(1);

        Caregiver saved = caregiverRepository.save(caregiver);

        return new RegisterResponse(
                saved.getId(),
                saved.getNickname(),
                saved.getUniqueId(),
                "Register successful"
        );
    }

    public LoginResponse login(LoginRequest request) {
        Optional<Caregiver> optionalCaregiver =
                caregiverRepository.findByUniqueId(request.getUniqueId().trim());

        if (optionalCaregiver.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        Caregiver caregiver = optionalCaregiver.get();

        if (caregiver.getIsActive() == 0) {
            throw new RuntimeException("Account is disabled");
        }

        if (!UtilPackage.matches(request.getPassword(), caregiver.getPassword())) {
            throw new RuntimeException("Password is incorrect");
        }

        return new LoginResponse(
                caregiver.getId(),
                caregiver.getNickname(),
                caregiver.getUniqueId(),
                "Login successful"
        );
    }

    private String generateNextUniqueId() {
        Long maxId = caregiverRepository.findMaxUniqueIdNumber();

        long nextId = (maxId == null) ? 1L : maxId + 1L;

        if (nextId > 999999L) {
            throw new RuntimeException("Unique ID limit reached");
        }

        return String.format("%06d", nextId);
    }
}