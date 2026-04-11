package com.garret.dreammoa.domain.controller.search;

import com.garret.dreammoa.domain.dto.search.requestdto.SearchClickLogRequestDto;
import com.garret.dreammoa.domain.dto.search.responsedto.SearchClickLogResponseDto;
import com.garret.dreammoa.domain.dto.search.responsedto.SynonymCandidateGenerationResponseDto;
import com.garret.dreammoa.domain.model.SearchClickLogEntity;
import com.garret.dreammoa.domain.service.search.SearchClickLogService;
import com.garret.dreammoa.domain.service.search.SearchSynonymCandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class SearchAnalyticsController {

    private final SearchClickLogService searchClickLogService;
    private final SearchSynonymCandidateService searchSynonymCandidateService;

    @PostMapping("/boards/search/click")
    public ResponseEntity<SearchClickLogResponseDto> recordClick(@RequestBody SearchClickLogRequestDto requestDto) {
        SearchClickLogEntity saved = searchClickLogService.recordClick(requestDto);
        return ResponseEntity.ok(new SearchClickLogResponseDto(saved.getId()));
    }

    @PostMapping("/admin/search/synonym-candidates/generate")
    public ResponseEntity<SynonymCandidateGenerationResponseDto> generateCandidates(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) throws IOException {
        LocalDateTime baseTime = since != null ? since : LocalDateTime.now().minusDays(7);
        int generatedCount = searchSynonymCandidateService.generateCandidates(baseTime);
        return ResponseEntity.ok(new SynonymCandidateGenerationResponseDto(generatedCount));
    }
}
