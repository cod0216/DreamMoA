package com.garret.dreammoa.domain.dto.board.responsedto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDto<T> {
    private List<T> content; //검색된 게시글 목록 담음
    private int totalPages; //전체 페이지 수
    private long totalElements; //총 게시글 개수
}
