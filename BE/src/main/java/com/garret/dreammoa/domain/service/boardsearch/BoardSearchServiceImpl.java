package com.garret.dreammoa.domain.service.boardsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.garret.dreammoa.domain.document.BoardDocument;
import com.garret.dreammoa.domain.dto.board.responsedto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardSearchServiceImpl implements BoardSearchService {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * 키워드가 포함된 게시글 검색(Elasticsearch match query 사용)
     * @param keyword 검색할 키워드
     * @return 검색된 게시글 목록
     */
    @Override
    public PageResponseDto<BoardDocument> searchBoards(String keyword, int page, int size){
        try {
            // ✅ 검색 쿼리 생성 (multi_match 쿼리)
            Query query = Query.of(q -> q
                    .multiMatch(mm -> mm
                            .query(keyword)
                            .fields("title", "title.ngram", "content", "content.ngram")
                    )
            );

            // ✅ Elasticsearch 검색 실행 (from: page * size, size: 요청한 개수)
            var searchResponse = elasticsearchClient.search(s -> s
                            .index("board")
                            .query(query)
                            .from(page * size) // 🔹 시작 위치
                            .size(size), // 🔹 페이지 크기
                    BoardDocument.class
            );

            // ✅ 검색 결과 변환
            List<BoardDocument> content = searchResponse.hits().hits().stream()
                    .map(hit -> hit.source()) // BoardDocument 객체로 변환
                    .collect(Collectors.toList());

            // ✅ 전체 게시글 개수 가져오기
            long totalElements = searchResponse.hits().total().value();

            // ✅ 전체 페이지 수 계산
            int totalPages = (int) Math.ceil((double) totalElements / size);

            // ✅ PageResponse로 감싸서 반환
            return new PageResponseDto<>(content, totalPages, totalElements);
        } catch (IOException e) {
            throw new RuntimeException("Elasticsearch 검색 중 오류 발생", e);
        }
    }

    /**
     * Scroll API를 사용하여 주어진 쿼리에 매칭되는 모든 BoardDocument를 가져옵니다.
     * @param query 검색 쿼리
     * @param batchSize 한 번에 가져올 문서 수
     * @return 모든 검색 결과 BoardDocument 리스트
     */
    public List<BoardDocument> scrollSearch(Query query, int batchSize) {
        List<BoardDocument> allResults = new ArrayList<>();
        try {
            // 초기 검색 요청 (Scroll 컨텍스트 생성)
            SearchResponse<BoardDocument> searchResponse = elasticsearchClient.search(s -> s
                            .index("board")
                            .query(query)
                            // .minScore(Double.valueOf(1.0))  // 최소 스코어 (명시적으로 Double로)
                            // Scroll 유지 시간: "1m" (문자열로 지정)
                            .scroll(t -> t.time("1m"))
                            .size(batchSize)
                    , BoardDocument.class);

            // scrollId를 재할당할 수 있도록 배열에 보관
            final String[] scrollIdHolder = new String[] { searchResponse.scrollId() };
            List<Hit<BoardDocument>> hits = searchResponse.hits().hits();

            while (hits != null && !hits.isEmpty()) {
                for (Hit<BoardDocument> hit : hits) {
                    allResults.add(hit.source());
                }
                // 다음 페이지 요청 (scrollId와 동일한 유지 시간 사용)
                ScrollResponse<BoardDocument> scrollResponse = elasticsearchClient.scroll(sr -> sr
                                .scrollId(scrollIdHolder[0])
                                .scroll(t -> t.time("1m"))
                        , BoardDocument.class);
                scrollIdHolder[0] = scrollResponse.scrollId();
                hits = scrollResponse.hits().hits();
            }

            // Scroll 컨텍스트 클리어 (선택 사항)
            ClearScrollResponse clearScrollResponse = elasticsearchClient.clearScroll(cs -> cs
                    .scrollId(scrollIdHolder[0])
            );
            log.debug("ClearScroll 응답: {}", clearScrollResponse);

        } catch (Exception e) {
            log.error("Scroll 검색 중 오류 발생", e);
            throw new RuntimeException("Scroll 검색 중 오류 발생", e);
        }
        return allResults;
    }
}
