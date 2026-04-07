package com.garret.dreammoa.domain.controller.BoardSearch;

import com.garret.dreammoa.domain.document.BoardDocument;
import com.garret.dreammoa.domain.dto.board.responsedto.PageResponseDto;
import com.garret.dreammoa.domain.service.boardsearch.BoardSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("boards/search")
@RequiredArgsConstructor
public class BoardSearchController {

    private final BoardSearchService boardSearchService;

    /**
     * 키워드 기반 게시글 검색 API
     * @param keyword 검색할 키워드
     * @return 검색된 게시글 목록
     */
    @GetMapping
    public ResponseEntity<PageResponseDto<BoardDocument>> searchBoards(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size){
        PageResponseDto<BoardDocument> results = boardSearchService.searchBoards(keyword, page, size);
        return ResponseEntity.ok(results);
    }

    /**
     * 의미 기반 게시글 검색 API (BERT 임베딩 및 script_score 기반)
     * Endpoint: GET /boards/searchSemantic?keyword=...
     * @param keyword 검색할 키워드
     * @return 검색된 게시글 목록
     */
    @GetMapping("/search-semantic")
    public ResponseEntity<PageResponseDto<BoardDocument>> searchSemanticBoards(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "false") boolean topOnly) {
        PageResponseDto<BoardDocument> results = boardSearchService.searchSemanticBoards(keyword, page, size, topOnly);
        return ResponseEntity.ok(results);
    }

}
