package com.ai.tools;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DateTimeTool {

    public String getCurrentDateTime() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    // Tool definition to send to OpenRouter
    public static java.util.Map<String, Object> getToolDefinition() {
        return java.util.Map.of(
                "type", "function",
                "function", java.util.Map.of(
                        "name", "getCurrentDateTime",
                        "description", "Returns the current date and time. Use this when the user asks about current date, time, day, or year.",
                        "parameters", java.util.Map.of(
                                "type", "object",
                                "properties", java.util.Map.of(),
                                "required", java.util.List.of()
                        )
                )
        );
    }
}