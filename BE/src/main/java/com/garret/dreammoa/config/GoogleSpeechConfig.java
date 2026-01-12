//package com.garret.dreammoa.config;
//
//import com.google.cloud.speech.v1.SpeechClient;
//import com.google.cloud.speech.v1.SpeechSettings;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.io.IOException;
//import java.nio.file.Path;
//
//@Configuration
//public class GoogleSpeechConfig {
//
//    @Bean
//    public SpeechClient speechClient() throws IOException {
//
//        // 1️⃣ 환경 변수에서 GOOGLE_APPLICATION_CREDENTIALS 가져오기
//        String googleCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
//
//        if (googleCredentialsPath == null || googleCredentialsPath.isEmpty()) {
//            throw new IOException("❌ 환경 변수 'GOOGLE_APPLICATION_CREDENTIALS'가 설정되지 않았습니다.");
//        }
//
//        // 3️⃣ SpeechClient 생성
//        return SpeechClient.create(SpeechSettings.newBuilder().build());
//    }
//}
