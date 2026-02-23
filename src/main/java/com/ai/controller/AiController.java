package com.ai.controller;

import com.ai.service.OpenRouterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = "http://localhost:5173")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "https://smartprep-ai.up.railway.app"
})
public class AiController {

    private final OpenRouterService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiController(OpenRouterService service) {
        this.service = service;
    }

    // ── MCQ Generation ──────────────────────────────────────────────────────────
    @PostMapping("/mcq")
    public ResponseEntity<?> generateMCQ(@RequestBody Map<String, String> body) {
        try {
            String sessionId   = body.getOrDefault("sessionId", "default");
            String topic       = body.getOrDefault("topic", "Java");
            String count       = body.getOrDefault("count", "5");
            String difficulty  = body.getOrDefault("difficulty", "medium");

            String prompt = String.format(
                    "Generate exactly %s MCQ questions on the topic: '%s'. Difficulty level: %s.",
                    count, topic, difficulty
            );

            String aiResponse = service.callOpenRouter(sessionId, prompt, "mcq");

            // Strip markdown code fences if model wraps JSON
            String cleaned = aiResponse.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            Object parsed = objectMapper.readValue(cleaned, Object.class);
            return ResponseEntity.ok(parsed);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── General Chat ─────────────────────────────────────────────────────────────
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) {
        try {
            String sessionId        = body.getOrDefault("sessionId", "default");
            String prompt           = body.get("prompt");
            String systemPromptType = body.getOrDefault("systemPromptType", "general");

            String aiResponse = service.callOpenRouter(sessionId, prompt, systemPromptType);
            return ResponseEntity.ok(Map.of("reply", aiResponse));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Performance Feedback ──────────────────────────────────────────────────────
    @PostMapping("/performance")
    public ResponseEntity<?> performance(@RequestBody Map<String, String> body) {
        try {
            String sessionId   = body.getOrDefault("sessionId", "default");
            String topic       = body.getOrDefault("topic", "Unknown");
            String correct     = body.getOrDefault("correct", "0");
            String total       = body.getOrDefault("total", "5");
            String difficulty  = body.getOrDefault("difficulty", "medium");
            String timePerQ    = body.getOrDefault("timePerQuestion", "30");

            String prompt = String.format(
                    "Quiz Performance Report:\n" +
                            "- Topic: %s\n" +
                            "- Total Questions: %s\n" +
                            "- Correct Answers: %s\n" +
                            "- Difficulty Level: %s\n" +
                            "- Time Per Question: %s seconds\n\n" +
                            "Please give a detailed performance review and study recommendations.",
                    topic, total, correct, difficulty, timePerQ
            );

            String feedback = service.callOpenRouter(sessionId, prompt, "performance");
            return ResponseEntity.ok(Map.of("feedback", feedback));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}