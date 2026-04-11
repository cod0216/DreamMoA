package com.garret.dreammoa.domain.dto.search.requestdto;

import com.garret.dreammoa.domain.model.SearchLogEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchClickLogRequestDto {
    private Long searchLogId;
    private Long postId;
    private String postTitle;
    private int clickedRank;
    private SearchLogEntity.SearchType resultType;
}
