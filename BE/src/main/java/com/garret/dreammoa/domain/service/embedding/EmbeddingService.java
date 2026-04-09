package com.garret.dreammoa.domain.service.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class EmbeddingService {

    // Python 임베딩 서비스의 URL (예: 로컬 테스트 시 http://localhost:8000)
    private final WebClient webClient = WebClient.create("http://localhost:8000");

    public float[] getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        String preview = text.length() > 80 ? text.substring(0, 80) + "..." : text;
        log.info("🔎 FastAPI 임베딩 요청 시작 - textLength={}, preview={}", text.length(), preview);

        EmbedRequest request = new EmbedRequest(text);

        try {
            Mono<EmbedResponse> responseMono = webClient.post()
                    .uri("/embed")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbedResponse.class);

            EmbedResponse response = responseMono.block(Duration.ofSeconds(30));
            if (response == null || response.getEmbedding() == null || response.getEmbedding().length == 0) {
                log.error("❌ FastAPI 임베딩 응답이 비어 있습니다.");
                throw new IllegalStateException("FastAPI 임베딩 응답이 비어 있습니다.");
            }

            log.info("✅ FastAPI 임베딩 응답 성공 - vectorSize={}", response.getEmbedding().length);
            return response.getEmbedding();
        } catch (WebClientResponseException e) {
            log.error("❌ FastAPI 임베딩 API 오류 - status={}, responseBody={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (Exception e) {
            log.error("❌ FastAPI 임베딩 처리 중 예외 발생", e);
            throw e;
        }
    }

    public static class EmbedRequest {
        private String text;

        public EmbedRequest() { }

        public EmbedRequest(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class EmbedResponse {
        private float[] embedding;

        public float[] getEmbedding() {
            return embedding;
        }

        public void setEmbedding(float[] embedding) {
            this.embedding = embedding;
        }
    }
}
