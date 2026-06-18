// src/main/java/carrental/controller/ChatController.java
package carrental.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Value("${OPENROUTER_API_KEY:}")
    private String openRouterKey;

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    @PostMapping("/chat")
    public ResponseEntity<Object> chat(@RequestBody Map<String, Object> payload) {
        try {
            // Build headers with API key (fallback to empty if not set)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (openRouterKey != null && !openRouterKey.isBlank()) {
                headers.setBearerAuth(openRouterKey.trim());
            }

            // List of reliable free models to try in order
        String[] fallbackModels = {
            "huggingfaceh4/zephyr-7b-beta:free",
            "google/gemma-2-9b-it:free",
            "meta-llama/llama-3-8b-instruct:free",
            "mistralai/mistral-7b-instruct:free"
        };

        RestTemplate restTemplate = new RestTemplate();
        Exception lastException = null;

        for (String model : fallbackModels) {
            try {
                // Override the model in the payload
                payload.put("model", model);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        OPENROUTER_URL,
                        HttpMethod.POST,
                        request,
                        String.class
                );

                String body = response.getBody();
                if (body != null && body.trim().startsWith("<!DOCTYPE")) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "OpenRouter returned HTML. Possible authentication issue."));
                }

                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> jsonResponse = mapper.readValue(body, Map.class);
                return ResponseEntity.status(response.getStatusCode()).body(jsonResponse);

            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                lastException = e;
                // If the key is outright invalid (401), stop immediately
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    break;
                }
                // Otherwise (402 Payment Required, 404 Not Found), continue to the next model
            } catch (Exception e) {
                lastException = e;
                // Other exceptions (network errors, etc), try the next model
            }
        }

        // If all models fail, return the last exception encountered
        String errorMsg = lastException != null ? lastException.getMessage() : "Unknown error";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "All fallback models failed. Last error: " + errorMsg));
        
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process chat request: " + e.getMessage()));
        }
    }
}
