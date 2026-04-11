package com.garret.dreammoa.domain.controller.BoardSearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garret.dreammoa.domain.document.BoardDocument;
import com.garret.dreammoa.domain.dto.board.responsedto.PageResponseDto;
import com.garret.dreammoa.domain.service.boardsearch.BoardSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoardSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class BoardSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BoardSearchService boardSearchService;

    @Test
    void searchBoardsReturnsMetadataAndResults() throws Exception {
        BoardDocument boardDocument = BoardDocument.builder()
                .id(1L)
                .title("엘라스틱서치 검색")
                .content("키워드 검색 결과")
                .plainContent("키워드 검색 결과")
                .category("자유")
                .userId(10L)
                .userNickname("tester")
                .createdAt(1_700_000_000_000L)
                .updatedAt(1_700_000_001_000L)
                .viewCount(3)
                .build();

        PageResponseDto<BoardDocument> response = new PageResponseDto<>(
                List.of(boardDocument),
                1,
                1,
                101L,
                "KEYWORD",
                false
        );

        given(boardSearchService.searchBoards(eq("엘라스틱"), eq("자유"), eq("session-1"), eq(0), eq(5)))
                .willReturn(response);

        mockMvc.perform(get("/boards/search")
                        .param("keyword", "엘라스틱")
                        .param("category", "자유")
                        .param("sessionId", "session-1")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(response)))
                .andExpect(jsonPath("$.searchLogId").value(101))
                .andExpect(jsonPath("$.searchType").value("KEYWORD"))
                .andExpect(jsonPath("$.semanticTriggered").value(false));
    }

    @Test
    void searchSemanticBoardsPassesPreviousSearchLogId() throws Exception {
        PageResponseDto<BoardDocument> response = new PageResponseDto<>(
                List.of(),
                0,
                0,
                202L,
                "SEMANTIC",
                true
        );

        given(boardSearchService.searchSemanticBoards(eq("멤버"), eq("자유"), eq("session-1"), eq(101L), eq(0), eq(5), eq(true)))
                .willReturn(response);

        mockMvc.perform(get("/boards/search/search-semantic")
                        .param("keyword", "멤버")
                        .param("category", "자유")
                        .param("sessionId", "session-1")
                        .param("previousSearchLogId", "101")
                        .param("page", "0")
                        .param("size", "5")
                        .param("topOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchLogId").value(202))
                .andExpect(jsonPath("$.searchType").value("SEMANTIC"))
                .andExpect(jsonPath("$.semanticTriggered").value(true));
    }
}
