# ParkiCare - Caregiver Task Coordination System Backend

Welcome to the ParkiCare backend project! This is a modern, feature-rich application built with Spring Boot, designed to provide a comprehensive solution for task management, schedule coordination, and health monitoring for caregivers and patients.

## ✨ Introduction

The core mission of ParkiCare is to empower caregivers to manage their daily tasks more intelligently and efficiently. Whether it's medication management, outdoor activity planning, or routine home care, this system offers a robust set of APIs to support these functionalities. The project leverages cloud services like Google Vision API and Firebase to deliver advanced features such as OCR recognition and push notifications.

## 🚀 Tech Stack

This project is built with a modern, enterprise-grade technology stack:

- **Core Framework**: [Spring Boot 3.5.13](https://spring.io/projects/spring-boot)
- **Programming Language**: [Java 17](https://www.oracle.com/java/)
- **Database & Persistence**:
    - [Spring Data JPA](https://spring.io/projects/spring-data-jpa) (with Hibernate)
    - [MySQL](https://www.mysql.com/)
- **API & Web**: Spring Web (for RESTful APIs)
- **Security**: [Spring Security Crypto](https://spring.io/projects/spring-security) (for password hashing)
- **Cloud Service Integration**:
    - **Push Notifications**: [Firebase Admin SDK](https://firebase.google.com/docs/admin/setup)
    - **Optical Character Recognition (OCR)**: [Qwen OCR API](https://bailian.console.aliyun.com/)
- **Utilities**:
    - [Lombok](https://projectlombok.org/): To reduce boilerplate code
    - [OpenPDF](https://github.com/librepdf/openpdf): For generating PDF documents
- **Build Tool**: [Apache Maven](https://maven.apache.org/)

## 🌟 Core Features

Based on an analysis of the project's controllers and services, the system provides the following key features:

- **Authentication & Authorization**:
    - User Registration (`/api/auth/register`)
    - User Login (`/api/auth/login`)
- **Core Entity Management**:
    - Caregiver Management
    - Patient Management
- **Scheduling & Task Coordination**:
    - Caregiver Schedule Management
    - Patient Outdoor Activity Planning
    - Patient Home Care Scheduling
- **Medication Management & Reminders**:
    - **Smart Recognition**: OCR for drug information (`DrugOcrController`, `MedicineRecognitionController`)
    - Automatic/Manual Medication Plan Creation (`MedicationPlanService`)
    - Medication Report Generation (`MedicationReportController`)
    - Medication Reminders (`ReminderController`)
- **Dashboard**:
    - Provides an overview of daily tasks, medication schedules, and more.
- **Food & Nutrition**:
    - An API to query nutritional information about food items (`FoodNutritionController`).
- **Push Notifications**:
    - Sends real-time alerts and notifications (`PushSubscriptionController`, `PushNotificationService`).

## ⚙️ Environment Setup & Running

This project is configured with multi-environment support (`dev` and `prod`) to ensure a clean separation between development and production settings.

### Configuration File Structure

- `src/main/resources/`
    - `application.properties`: A common configuration file. Current default active profile is `prod`.
    - `application-dev.properties`: **For local development**. Used to connect to a **remote database**. **This file should NOT be committed to version control**.
    - `application-prod.properties`: **For production deployment**. Used to connect to the **local database** (`localhost`) on the server.
    - Firebase credentials are loaded by file path from `firebase.key.path` (profile-specific property).

### How to Run the Project

#### 1. Local Development

1.  **Configure `application-dev.properties`**:
    - Fill in the public IP, username, and password for your remote database.
2.  **Configure Firebase**:
    - Set `firebase.key.path` to your local Firebase service account file path.
    
3.  **Run the Application**:
    - In your IDE, locate and run the `CaregiverTaskSystemApplication.java` file.
    - The application will start in `dev` mode and connect to the remote database you configured.

#### 2. Production Deployment

1.  **Package the Application**:
    ```bash
    mvn clean package
    ```
    This command will generate a `.jar` file in the `target/` directory.

2.  **Upload and Run**:
    - Upload the generated `.jar` file to your cloud server.
    - Ensure `application-prod.properties` contains the correct Firebase key file path.
    - Example: `firebase.key.path=/home/ec2-user/secrets/firebase-service-account.json`
    - Use the following command to start the application, explicitly activating the `prod` profile:
    ```bash
    java -jar caregiver-task-system-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
    ```
    The application will start in `prod` mode and connect to the local database on the server.

## 🤝 Contributing

Contributions are welcome! If you have any questions or suggestions, please feel free to open an issue.
