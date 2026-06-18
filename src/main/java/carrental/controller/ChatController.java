// src/main/java/carrental/controller/ChatController.java
package carrental.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Value("${OPENROUTER_API_KEY:}")
    private String openRouterKey;

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    @PostMapping("/chat")
    public ResponseEntity<Object> chat(@RequestBody Map<String, Object> payload) {
        // Build headers with API key (fallback to empty if not set)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (openRouterKey != null && !openRouterKey.isBlank()) {
            headers.setBearerAuth(openRouterKey);
        }

        // Forward the incoming payload to OpenRouter with robust error handling
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    OPENROUTER_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            // If the response body looks like HTML (starts with '<'), treat as error
            String body = response.getBody();
            if (body != null && body.trim().startsWith("<!DOCTYPE")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "OpenRouter returned non-JSON response. Possible authentication issue."));
            }
            // Return OpenRouter's response as parsed JSON object to avoid double serialization issues
            return ResponseEntity.status(response.getStatusCode()).body(body);
        } catch (Exception e) {
            // Safely serialize the error as JSON using Spring's built-in Jackson
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to contact OpenRouter: " + e.getMessage()));
        }
    }}
