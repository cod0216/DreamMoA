package com.garret.dreammoa.domain.dto.board.responsedto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class PageResponseDto<T> {
    private List<T> content; //검색된 게시글 목록 담음
    private int totalPages; //전체 페이지 수
    private long totalElements; //총 게시글 개수
    private SearchType searchType; // 검색 결과 유형

    public PageResponseDto(List<T> content, int totalPages, long totalElements) {
        this(content, totalPages, totalElements, SearchType.KEYWORD);
    }

    public PageResponseDto(List<T> content, int totalPages, long totalElements, SearchType searchType) {
        this.content = content;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.searchType = searchType;
    }

    public enum SearchType {
        KEYWORD,
        RELATED
    }
}
