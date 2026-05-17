package com.caregiver.repository;

import com.caregiver.entity.ContentTranslation;
import com.caregiver.model.TranslationEntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ContentTranslationRepository extends JpaRepository<ContentTranslation, Long> {

    List<ContentTranslation> findByEntityTypeAndEntityIdInAndLocale(
            TranslationEntityType entityType,
            Collection<Long> entityIds,
            String locale
    );

    List<ContentTranslation> findByEntityTypeAndEntityIdAndLocale(
            TranslationEntityType entityType,
            Long entityId,
            String locale
    );

    void deleteByEntityTypeAndEntityIdAndLocale(
            TranslationEntityType entityType,
            Long entityId,
            String locale
    );

    long countByEntityTypeAndLocaleAndFieldKey(
            TranslationEntityType entityType,
            String locale,
            String fieldKey
    );

    @Query("""
            SELECT COUNT(DISTINCT t.entityId) FROM ContentTranslation t
            WHERE t.entityType = :entityType AND t.locale = :locale
            """)
    long countDistinctEntityIdsByEntityTypeAndLocale(
            @Param("entityType") TranslationEntityType entityType,
            @Param("locale") String locale
    );
}
