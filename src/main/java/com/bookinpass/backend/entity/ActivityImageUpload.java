package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Photo déposée dans S3 depuis le back-office : gardée tant qu'une fiche l'emploie, supprimée sinon (après un délai de
 * grâce, ou tout de suite si elle est retirée avant d'avoir été enregistrée).
 */
@Entity
@Table(name = "activity_image_upload")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityImageUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "object_key", nullable = false)
    private String objectKey;
    @Column(nullable = false)
    private String url;
    @Column(name = "uploaded_by", nullable = false, length = 50)
    private String uploadedBy;
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public ActivityImageUpload(String objectKey, String url, String uploadedBy, Instant uploadedAt) {
        this.objectKey = objectKey;
        this.url = url;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }
}
