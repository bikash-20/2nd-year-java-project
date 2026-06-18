// src/main/java/carrental/controller/ChatController.java
package carrental.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import carrental.repository.CarRepository;
import carrental.repository.RentalRepository;
import carrental.model.Car;
import carrental.model.Rental;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Value("${OPENROUTER_API_KEY:}")
    private String openRouterKey;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private RentalRepository rentalRepository;

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    @PostMapping("/chat")
    public ResponseEntity<Object> chat(@RequestBody Map<String, Object> payload) {
        try {
            // Build Context Injection String from live database
            List<Car> availableCars = carRepository.findByAvailableTrue();
            List<Rental> activeRentals = rentalRepository.findByStatus(Rental.Status.ACTIVE);
            
            double totalRevenue = rentalRepository.findAll().stream()
                .mapToDouble(Rental::getTotalPrice).sum();
            
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("LIVE SYSTEM DATA (Use this to answer the user's question accurately):\n");
            contextBuilder.append("- Total Revenue: $").append(String.format("%.2f", totalRevenue)).append("\n");
            contextBuilder.append("- Active Rentals: ").append(activeRentals.size()).append("\n");
            contextBuilder.append("- Cars Currently Available to Rent (").append(availableCars.size()).append(" total):\n");
            for (Car c : availableCars) {
                contextBuilder.append("  * ").append(c.getBrand()).append(" ").append(c.getModel())
                              .append(" ($").append(c.getBasePricePerDay()).append("/day)\n");
            }
            
            Map<String, String> liveContextMsg = new HashMap<>();
            liveContextMsg.put("role", "system");
            liveContextMsg.put("content", contextBuilder.toString());
            
            // Inject into messages array
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) payload.get("messages");
            if (messages == null) {
                messages = new ArrayList<>();
                payload.put("messages", messages);
            }
            messages.add(0, liveContextMsg);

            // Build headers with API key (fallback to empty if not set)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (openRouterKey != null && !openRouterKey.isBlank()) {
                headers.setBearerAuth(openRouterKey.trim());
            }

            // List of reliable free models to try in order
        String[] fallbackModels = {
            "openrouter/free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "meta-llama/llama-3.2-3b-instruct:free",
            "google/gemma-4-31b-it:free"
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
