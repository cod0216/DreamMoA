package com.garret.dreammoa.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_search_click_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchClickLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_log_id", nullable = false)
    private SearchLogEntity searchLog;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false, length = 255)
    private String postTitle;

    @Column(nullable = false)
    private int clickedRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SearchLogEntity.SearchType resultType;

    private LocalDateTime clickedAt;

    @PrePersist
    public void prePersist() {
        if (clickedAt == null) {
            clickedAt = LocalDateTime.now();
        }
    }
}
