package com.ai.service;

import com.ai.config.SystemPrompts;
import com.ai.tools.DateTimeTool;
import com.ai.tools.GoogleSearchTool;
import com.ai.tools.YouTubeSearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenRouterService {

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.url}")
    private String apiUrl;

    @Autowired
    private DateTimeTool dateTimeTool;

    @Autowired
    private GoogleSearchTool googleSearchTool;

    @Autowired
    private YouTubeSearchTool youTubeSearchTool;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Per-session chat memory
    private final Map<String, ChatMemory> memoryMap = new HashMap<>();

    // ── Memory ───────────────────────────────────────────────────────────────────
    private ChatMemory getMemory(String sessionId) {
        return memoryMap.computeIfAbsent(sessionId, id ->
                MessageWindowChatMemory.builder()
                        .maxMessages(20)
                        .build()
        );
    }

    // ── Main Entry Point ─────────────────────────────────────────────────────────
    public String callOpenRouter(String sessionId, String userPrompt, String systemPromptType) throws Exception {

        String systemPrompt = SystemPrompts.getPrompt(systemPromptType);

        // Save user message to memory
        ChatMemory memory = getMemory(sessionId);
        memory.add(sessionId, new UserMessage(userPrompt));

        // Build messages
        List<Map<String, Object>> messages = buildMessages(sessionId, systemPrompt);

        // MCQ and Performance don't need tools — clean JSON output only
        boolean useTools = !"mcq".equalsIgnoreCase(systemPromptType)
                && !"performance".equalsIgnoreCase(systemPromptType);

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "openai/gpt-3.5-turbo");
        requestBody.put("messages", messages);

        if (useTools) {
            List<Map<String, Object>> tools = List.of(
                    DateTimeTool.getToolDefinition(),
                    GoogleSearchTool.getToolDefinition(),
                    YouTubeSearchTool.getToolDefinition()
            );
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // First API call
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
        Map result = response.getBody();

        List choices = (List) result.get("choices");
        Map choice = (Map) choices.get(0);
        Map responseMessage = (Map) choice.get("message");
        String finishReason = (String) choice.get("finish_reason");

        System.out.println("Finish Reason : " + finishReason);

        // Handle tool calls (only for general mode)
        List toolCalls = (List) responseMessage.get("tool_calls");
        if (useTools && toolCalls != null && !toolCalls.isEmpty()) {
            return handleToolCalls(sessionId, memory, messages, toolCalls, headers);
        }

        // Direct reply
        String aiReply = responseMessage.get("content").toString();
        memory.add(sessionId, new AssistantMessage(aiReply));

        System.out.println("Direct Reply  : " + aiReply.substring(0, Math.min(120, aiReply.length())));
        return aiReply;
    }

    // ── Tool Call Handler ─────────────────────────────────────────────────────────
    private String handleToolCalls(
            String sessionId,
            ChatMemory memory,
            List<Map<String, Object>> messages,
            List toolCalls,
            HttpHeaders headers
    ) throws Exception {

        System.out.println("=== TOOL CALLED ===");

        // Add assistant tool-call message
        messages.add(Map.of(
                "role", "assistant",
                "content", "",
                "tool_calls", toolCalls
        ));

        // Execute each tool
        for (Object toolCallObj : toolCalls) {
            Map toolCall     = (Map) toolCallObj;
            String toolCallId    = (String) toolCall.get("id");
            Map function         = (Map) toolCall.get("function");
            String functionName  = (String) function.get("name");
            String argumentsJson = (String) function.get("arguments");

            System.out.println("Tool Name     : " + functionName);
            System.out.println("Arguments     : " + argumentsJson);

            String toolResult = executeToolCall(functionName, argumentsJson);
            System.out.println("Tool Result   : " + toolResult.substring(0, Math.min(200, toolResult.length())));

            messages.add(Map.of(
                    "role", "tool",
                    "tool_call_id", toolCallId,
                    "content", toolResult
            ));
        }

        // Second API call — no tools, just final answer
        Map<String, Object> secondBody = new HashMap<>();
        secondBody.put("model", "openai/gpt-3.5-turbo");
        secondBody.put("messages", messages);

        HttpEntity<Map<String, Object>> secondEntity = new HttpEntity<>(secondBody, headers);
        ResponseEntity<Map> secondResponse = restTemplate.postForEntity(apiUrl, secondEntity, Map.class);

        Map secondResult   = secondResponse.getBody();
        List secondChoices = (List) secondResult.get("choices");
        Map secondChoice   = (Map) secondChoices.get(0);
        Map secondMessage  = (Map) secondChoice.get("message");

        String aiReply = secondMessage.get("content").toString();
        memory.add(sessionId, new AssistantMessage(aiReply));

        System.out.println("Tool Reply    : " + aiReply.substring(0, Math.min(120, aiReply.length())));
        return aiReply;
    }

    // ── Build Messages Array ──────────────────────────────────────────────────────
    private List<Map<String, Object>> buildMessages(String sessionId, String systemPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System prompt always first
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // Chat history from memory
        ChatMemory memory = getMemory(sessionId);
        List<Message> history = memory.get(sessionId);

        for (Message msg : history) {
            String role = switch (msg.getMessageType()) {
                case USER      -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM    -> "system";
                default        -> "user";
            };
            messages.add(Map.of("role", role, "content", msg.getText()));
        }

        return messages;
    }

    // ── Tool Router ───────────────────────────────────────────────────────────────
    private String executeToolCall(String functionName, String argumentsJson) throws Exception {
        return switch (functionName) {

            case "getCurrentDateTime" -> dateTimeTool.getCurrentDateTime();

            case "googleSearch" -> {
                Map<String, String> args = objectMapper.readValue(argumentsJson, Map.class);
                String query = args.get("query");
                System.out.println("Google Query  : " + query);
                yield googleSearchTool.search(query);
            }

            case "youtubeSearch" -> {
                Map<String, String> args = objectMapper.readValue(argumentsJson, Map.class);
                String query = args.get("query");
                System.out.println("YouTube Query : " + query);
                yield youTubeSearchTool.search(query);
            }

            default -> "Tool not found: " + functionName;
        };
    }
}