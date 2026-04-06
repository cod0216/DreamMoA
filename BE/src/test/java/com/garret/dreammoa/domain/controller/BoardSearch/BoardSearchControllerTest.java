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
    void searchBoardsReturnsKeywordSearchResults() throws Exception {
        BoardDocument boardDocument = BoardDocument.builder()
                .id(1L)
                .title("엘라스틱서치 검색")
                .content("키워드 검색 결과")
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
                1
        );

        given(boardSearchService.searchBoards(eq("엘라스틱"), eq(0), eq(5))).willReturn(response);

        mockMvc.perform(get("/boards/search")
                        .param("keyword", "엘라스틱")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(response)))
                .andExpect(jsonPath("$.content[0].title").value("엘라스틱서치 검색"))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void semanticSearchEndpointIsNotExposed() throws Exception {
        mockMvc.perform(get("/boards/search/search-semantic")
                        .param("keyword", "엘라스틱"))
                .andExpect(status().isNotFound());
    }
}
