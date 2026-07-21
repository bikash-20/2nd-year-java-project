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
import carrental.service.CarRentalSystem;
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

    @Autowired
    private CarRentalSystem carRentalSystem;

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
                    // Server-side HTML safety: strip any raw HTML tags from the AI's response
                    // before sending to the frontend. Free-tier models sometimes output
                    // <li>, <p>, <strong> etc. despite instructions to use plain markdown.
                    stripHtmlFromResponse(jsonResponse);
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
                    .append(" | Notes: ").append(r.getNotes() != null && !r.getNotes().isBlank() ? r.getNotes() : "None")
                    .append("\n");
        }
        sb.append("\n");

        // --- Dashboard insights ---
        try {
            java.util.Map<String, Object> insights = carRentalSystem.getDashboardInsights();
            sb.append("DASHBOARD INSIGHTS:\n");
            sb.append("- Most Rented Car: ").append(insights.get("mostRentedCar"))
                    .append(" (").append(insights.get("mostRentedCount")).append(" rentals)\n");
            sb.append("- Top Customer: ").append(insights.get("topCustomer"))
                    .append(" (").append(insights.get("topCustomerRentals")).append(" rentals)\n");
            sb.append("- Busiest Month: ").append(insights.get("busiestMonth"))
                    .append(" ($").append(fmt((Double) insights.get("busiestMonthRevenue"))).append(")\n");
            sb.append("- Average Rental Duration: ").append(insights.get("avgRentalDays")).append(" days\n");
            sb.append("- Total Unique Customers: ").append(insights.get("totalCustomers")).append("\n");
            sb.append("\n");
        } catch (Exception e) {
            // silently skip insights if unavailable
        }

        // --- Customer ratings ---
        try {
            java.util.Map<String, Double> carRatings = carRentalSystem.getAllCarRatings();
            sb.append("CAR RATINGS:\n");
            if (carRatings.isEmpty()) {
                sb.append("  (no ratings yet)\n");
            }
            for (java.util.Map.Entry<String, Double> entry : carRatings.entrySet()) {
                sb.append("  * Car ").append(entry.getKey())
                        .append(": ").append(entry.getValue()).append(" / 5.0 stars\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            // silently skip ratings if unavailable
        }

        // --- Available features (for feature questions) ---
        sb.append("AVAILABLE FEATURES YOU CAN HELP WITH:\n");
        sb.append("- Dashboard: View fleet stats, revenue, and insights\n");
        sb.append("- Rent a Car: Date-based booking with live price preview and double-booking prevention\n");
        sb.append("- Return a Car: One-click return of any active rental\n");
        sb.append("- Rental History: Full record of active and completed rentals\n");
        sb.append("- PDF Receipts: Download professional PDF receipt for any booking\n");
        sb.append("- Revenue Charts: Monthly revenue bar chart + category doughnut chart\n");
        sb.append("- Manage Fleet: Add new cars or delete existing ones from the UI\n");
        sb.append("- Customer Ratings: Rate returned rentals from 1-5 stars; view average rating per car\n");
        sb.append("- Dashboard Insights: See most rented car, top customer, busiest month, and avg rental duration\n");
        sb.append("- CSV Export: Download complete rental history as a CSV file\n");
        sb.append("- Booking Notes: Customers can add special requests (e.g. baby seat, preferred color) when renting\n");
        sb.append("- Customer Profiles: Search customers by phone number and view their complete rental history, stats, and ratings\n");
        sb.append("- Activity Log: Track all actions performed in the system — car added, deleted, rented, returned with timestamps\n");
        sb.append("- Loyalty Discounts: Returning customers get 5% off after 3 rentals, 10% off after 5, 15% off after 10\n");
        sb.append("- Rental Agreement: View printable rental agreement with full terms and conditions\n");
        sb.append("- About Page: Developer info for Bikash Talukder — LinkedIn and GitHub profiles\n");
        sb.append("- Theme Toggle: Switch between dark and light mode from the navigation bar\n");
        sb.append("- AI Assistant: Ask questions about any of the above features\n");

        // --- Loyalty info for returning customers ---
        try {
            Map<String, Integer> customerRentalCounts = new HashMap<>();
            for (Rental r : allRentals) {
                if (r.getCustomer() != null) {
                    String name = r.getCustomer().getName();
                    customerRentalCounts.merge(name != null ? name : "Unknown", 1, Integer::sum);
                }
            }
            sb.append("CUSTOMER LOYALTY TIERS:\n");
            if (customerRentalCounts.isEmpty()) {
                sb.append("  (no customers yet)\n");
            }
            for (Map.Entry<String, Integer> entry : customerRentalCounts.entrySet()) {
                int count = entry.getValue();
                String discount = count >= 10 ? "15%" : count >= 5 ? "10%" : count >= 3 ? "5%" : "0%";
                sb.append("  * ").append(entry.getKey()).append(": ").append(count)
                        .append(" rental(s) — Loyalty Discount: ").append(discount).append("\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            // silently skip
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

    /**
     * Recursively strips raw HTML tags from the AI response content.
     * Free-tier models sometimes output <li>, <p>, <strong> etc. despite
     * being instructed to use plain markdown. This server-side safety net
     * removes them so the frontend never has to deal with raw HTML.
     */
    @SuppressWarnings("unchecked")
    private void stripHtmlFromResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return;
            for (Map<String, Object> choice : choices) {
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message == null) continue;
                String content = (String) message.get("content");
                if (content == null) continue;
                // Strip ALL HTML tags using regex
                content = content.replaceAll("<[^>]*>", "");
                message.put("content", content);
            }
        } catch (Exception e) {
            // silently skip — don't break the response
        }
    }
}