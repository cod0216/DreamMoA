package com.garret.dreammoa.config;

import com.garret.dreammoa.domain.service.search.SearchSynonymCandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "search.synonym-candidate", name = "enabled", havingValue = "true")
public class SearchSynonymCandidateBatch {

    private final SearchSynonymCandidateService searchSynonymCandidateService;

    @Value("${search.synonym-candidate.lookback-days:7}")
    private long lookbackDays;

    @Scheduled(cron = "${search.synonym-candidate.cron:0 0 3 * * *}")
    public void run() {
        try {
            int generated = searchSynonymCandidateService.generateCandidates(LocalDateTime.now().minusDays(lookbackDays));
            log.info("✅ 동의어 후보 배치 완료 - generated={}", generated);
        } catch (Exception e) {
            log.error("❌ 동의어 후보 배치 실패", e);
        }
    }
}
