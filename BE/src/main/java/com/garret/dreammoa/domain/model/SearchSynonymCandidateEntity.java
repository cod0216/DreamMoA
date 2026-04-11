package com.garret.dreammoa.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_search_synonym_candidate",
        uniqueConstraints = @UniqueConstraint(name = "uq_search_synonym_candidate",
                columnNames = {"sourceTerm", "targetTerm", "category"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchSynonymCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sourceTerm;

    @Column(nullable = false, length = 100)
    private String targetTerm;

    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    private int evidenceCount;

    @Column(nullable = false)
    private int distinctUserCount;

    @Column(columnDefinition = "TEXT")
    private String sampleTitles;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = Status.PENDING;
        }
        if (confidenceScore == null) {
            confidenceScore = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }
}
