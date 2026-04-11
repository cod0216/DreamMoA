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
    private Long searchLogId;
    private String searchType;
    private Boolean semanticTriggered;

    public PageResponseDto(List<T> content, int totalPages, long totalElements) {
        this(content, totalPages, totalElements, null, null, null);
    }

    public PageResponseDto(List<T> content, int totalPages, long totalElements, Long searchLogId, String searchType, Boolean semanticTriggered) {
        this.content = content;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.searchLogId = searchLogId;
        this.searchType = searchType;
        this.semanticTriggered = semanticTriggered;
    }
}
