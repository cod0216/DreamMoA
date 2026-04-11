package com.garret.dreammoa.domain.service.search;

import com.garret.dreammoa.domain.dto.search.requestdto.SearchClickLogRequestDto;
import com.garret.dreammoa.domain.model.BoardEntity;
import com.garret.dreammoa.domain.model.SearchClickLogEntity;
import com.garret.dreammoa.domain.model.SearchLogEntity;
import com.garret.dreammoa.domain.repository.BoardRepository;
import com.garret.dreammoa.domain.repository.SearchClickLogRepository;
import com.garret.dreammoa.domain.repository.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchClickLogService {

    private final SearchLogRepository searchLogRepository;
    private final SearchClickLogRepository searchClickLogRepository;
    private final BoardRepository boardRepository;

    @Transactional
    public SearchClickLogEntity recordClick(SearchClickLogRequestDto requestDto) {
        SearchLogEntity searchLog = searchLogRepository.findById(requestDto.getSearchLogId())
                .orElseThrow(() -> new IllegalArgumentException("검색 로그가 존재하지 않습니다. id=" + requestDto.getSearchLogId()));

        String postTitle = requestDto.getPostTitle();
        if (postTitle == null || postTitle.isBlank()) {
            BoardEntity board = boardRepository.findById(requestDto.getPostId())
                    .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + requestDto.getPostId()));
            postTitle = board.getTitle();
        }

        SearchClickLogEntity clickLog = SearchClickLogEntity.builder()
                .searchLog(searchLog)
                .postId(requestDto.getPostId())
                .postTitle(postTitle)
                .clickedRank(requestDto.getClickedRank())
                .resultType(requestDto.getResultType())
                .build();

        return searchClickLogRepository.save(clickLog);
    }
}
