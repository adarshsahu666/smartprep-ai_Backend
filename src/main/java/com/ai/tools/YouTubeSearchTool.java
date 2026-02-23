package com.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class YouTubeSearchTool {

    @Value("${youtube.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public String search(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.googleapis.com/youtube/v3/search"
                    + "?key=" + apiKey
                    + "&q=" + encoded
                    + "&part=snippet"
                    + "&type=video"
                    + "&maxResults=3"
                    + "&order=relevance";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("items");

            if (!items.isArray() || items.isEmpty()) {
                return "No YouTube videos found for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[YouTube Results for: ").append(query).append("]\n\n");

            for (int i = 0; i < Math.min(3, items.size()); i++) {
                JsonNode item     = items.get(i);
                JsonNode snippet  = item.path("snippet");
                String videoId    = item.path("id").path("videoId").asText("");
                String title      = snippet.path("title").asText("");
                String channel    = snippet.path("channelTitle").asText("");
                String description = snippet.path("description").asText("").replace("\n", " ");
                String publishedAt = snippet.path("publishedAt").asText("").substring(0, 10);

                sb.append(i + 1).append(". ").append(title).append("\n");
                sb.append("   Channel: ").append(channel).append(" | Published: ").append(publishedAt).append("\n");
                sb.append("   ").append(description, 0, Math.min(description.length(), 150)).append("\n");
                sb.append("   URL: https://www.youtube.com/watch?v=").append(videoId).append("\n\n");
            }

            return sb.toString().trim();

        } catch (Exception e) {
            return "YouTube search failed: " + e.getMessage();
        }
    }

    public static Map<String, Object> getToolDefinition() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");

        Map<String, Object> function = new HashMap<>();
        function.put("name", "youtubeSearch");
        function.put("description",
                "Search YouTube for video tutorials, courses, tech talks, or any video content. " +
                        "Use when the user asks for videos, tutorials, or how-to guides.");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "The YouTube search query");
        properties.put("query", queryProp);

        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));
        function.put("parameters", parameters);
        tool.put("function", function);

        return tool;
    }
}