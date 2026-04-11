package com.garret.dreammoa.domain.repository;

import com.garret.dreammoa.domain.model.SearchClickLogEntity;
import com.garret.dreammoa.domain.model.SearchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchClickLogRepository extends JpaRepository<SearchClickLogEntity, Long> {

    @Query("""
        select scl
        from SearchClickLogEntity scl
        join fetch scl.searchLog sl
        where sl.searchType = :searchType
          and scl.resultType = :resultType
          and scl.clickedAt >= :since
        """)
    List<SearchClickLogEntity> findSemanticClicksSince(
            @Param("since") LocalDateTime since,
            @Param("searchType") SearchLogEntity.SearchType searchType,
            @Param("resultType") SearchLogEntity.SearchType resultType
    );
}
