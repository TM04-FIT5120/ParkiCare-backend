package com.caregiver.entity;

import com.caregiver.model.TranslationEntityType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "content_translation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_translation",
                columnNames = {"entity_type", "entity_id", "locale", "field_key"}
        )
)
public class ContentTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private TranslationEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "translated_text", nullable = false, columnDefinition = "TEXT")
    private String translatedText;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }
}
