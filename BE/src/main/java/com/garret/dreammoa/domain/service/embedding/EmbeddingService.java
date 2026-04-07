package com.garret.dreammoa.domain.service.embedding;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class EmbeddingService {

    // Python 임베딩 서비스의 URL (예: 로컬 테스트 시 http://localhost:8000)
    private final WebClient webClient = WebClient.create("http://localhost:8000");

    public float[] getEmbedding(String text) {
        // 요청 객체 생성
        EmbedRequest request = new EmbedRequest(text);

        // Python 서비스의 /embed 엔드포인트 호출 (블로킹 방식)
        Mono<EmbedResponse> responseMono = webClient.post()
                .uri("/embed")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbedResponse.class);

        EmbedResponse response = responseMono.block();
        return response.getEmbedding();
    }

    // 요청 객체 정의
    public static class EmbedRequest {
        private String text;
        public EmbedRequest() { }
        public EmbedRequest(String text) { this.text = text; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    // 응답 객체 정의
    public static class EmbedResponse {
        private float[] embedding;
        public float[] getEmbedding() { return embedding; }
        public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    }
}
