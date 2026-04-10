package com.garret.dreammoa.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ElasticsearchInitializer {

    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @PostConstruct
    public void initializeElasticsearchIndex() {
        try {
            List<String> synonymLines = loadSynonymLines();
            String synonymsJsonArray = synonymLines.stream()
                    .map(this::escapeJson)
                    .collect(Collectors.joining("\",\n                                \"", "\"", "\""));

            // 기존 'board' 인덱스가 존재하는지 확인
            boolean indexExists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index("board"))).value();

            // 기존 인덱스 삭제 (새로운 설정 적용을 위해)
            if (indexExists) {
                elasticsearchClient.indices()
                        .delete(DeleteIndexRequest.of(d -> d.index("board")));
                System.out.println("⚠️ 기존 'board' 인덱스를 삭제했습니다.");
            }

            // 새로운 'board' 인덱스 생성
            // index.max_ngram_diff 값을 추가하여 edge_ngram tokenizer의 min_gram과 max_gram 차이를 허용합니다.
            // nori_analyzer는 한국어 형태소 분석을 위해 사용하고,
            // title.ngram 필드는 edge_ngram 기반 prefix 검색을 지원합니다.
            String settingsJson = """
                    {
                      "settings": {
                        "index": {
                          "max_ngram_diff": 8
                        },
                        "analysis": {
                          "tokenizer": {
                            "nori_tokenizer": { "type": "nori_tokenizer" },
                            "edge_ngram_tokenizer": {
                              "type": "edge_ngram",
                              "min_gram": 2,
                              "max_gram": 10,
                              "token_chars": [ "letter", "digit", "symbol" ]
                            }
                          },
                          "filter": {
                            "board_synonym_filter": {
                              "type": "synonym_graph",
                              "synonyms": [
                                %s
                              ]
                            }
                          },
                          "analyzer": {
                            "nori_analyzer": { "type": "custom", "tokenizer": "nori_tokenizer" },
                            "nori_search_analyzer": {
                              "type": "custom",
                              "tokenizer": "nori_tokenizer",
                              "filter": ["board_synonym_filter"]
                            },
                            "edge_ngram_analyzer": { "type": "custom", "tokenizer": "edge_ngram_tokenizer", "filter": ["lowercase"] }
                          }
                        }
                      }
                    }
                    """.formatted(synonymsJsonArray);

            // 멀티 필드 매핑을 사용하여, 기본 텍스트 필드에는 nori_analyzer를 적용합니다.
            // 제목의 부분검색 필드는 edge_ngram을 사용하고, 본문 검색은 plainContent 필드로 수행합니다.
            String mappingsJson = """
                    {
                      "mappings": {
                        "properties": {
                          "title": {
                            "type": "text",
                            "analyzer": "nori_analyzer",
                            "search_analyzer": "nori_search_analyzer",
                            "fields": {
                              "ngram": {
                                "type": "text",
                                "analyzer": "edge_ngram_analyzer"
                              }
                            }
                          },
                          "content": {
                            "type": "text",
                            "analyzer": "nori_analyzer",
                            "index": false
                          },
                          "plainContent": {
                            "type": "text",
                            "analyzer": "nori_analyzer",
                            "search_analyzer": "nori_search_analyzer"
                          },
                          "embedding": {
                                          "type": "dense_vector",
                                          "dims": 768
                                        }
                        }
                      }
                    }
                    """;

            ByteArrayInputStream settingsStream = new ByteArrayInputStream(
                    settingsJson.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream mappingsStream = new ByteArrayInputStream(
                    mappingsJson.getBytes(StandardCharsets.UTF_8));

            CreateIndexRequest createIndexRequest = CreateIndexRequest.of(c -> c
                    .index("board")
                    .withJson(settingsStream)
                    .withJson(mappingsStream)
            );

            CreateIndexResponse response = elasticsearchClient.indices().create(createIndexRequest);
            if (response.acknowledged()) {
                System.out.println("✅ Elasticsearch 'board' 인덱스가 성공적으로 생성됨!");
            }
        } catch (IOException e) {
            System.err.println("❌ Elasticsearch 인덱스 설정 중 오류 발생: " + e.getMessage());
        }
    }

    private List<String> loadSynonymLines() throws IOException {
        ClassPathResource resource = new ClassPathResource("elasticsearch/board-synonyms.txt");
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .toList();
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
