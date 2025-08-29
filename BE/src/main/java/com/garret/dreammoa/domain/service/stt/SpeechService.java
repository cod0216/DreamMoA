package com.garret.dreammoa.domain.service.stt;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SpeechService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient();

    @Value("${openai.api.key}")
    private String openaiApiKey;

    // 공통 음성 인식 로직 (음성 데이터를 받아 텍스트로 변환)
    private String convertSpeech(byte[] audioBytes, RecognitionConfig config) throws IOException {
        ByteString audioData = ByteString.copyFrom(audioBytes);
        RecognitionAudio recognitionAudio = RecognitionAudio.newBuilder()
                .setContent(audioData)
                .build();

        try (SpeechClient speechClient = SpeechClient.create()) {
            RecognizeResponse response = speechClient.recognize(config, recognitionAudio);
            List<SpeechRecognitionResult> results = response.getResultsList();
            if (!results.isEmpty()) {
                return results.get(0).getAlternatives(0).getTranscript();
            } else {
                return "";
            }
        }
    }

    // 화상통화 중 녹음된 파일(MultipartFile)을 처리하는 메소드
    // 파일 기반 음성 인식도 동일한 포맷(LINEAR16, 16kHz)으로 처리하도록 수정
    public String speechToText(MultipartFile audioFile) throws IOException {
        if (audioFile.isEmpty()) {
            throw new IOException("전달받은 음성 데이터 audioFile이 빈 파일입니다.");
        }

        byte[] audioBytes = audioFile.getBytes();
        RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("ko-KR")
                .build();

        return convertSpeech(audioBytes, recognitionConfig);
    }

    // WebSocket 등에서 byte[] 형태로 전달된 음성 데이터를 LINEAR16 형식으로 처리하는 메소드
    public String speechToTextFromBytes(byte[] audioBytes) throws IOException {
        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("ko-KR")
                .build();

        String str = convertSpeech(audioBytes, config);
        if(str == null || str.isBlank()) return "";
        return gptCorrect(str);
    }

    private String gptCorrect(String speechText) {
        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content",
                "너는 한국어 받아쓰기 교정기다. 철자, 띄어쓰기, 조사, 숫자 표기, 고유명사 대문자, 문장부호를 자연스럽게 교정한다. " +
                        "원문의 의미를 바꾸지 않는다. 설명이나 메타 텍스트를 절대 추가하지 말고, 교정된 문장만 출력한다.");

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content",
                "다음 텍스트를 한국어 표준 맞춤법으로 자연스럽게 교정해줘. 결과는 교정된 문장만 한 줄로 반환해.\n\n" + speechText);

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("temperature", 0.0); // 교정은 결정적이게
            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(systemMsg);
            messages.add(userMsg);
            requestBody.set("messages", messages);

            String requestBodyString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);

            RequestBody body = RequestBody.create(
                    requestBodyString,
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + openaiApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    System.out.println("OpenAI API error: " + responseBody);
                    throw new IOException("Unexpected code " + response);
                }
                JsonNode responseJson = objectMapper.readTree(responseBody);
                String content = responseJson
                        .path("choices")
                        .get(0)
                        .path("message")
                        .path("content")
                        .asText()
                        .trim();

                return removeCodeBlock(content);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return speechText;
        }
    }

    // 음성 인식된 텍스트를 요약하는 기능 (OpenAI API 호출)
    public String textSummary(String speechText) {
        String prompt = String.format(
                "다음 텍스트를 간결하게 요약해줘:\n\n%s\n\n요약:",
                speechText
        );
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("temperature", 0.7);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);
            requestBody.set("messages", messages);

            String requestBodyString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
            System.out.println("Request Body: " + requestBodyString);

            RequestBody body = RequestBody.create(
                    requestBodyString,
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + openaiApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    System.out.println("OpenAI API error: " + responseBody);
                    throw new IOException("Unexpected code " + response);
                }
                JsonNode responseJson = objectMapper.readTree(responseBody);
                String content = responseJson
                        .path("choices")
                        .get(0)
                        .path("message")
                        .path("content")
                        .asText()
                        .trim();
                return removeCodeBlock(content);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private String removeCodeBlock(String content) {
        Pattern codeBlock = Pattern.compile("```[a-zA-Z]*\\n([\\s\\S]*?)\\n```");
        Matcher codeMatcher = codeBlock.matcher(content);
        if (codeMatcher.find()) {
            content = codeMatcher.group(1).trim();
        }
        // 양끝 큰따옴표만 단독으로 감싸진 경우 제거
        if ((content.startsWith("\"") && content.endsWith("\"")) ||
                (content.startsWith("“") && content.endsWith("”"))) {
            content = content.substring(1, content.length() - 1).trim();
        }
        return content;
    }
}
