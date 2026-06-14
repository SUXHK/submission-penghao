package com.defecttriage.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekClient {

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final HttpClient client;

    public DeepSeekClient(@Value("${deepseek.api.key}") String apiKey,
                          @Value("${deepseek.api.url}") String apiUrl,
                          @Value("${deepseek.model}") String model,
                          @Value("${deepseek.timeout}") long timeout) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
    }

    public String callDeepSeek(String systemPrompt, String userPrompt) {
        Exception lastError = null;
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (attempt > 0) Thread.sleep(2000); // retry delay
                String jsonBody = buildRequestBody(systemPrompt, userPrompt);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String content = extractContent(response.body());
                if (content != null && !content.isBlank()) return content;
                lastError = new RuntimeException("AI 返回空内容");
            } catch (Exception e) {
                lastError = e;
            }
        }
        return "AI 服务暂时不可用，已自动重试 " + maxRetries + " 次。错误: " + (lastError != null ? lastError.getMessage() : "未知");
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        return """
                {
                    "model": "%s",
                    "messages": [
                        {"role": "system", "content": "%s"},
                        {"role": "user", "content": "%s"}
                    ],
                    "temperature": 0.7,
                    "max_tokens": 2000
                }
                """.formatted(model, escapeJson(systemPrompt), escapeJson(userPrompt));
    }

    private String extractContent(String responseBody) {
        try {
            // Simple JSON parsing without external libraries
            int contentIdx = responseBody.indexOf("\"content\"");
            if (contentIdx < 0) return "AI 返回格式异常";
            int start = responseBody.indexOf("\"", contentIdx + 10) + 1;
            int end = responseBody.indexOf("\"", start);
            if (start < 1 || end < 0) return "AI 返回格式异常";
            return responseBody.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        } catch (Exception e) {
            return "AI 返回解析失败: " + e.getMessage();
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
