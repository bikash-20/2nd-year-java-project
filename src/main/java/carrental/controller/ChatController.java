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
import java.util.Comparator;

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

    // Ordered strongest-first: weaker/smaller models are more likely to ignore
    // injected context and hallucinate numbers, so they are tried last.
    private static final String[] FALLBACK_MODELS = {
            "meta-llama/llama-3.3-70b-instruct:free",
            "openrouter/free",
            "google/gemma-2-9b-it:free",
            "meta-llama/llama-3.2-3b-instruct:free"
    };

    @PostMapping("/chat")
    public ResponseEntity<Object> chat(@RequestBody Map<String, Object> payload) {
        try {
            String liveData = buildLiveContext();

            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) payload.get("messages");
            if (messages == null) {
                messages = new ArrayList<>();
            } else {
                // Defensive copy: never mutate / depend on a possibly-immutable
                // list deserialized by Jackson, and never trust its exact type.
                messages = new ArrayList<>(messages);
            }
            payload.put("messages", messages);

            // Merge live data into the FIRST existing system message instead of
            // adding a second separate system message. Smaller free-tier models
            // are noticeably worse at reconciling two competing system prompts;
            // merging into one avoids that confusion entirely.
            boolean merged = false;
            for (Map<String, String> m : messages) {
                if ("system".equals(m.get("role"))) {
                    String existing = m.getOrDefault("content", "");
                    m.put("content", existing + "\n\n" + liveData);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", liveData);
                messages.add(0, systemMsg);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (openRouterKey != null && !openRouterKey.isBlank()) {
                headers.setBearerAuth(openRouterKey.trim());
            }

            RestTemplate restTemplate = new RestTemplate();
            Exception lastException = null;

            for (String model : FALLBACK_MODELS) {
                try {
                    payload.put("model", model);
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

                    ResponseEntity<String> response = restTemplate.exchange(
                            OPENROUTER_URL,
                            HttpMethod.POST,
                            request,
                            String.class);

                    String body = response.getBody();
                    if (body != null && body.trim().startsWith("<!DOCTYPE")) {
                        continue; // try next model instead of failing outright
                    }

                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> jsonResponse = mapper.readValue(body, Map.class);
                    return ResponseEntity.status(response.getStatusCode()).body(jsonResponse);

                } catch (org.springframework.web.client.HttpStatusCodeException e) {
                    lastException = e;
                    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        break; // bad API key, no point trying other models
                    }
                    // 402 / 404 / 429 etc -> try next model
                } catch (Exception e) {
                    lastException = e;
                    // network errors, parse errors, etc -> try next model
                }
            }

            String errorMsg = lastException != null ? lastException.getMessage() : "Unknown error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "All fallback models failed. Last error: " + errorMsg));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process chat request: " + e.getMessage()));
        }
    }

    /**
     * Builds a single comprehensive LIVE SYSTEM DATA block covering everything
     * the assistant might be asked about: current fleet + pricing, category
     * breakdown, active rentals, full rental history, and revenue totals.
     * Dataset is small (well under 100 rentals), so sending the full history
     * on every request is safe and keeps answers accurate instead of relying
     * on the model to remember/guess numbers from earlier turns.
     */
    private String buildLiveContext() {
        List<Car> allCars = carRepository.findAll();
        List<Rental> allRentals = rentalRepository.findAll();

        List<Car> availableCars = allCars.stream()
                .filter(Car::isAvailable)
                .toList();

        List<Rental> activeRentals = allRentals.stream()
                .filter(r -> r.getStatus() == Rental.Status.ACTIVE)
                .toList();

        List<Rental> returnedRentals = allRentals.stream()
                .filter(r -> r.getStatus() == Rental.Status.RETURNED)
                .toList();

        double totalRevenue = allRentals.stream()
                .mapToDouble(Rental::getTotalPrice)
                .sum();

        StringBuilder sb = new StringBuilder();
        sb.append(
                "LIVE SYSTEM DATA (authoritative — always use these exact figures, never estimate or recalculate):\n\n");

        // --- Revenue summary ---
        sb.append("REVENUE SUMMARY:\n");
        sb.append("- Total Revenue (all rentals, all time): $").append(fmt(totalRevenue)).append("\n");
        sb.append("- Total Rentals Recorded: ").append(allRentals.size()).append("\n");
        sb.append("- Active Rentals: ").append(activeRentals.size()).append("\n");
        sb.append("- Returned/Completed Rentals: ").append(returnedRentals.size()).append("\n\n");

        // --- Fleet ---
        sb.append("FULL FLEET (").append(allCars.size()).append(" cars total):\n");
        for (Car c : allCars) {
            sb.append("  * [").append(c.getCarId()).append("] ")
                    .append(c.getBrand()).append(" ").append(c.getModel())
                    .append(" | Category: ").append(c.getCategory())
                    .append(" | $").append(fmt(c.getBasePricePerDay())).append("/day")
                    .append(" | Status: ").append(c.isAvailable() ? "Available" : "Currently Rented")
                    .append("\n");
        }
        sb.append("\n");

        sb.append("CURRENTLY AVAILABLE TO RENT (").append(availableCars.size()).append(" cars):\n");
        if (availableCars.isEmpty()) {
            sb.append("  (none — entire fleet is currently rented out)\n");
        }
        for (Car c : availableCars) {
            sb.append("  * ").append(c.getBrand()).append(" ").append(c.getModel())
                    .append(" (").append(c.getCategory()).append(") — $")
                    .append(fmt(c.getBasePricePerDay())).append("/day\n");
        }
        sb.append("\n");

        // --- Category breakdown (by rental count, matches the dashboard donut chart)
        // ---
        Map<String, Long> categoryCounts = new HashMap<>();
        for (Rental r : allRentals) {
            if (r.getCar() != null && r.getCar().getCategory() != null) {
                categoryCounts.merge(r.getCar().getCategory(), 1L, Long::sum);
            }
        }
        sb.append("RENTALS BY CATEGORY:\n");
        if (categoryCounts.isEmpty()) {
            sb.append("  (no rentals yet)\n");
        }
        for (Map.Entry<String, Long> e : categoryCounts.entrySet()) {
            sb.append("  * ").append(e.getKey()).append(": ").append(e.getValue()).append(" rental(s)\n");
        }
        sb.append("\n");

        // --- Active rentals detail ---
        sb.append("ACTIVE RENTALS (currently out, not yet returned):\n");
        if (activeRentals.isEmpty()) {
            sb.append("  (none — no cars are currently rented out)\n");
        }
        for (Rental r : activeRentals) {
            sb.append("  * Rental #").append(r.getId())
                    .append(" | Car: ").append(carLabel(r))
                    .append(" | Customer: ").append(customerLabel(r))
                    .append(" | ").append(r.getStartDateStr()).append(" to ").append(r.getEndDateStr())
                    .append(" | ").append(r.getDays()).append(" day(s)")
                    .append(" | Total: $").append(fmt(r.getTotalPrice()))
                    .append("\n");
        }
        sb.append("\n");

        // --- Full rental history, most recent first ---
        sb.append("FULL RENTAL HISTORY (all rentals, most recent first):\n");
        if (allRentals.isEmpty()) {
            sb.append("  (no rentals have been recorded yet)\n");
        }
        List<Rental> sortedHistory = new ArrayList<>(allRentals);
        sortedHistory.sort(Comparator.comparing(
                (Rental r) -> r.getStartDate() != null ? r.getStartDate() : java.time.LocalDate.MIN).reversed());
        for (Rental r : sortedHistory) {
            sb.append("  * Rental #").append(r.getId())
                    .append(" | Car: ").append(carLabel(r))
                    .append(" | Customer: ").append(customerLabel(r))
                    .append(" | ").append(r.getStartDateStr()).append(" to ").append(r.getEndDateStr())
                    .append(" | ").append(r.getDays()).append(" day(s)")
                    .append(" | Total: $").append(fmt(r.getTotalPrice()))
                    .append(" | Status: ").append(r.getStatus())
                    .append("\n");
        }

        return sb.toString();
    }

    private String carLabel(Rental r) {
        return r.getCar() != null ? r.getCar().getBrand() + " " + r.getCar().getModel() : "Unknown car";
    }

    private String customerLabel(Rental r) {
        if (r.getCustomer() == null)
            return "Unknown customer";
        String name = r.getCustomer().getName();
        return (name != null && !name.isBlank()) ? name : "Customer";
    }

    private String fmt(double value) {
        return String.format("%.2f", value);
    }
}