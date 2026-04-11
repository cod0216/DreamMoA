package com.garret.dreammoa.domain.service.search;

import com.garret.dreammoa.domain.dto.user.CustomUserDetails;
import com.garret.dreammoa.domain.model.SearchLogEntity;
import com.garret.dreammoa.domain.repository.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogRepository searchLogRepository;

    @Transactional
    public SearchLogEntity createSearchLog(
            String query,
            String category,
            String sessionId,
            SearchLogEntity.SearchType searchType,
            int resultCount,
            Long previousSearchLogId
    ) {
        SearchLogEntity searchLog = SearchLogEntity.builder()
                .userId(resolveUserId())
                .sessionId(sessionId)
                .queryText(query)
                .normalizedQuery(normalizeQuery(query))
                .category(category)
                .searchType(searchType)
                .resultCount(resultCount)
                .semanticTriggered(false)
                .previousSearchLogId(previousSearchLogId)
                .build();

        SearchLogEntity saved = searchLogRepository.save(searchLog);
        if (searchType == SearchLogEntity.SearchType.SEMANTIC && previousSearchLogId != null) {
            searchLogRepository.findById(previousSearchLogId).ifPresent(previous -> {
                previous.setSemanticTriggered(true);
                searchLogRepository.save(previous);
            });
        }
        return saved;
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getId();
        }
        return null;
    }
}
