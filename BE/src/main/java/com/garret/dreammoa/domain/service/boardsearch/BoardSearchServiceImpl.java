package com.garret.dreammoa.domain.service.boardsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.garret.dreammoa.domain.document.BoardDocument;
import com.garret.dreammoa.domain.dto.board.responsedto.PageResponseDto;
import com.garret.dreammoa.domain.service.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.query.HasChildQuery;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardSearchServiceImpl implements BoardSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingService embeddingService;  // 생성자 주입 (@RequiredArgsConstructor 사용)

    @Value("${elastic.search.semantic.min-score:1.2}")
    private double semanticMinScore;

    @Value("${elastic.search.semantic.top-limit:10}")
    private int semanticTopLimit;

    /**
     * 키워드가 포함된 게시글 검색(Elasticsearch match query 사용)
     * @param keyword 검색할 키워드
     * @return 검색된 게시글 목록
     */
    @Override
    public PageResponseDto<BoardDocument> searchBoards(String keyword, String category, int page, int size){
        try {
            String minimumShouldMatch = resolveMinimumShouldMatch(keyword);
            // ✅ 검색 쿼리 생성 (multi_match 쿼리)
            Query keywordQuery = Query.of(q -> q
                    .multiMatch(mm -> mm
                            .query(keyword)
                            .fields("title^3", "title.ngram^2", "plainContent")
                            .minimumShouldMatch(minimumShouldMatch)
                    )
            );
            Query query = wrapWithCategoryFilter(keywordQuery, category);

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

    static String resolveMinimumShouldMatch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }

        int tokenCount = keyword.trim().split("\\s+").length;
        if (tokenCount <= 1) {
            return null;
        }
        if (tokenCount == 2) {
            return "50%";
        }
        if (tokenCount == 3) {
            return "70%";
        }
        return "75%";
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
                    // _source 필터링: "embedding" 필드를 제외 (응답에 embedding이 보이지 않음)
//                            .source(src -> src.filter(f -> f.excludes(List.of("embedding"))))
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

    @Override
    public PageResponseDto<BoardDocument> searchSemanticBoards(String keyword, String category, int page, int size, boolean topOnly) {
        try {
//            log.debug("searchSemanticBoards - received keyword: {}", keyword);

            // 1. 임베딩 서비스로부터 검색어 임베딩 벡터 획득
            float[] queryEmbedding = embeddingService.getEmbedding(keyword);
//            log.debug("searchSemanticBoards - obtained embedding vector of length: {}", queryEmbedding.length);
//            log.debug("searchSemanticBoards - first 5 elements: {}",
//                    Arrays.toString(Arrays.copyOfRange(queryEmbedding, 0, Math.min(queryEmbedding.length, 5))));

            // 2. float[]를 List<Double>로 변환
            List<Double> queryVectorList = new ArrayList<>();
            for (float val : queryEmbedding) {
                queryVectorList.add((double) val);
            }

            // 3. "정확한 텍스트 매칭" 쿼리 (제목과 내용 검색, boost 적용)
            Map<String, Object> titleMatch = new HashMap<>();
            titleMatch.put("query", keyword);
            titleMatch.put("boost", 4.0);
            Map<String, Object> titleClause = Collections.singletonMap("match", Collections.singletonMap("title", titleMatch));

            Map<String, Object> contentMatch = new HashMap<>();
            contentMatch.put("query", keyword);
            contentMatch.put("boost", 2.0);
            Map<String, Object> contentClause = Collections.singletonMap("match", Collections.singletonMap("plainContent", contentMatch));

            Map<String, Object> exactMatchClause = new HashMap<>();
            exactMatchClause.put("bool", Collections.singletonMap("should", Arrays.asList(titleClause, contentClause)));

            // 4. 임베딩 기반 semantic 쿼리 구성 (script_score 사용)
            Map<String, Object> script = new HashMap<>();
            script.put("source", "cosineSimilarity(params.query_vector, 'embedding') + 1.0");
            script.put("params", Collections.singletonMap("query_vector", queryVectorList));

            Map<String, Object> scriptScoreWrapper = Collections.singletonMap("script", script);
            Map<String, Object> semanticFunction = Collections.singletonMap("script_score", scriptScoreWrapper);

            Map<String, Object> semanticClause = new HashMap<>();
            semanticClause.put("function_score", new HashMap<String, Object>() {{
                put("query", Collections.singletonMap("match_all", new HashMap<>()));
                put("functions", Collections.singletonList(semanticFunction));
                put("boost_mode", "multiply");
                put("score_mode", "sum");
            }});

            // 5. 위 두 쿼리를 bool 쿼리의 should 절로 결합
            Map<String, Object> boolQuery = new HashMap<>();
            boolQuery.put("should", Arrays.asList(exactMatchClause, semanticClause));
            boolQuery.put("minimum_should_match", 1);
// 운영, 의미 기반만쓸건가
            /*
             * topOnly가 true인 경우,
             * 전체 검색 결과 중 상위 추천 결과(예: 상위 10개)만을 대상으로 페이지네이션 처리합니다.
             * 예를 들어, 프론트에서 상위 10개를 5개씩 2페이지로 나누어 보여주려면,
             * - 실제 검색 쿼리는 from=0, size=10으로 실행되어 상위 10개를 모두 가져온 뒤,
             * - 이후 Java 단에서 page와 size (예: page 0: index 0~4, page 1: index 5~9)로 나누어 반환합니다.
             */
            int queryFrom;
            int querySize;
            if (topOnly) {
                int topLimit = semanticTopLimit; // 상위 추천 결과 제한 개수
                queryFrom = 0;
                querySize = topLimit;
            } else {
                queryFrom = page * size;
                querySize = size;
            }

            // 6. 최종 쿼리 구성 (페이지네이션과 정렬 적용)
            Map<String, Object> finalQuery = new HashMap<>();
            Map<String, Object> finalBoolQuery = new HashMap<>();
            finalBoolQuery.put("should", boolQuery.get("should"));
            finalBoolQuery.put("minimum_should_match", boolQuery.get("minimum_should_match"));
            if (hasCategoryFilter(category)) {
                finalBoolQuery.put("filter", Collections.singletonList(
                        Collections.singletonMap("term", Collections.singletonMap("category", category))
                ));
            }
            finalQuery.put("query", Collections.singletonMap("bool", finalBoolQuery));
            finalQuery.put("from", queryFrom);
            finalQuery.put("size", querySize);
            finalQuery.put("min_score", semanticMinScore);
            finalQuery.put("sort", Collections.singletonList(
                    Collections.singletonMap("_score", Collections.singletonMap("order", "desc"))
            ));
            // _source 필터: embedding 필드 제외
            finalQuery.put("_source", Collections.singletonMap("excludes", Collections.singletonList("embedding")));

            ObjectMapper mapper = new ObjectMapper();
            String rawQuery = mapper.writeValueAsString(finalQuery);
            log.debug("Raw semantic query: {}", rawQuery);

            // 7. low-level REST 클라이언트를 사용하여 쿼리 실행
            RestClient lowLevelClient = ((co.elastic.clients.transport.rest_client.RestClientTransport)
                    elasticsearchClient._transport()).restClient();
            Request request = new Request("GET", "/board/_search?pretty");
            request.setJsonEntity(rawQuery);
            Response response = lowLevelClient.performRequest(request);

            JsonNode rootNode = mapper.readTree(response.getEntity().getContent());
            JsonNode hitsNode = rootNode.path("hits").path("hits");

            List<BoardDocument> allResults = new ArrayList<>();
            for (JsonNode hit : hitsNode) {
                double score = hit.path("_score").asDouble();
                JsonNode sourceNode = hit.path("_source");
                BoardDocument doc = mapper.treeToValue(sourceNode, BoardDocument.class);
                allResults.add(doc);
                log.info("게시글 제목: '{}' | 유사도 점수: {}", doc.getTitle(), score);
            }

            long totalElements;
            int totalPages;
            List<BoardDocument> paginatedResults;
            if (topOnly) {
                // topOnly인 경우, 전체 결과는 최대 topLimit(예: 10)개입니다.
                totalElements = allResults.size();
                totalPages = (int) Math.ceil((double) totalElements / size);
                // 프론트에서 요청한 페이지에 따라 subList로 결과를 잘라서 반환
                int startIndex = page * size;
                int endIndex = Math.min(startIndex + size, allResults.size());
                if (startIndex >= allResults.size()) {
                    paginatedResults = Collections.emptyList();
                } else {
                    paginatedResults = allResults.subList(startIndex, endIndex);
                }
            } else {
                totalElements = rootNode.path("hits").path("total").path("value").asLong();
                totalPages = (int) Math.ceil((double) totalElements / size);
                paginatedResults = allResults;
            }

            log.debug("searchSemanticBoards - returning {} results", paginatedResults.size());
            return new PageResponseDto<>(paginatedResults, totalPages, totalElements);
        } catch (Exception e) {
            log.error("Elasticsearch 의미 기반 검색 중 오류 발생", e);
            throw new RuntimeException("Elasticsearch 의미 기반 검색 중 오류 발생", e);
        }
    }

    private Query wrapWithCategoryFilter(Query query, String category) {
        if (!hasCategoryFilter(category)) {
            return query;
        }

        return Query.of(q -> q
                .bool(b -> b
                        .must(query)
                        .filter(f -> f
                                .term(t -> t
                                        .field("category")
                                        .value(category)
                                )
                        )
                )
        );
    }

    private boolean hasCategoryFilter(String category) {
        return category != null && !category.isBlank();
    }




}
