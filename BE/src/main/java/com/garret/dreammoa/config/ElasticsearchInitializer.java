package com.garret.dreammoa.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ElasticsearchInitializer {

    private final ElasticsearchClient elasticsearchClient;
    @Value("${elastic.semantic.inference-id:.elser-2-elasticsearch}")
    private String semanticInferenceId;

    public ElasticsearchInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @PostConstruct
    public void initializeElasticsearchIndex() {
        try {
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
            // nori_analyzer를 사용하여 한국어 형태소 기반 검색을 지원합니다.
            String settingsJson = """
                    {
                      "settings": {
                        "analysis": {
                          "tokenizer": {
                            "nori_tokenizer": { "type": "nori_tokenizer" }
                          },
                          "analyzer": {
                            "nori_analyzer": { "type": "custom", "tokenizer": "nori_tokenizer" }
                          }
                        }
                      }
                    }
                    """;

            // title/content 필드에 nori_analyzer를 적용하고, semantic fallback 검색용 필드를 함께 저장합니다.
            String mappingsJson = """
                    {
                      "mappings": {
                        "properties": {
                          "title": {
                            "type": "text",
                            "analyzer": "nori_analyzer"
                          },
                          "content": {
                            "type": "text",
                            "analyzer": "nori_analyzer"
                          },
                          "semanticText": {
                            "type": "semantic_text",
                            "inference_id": "%s"
                          }
                        }
                      }
                    }
                    """.formatted(semanticInferenceId);

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
}
