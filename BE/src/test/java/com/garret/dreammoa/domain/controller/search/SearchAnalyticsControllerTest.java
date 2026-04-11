package com.garret.dreammoa.domain.controller.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garret.dreammoa.domain.dto.search.requestdto.SearchClickLogRequestDto;
import com.garret.dreammoa.domain.model.SearchClickLogEntity;
import com.garret.dreammoa.domain.model.SearchLogEntity;
import com.garret.dreammoa.domain.service.search.SearchClickLogService;
import com.garret.dreammoa.domain.service.search.SearchSynonymCandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchClickLogService searchClickLogService;

    @MockBean
    private SearchSynonymCandidateService searchSynonymCandidateService;

    @Test
    void recordClickStoresSearchClickLog() throws Exception {
        SearchClickLogRequestDto request = new SearchClickLogRequestDto();
        request.setSearchLogId(101L);
        request.setPostId(8L);
        request.setPostTitle("함께 도전할 팀원을 모집합니다!");
        request.setClickedRank(2);
        request.setResultType(SearchLogEntity.SearchType.SEMANTIC);

        SearchClickLogEntity saved = SearchClickLogEntity.builder()
                .id(1L)
                .postId(8L)
                .postTitle("함께 도전할 팀원을 모집합니다!")
                .clickedRank(2)
                .resultType(SearchLogEntity.SearchType.SEMANTIC)
                .clickedAt(LocalDateTime.now())
                .build();

        given(searchClickLogService.recordClick(any(SearchClickLogRequestDto.class))).willReturn(saved);

        mockMvc.perform(post("/boards/search/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void generateCandidatesReturnsGeneratedCount() throws Exception {
        given(searchSynonymCandidateService.generateCandidates(any(LocalDateTime.class))).willReturn(3);

        mockMvc.perform(post("/admin/search/synonym-candidates/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedCount").value(3));
    }
}
