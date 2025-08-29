package com.garret.dreammoa.domain.controller.stt;

import com.garret.dreammoa.domain.service.stt.SpeechService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/stt")
public class SpeechController {
    private final SpeechService speechService;

    public SpeechController(SpeechService speechService) {
        this.speechService = speechService;
    }

    @PostMapping("/speech-to-text")
    public ResponseEntity<String> speechToText(@RequestParam("audioFile") MultipartFile audioFile) {
        try {
            String transcript = speechService.speechToText(audioFile);
            return ResponseEntity.ok(transcript);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
