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
    public ResponseEntity<String> chat(@RequestBody Map<String, Object> payload) {
        // Build headers with API key
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openRouterKey);

        // Forward the incoming payload directly to OpenRouter
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                OPENROUTER_URL,
                HttpMethod.POST,
                request,
                String.class
        );
        // Return OpenRouter's response as‑is to the front‑end
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}
