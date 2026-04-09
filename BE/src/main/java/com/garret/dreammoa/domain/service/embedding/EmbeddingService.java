package com.garret.dreammoa.domain.service.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class EmbeddingService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.embedding.api-url}")
    private String embeddingApiUrl;

    @Value("${openai.embedding.model}")
    private String embeddingModel;

    @Value("${openai.embedding.dimensions:768}")
    private int embeddingDimensions;

    public float[] getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        String preview = text.length() > 80 ? text.substring(0, 80) + "..." : text;
        log.info("🔎 OpenAI 임베딩 요청 시작 - model={}, dimensions={}, textLength={}, preview={}",
                embeddingModel, embeddingDimensions, text.length(), preview);

        EmbeddingRequest request = new EmbeddingRequest(text, embeddingModel, embeddingDimensions);
        WebClient webClient = WebClient.builder()
                .baseUrl(embeddingApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            EmbeddingResponse response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .block(Duration.ofSeconds(30));

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                log.error("❌ OpenAI 임베딩 응답이 비어 있습니다.");
                throw new IllegalStateException("OpenAI 임베딩 응답이 비어 있습니다.");
            }

            List<Double> embedding = response.getData().get(0).getEmbedding();
            if (embedding == null || embedding.isEmpty()) {
                log.error("❌ OpenAI 임베딩 벡터가 비어 있습니다.");
                throw new IllegalStateException("OpenAI 임베딩 벡터가 비어 있습니다.");
            }

            log.info("✅ OpenAI 임베딩 응답 성공 - vectorSize={}", embedding.size());

            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            return vector;
        } catch (WebClientResponseException e) {
            log.error("❌ OpenAI 임베딩 API 오류 - status={}, responseBody={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (Exception e) {
            log.error("❌ OpenAI 임베딩 처리 중 예외 발생", e);
            throw e;
        }
    }

    public static class EmbeddingRequest {
        private String input;
        private String model;
        private Integer dimensions;

        public EmbeddingRequest() { }

        public EmbeddingRequest(String input, String model, Integer dimensions) {
            this.input = input;
            this.model = model;
            this.dimensions = dimensions;
        }

        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Integer getDimensions() { return dimensions; }
        public void setDimensions(Integer dimensions) { this.dimensions = dimensions; }
    }

    public static class EmbeddingResponse {
        private List<EmbeddingData> data;

        public List<EmbeddingData> getData() { return data; }
        public void setData(List<EmbeddingData> data) { this.data = data; }
    }

    public static class EmbeddingData {
        private List<Double> embedding;

        public List<Double> getEmbedding() { return embedding; }
        public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }
    }
}
