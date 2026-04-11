package com.garret.dreammoa.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_search_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 100)
    private String sessionId;

    @Column(nullable = false, length = 255)
    private String queryText;

    @Column(nullable = false, length = 255)
    private String normalizedQuery;

    @Column(length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SearchType searchType;

    @Column(nullable = false)
    private int resultCount;

    @Column(nullable = false)
    private boolean semanticTriggered;

    private Long previousSearchLogId;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum SearchType {
        KEYWORD,
        SEMANTIC
    }
}
