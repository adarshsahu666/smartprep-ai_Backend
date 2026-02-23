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
public class GoogleSearchTool {

    @Value("${google.search.api.key}")
    private String apiKey;

    @Value("${google.search.cx}")
    private String cx;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public String search(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.googleapis.com/customsearch/v1"
                    + "?key=" + apiKey
                    + "&cx=" + cx
                    + "&q=" + encoded
                    + "&num=3";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("items");

            if (!items.isArray() || items.isEmpty()) {
                return "No Google results found for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[Google Search Results for: ").append(query).append("]\n\n");

            for (int i = 0; i < Math.min(3, items.size()); i++) {
                JsonNode item = items.get(i);
                String title   = item.path("title").asText("");
                String snippet = item.path("snippet").asText("").replace("\n", " ");
                String link    = item.path("link").asText("");

                sb.append(i + 1).append(". ").append(title).append("\n");
                sb.append("   ").append(snippet, 0, Math.min(snippet.length(), 200)).append("\n");
                sb.append("   URL: ").append(link).append("\n\n");
            }

            return sb.toString().trim();

        } catch (Exception e) {
            return "Google search failed: " + e.getMessage();
        }
    }

    public static Map<String, Object> getToolDefinition() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");

        Map<String, Object> function = new HashMap<>();
        function.put("name", "googleSearch");
        function.put("description",
                "Search Google for current information, news, documentation, tutorials, " +
                        "latest software features, recent events, or any web content.");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "The search query to look up on Google");
        properties.put("query", queryProp);

        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));
        function.put("parameters", parameters);
        tool.put("function", function);

        return tool;
    }
}