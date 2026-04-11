package com.garret.dreammoa.domain.repository;

import com.garret.dreammoa.domain.model.SearchSynonymCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SearchSynonymCandidateRepository extends JpaRepository<SearchSynonymCandidateEntity, Long> {
    Optional<SearchSynonymCandidateEntity> findBySourceTermAndTargetTermAndCategory(String sourceTerm, String targetTerm, String category);
}
