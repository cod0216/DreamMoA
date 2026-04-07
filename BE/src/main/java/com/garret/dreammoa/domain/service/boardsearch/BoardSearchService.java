package com.garret.dreammoa.domain.service.boardsearch;

import com.garret.dreammoa.domain.document.BoardDocument;
import com.garret.dreammoa.domain.dto.board.responsedto.PageResponseDto;

import java.util.List;

public interface BoardSearchService {
    /**
     * 키워드가 포함된 게시글 검색
     * @param keyword 검색할 키워드
     * @return 검색된 게시글 목록
     */

    PageResponseDto<BoardDocument> searchBoards(String keyword, int page, int size);

    PageResponseDto<BoardDocument> searchSemanticBoards(String keyword, int page, int size, boolean topOnly);
}
