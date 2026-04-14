package com.caregiver;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class CaregiverTaskSystemApplication {

    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CaregiverTaskSystemApplication.class, args);
    }

}
