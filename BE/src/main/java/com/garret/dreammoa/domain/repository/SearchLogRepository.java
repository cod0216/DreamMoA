package com.garret.dreammoa.domain.repository;

import com.garret.dreammoa.domain.model.SearchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLogEntity, Long> {
    List<SearchLogEntity> findAllByIdIn(Collection<Long> ids);

    List<SearchLogEntity> findBySessionIdIsNotNullAndCreatedAtAfterOrderBySessionIdAscCreatedAtAsc(LocalDateTime since);
}
