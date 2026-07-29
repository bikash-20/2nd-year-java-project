// src/main/java/carrental/controller/ChatController.java
package carrental.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import carrental.repository.CarRepository;
import carrental.repository.RentalRepository;
import carrental.service.CarRentalSystem;
import carrental.model.Car;
import carrental.model.Rental;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Value("${OPENROUTER_API_KEY:}")
    private String openRouterKey;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private CarRentalSystem carRentalSystem;

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String SITE_URL = "https://rentify-ifs4.onrender.com";
    private static final String SITE_NAME = "Rentify";

    // Ordered strongest-first. Weaker/smaller models are more likely to ignore
    // injected context and hallucinate numbers, so they are tried last.
    private static final String[] FALLBACK_MODELS = {
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemma-2-9b-it:free",
            "meta-llama/llama-3.2-3b-instruct:free"
    };

    // ── Bilingual system prompt (English + Bengali) ─────────────────────────
    // The assistant always responds in the user's selected language.
    // Bengali numerals and currency are used when language=bn.
    private static final String SYSTEM_PROMPT_BASE =
            "You are the official AI assistant for Rentify, a car rental web " +
            "application built by Bikash Talukder. You are ALSO fluent in " +
            "Bengali (Bangla) and English, and you MUST reply in the language " +
            "the user picks (or writes in). If unclear, mirror the user's " +
            "language.\n\n" +

            "CRITICAL RULES:\n" +
            "1. When the user sends a greeting, greet them back, introduce " +
            "yourself as the Rentify assistant built by Bikash, briefly list " +
            "what you can help with, and ask what they would like to know.\n" +
            "2. LIVE DATA: The system will inject real-time database stats " +
            "(revenue, available cars, active rentals) into the conversation " +
            "before the user's message. Use this live data confidently and " +
            "do NOT recalculate or estimate — always quote the exact figures.\n" +
            "3. LANGUAGE: Detect the user's language. If the user writes in " +
            "Bengali (বাংলা), you MUST reply entirely in Bengali using " +
            "natural, polite Bangla. If the user writes in English, reply in " +
            "English. You may also mix in a few Bangla words for warmth when " +
            "appropriate, but the primary language MUST match the user.\n" +
            "4. FOR Bengali: write Bengali in its native script (e.g. " +
            "'গাড়ি', 'ভাড়া', 'মোট আয়'). Use Bengali-style numerals " +
            "(০১২৩৪৫৬৭৮৯) ONLY if the user is clearly using them; otherwise " +
            "standard digits are fine. Currency may be written as 'টাকা' " +
            "(taka) when appropriate, but the LIVE DATA already uses '$' — " +
            "keep '$' when quoting figures from LIVE DATA so numbers stay " +
            "accurate.\n" +
            "5. FOR English: respond naturally in clear English.\n" +
            "6. FORMATTING — ABSOLUTE RULE: Respond using ONLY plain " +
            "Markdown. You are FORBIDDEN from outputting ANY HTML tags " +
            "WHATSOEVER — including <li>, <p>, <strong>, <b>, <i>, <ul>, " +
            "<ol>, <div>, <span>, <br>, <a>, <table>, <tr>, <td>. Use " +
            "Markdown lists (- or 1.) and **bold** / *italic* instead. The " +
            "server strips HTML, so Markdown is the only thing that will " +
            "render correctly.\n" +
            "7. You MAY use $LaTeX$ math notation for calculations.\n" +
            "8. Out-of-context questions: answer politely but remind the user " +
            "you are the Rentify assistant and offer relevant help.";

    // ── Lightweight per-IP token-bucket rate limiter (no extra dep) ─────────
    private static final int RATE_LIMIT_PER_HOUR = 30; // generous for demo
    private static final long RATE_LIMIT_WINDOW_MS = 60L * 60L * 1000L;
    private static final Map<String, long[]> RATE_BUCKETS = new ConcurrentHashMap<>();

    @PostMapping("/chat")
    public ResponseEntity<Object> chat(@RequestBody Map<String, Object> payload,
                                       HttpServletRequest request) {
        // ── Rate limit by client IP ──
        String ip = clientIp(request);
        if (!allowRequest(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "Too many requests. Please wait a minute and try again.",
                            "language", normalizeLanguage(payload.get("language"))));
        }

        // ── Validate language (en | bn, default en) ──
        String language = normalizeLanguage(payload.get("language"));

        try {
            String liveData = buildLiveContext();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages =
                    (List<Map<String, Object>>) payload.get("messages");
            if (messages == null) {
                messages = new ArrayList<>();
            } else {
                // Defensive copy so we never mutate a possibly-immutable list
                // deserialized by Jackson, and never trust its exact type.
                messages = new ArrayList<>(messages);
            }
            payload.put("messages", messages);

            // Build the effective system prompt: base + live DB context + a
            // short language reminder so the model stays in the right
            // language for the entire conversation.
            String systemPrompt = SYSTEM_PROMPT_BASE
                    + "\n\n[LANGUAGE FOR THIS REQUEST: "
                    + (language.equals("bn") ? "Bengali (বাংলা)" : "English")
                    + " — keep ALL replies in this language unless the user " +
                      "explicitly switches.]"
                    + "\n\n" + liveData;

            // Replace any client-supplied system message(s) with ours.
            // This is critical: smaller free-tier models are bad at
            // reconciling two competing system prompts.
            messages.removeIf(m -> "system".equals(String.valueOf(m.get("role"))));
            Map<String, Object> systemMsg = new LinkedHashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(0, systemMsg);

            // Make sure every message has the required string fields the
            // OpenRouter API expects (role + content).
            for (Map<String, Object> m : messages) {
                if (m.get("role") == null) m.put("role", "user");
                if (m.get("content") == null) m.put("content", "");
            }

            // ── Build HTTP client with timeouts ──
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
            rf.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
            RestTemplate restTemplate = new RestTemplate(rf);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("HTTP-Referer", SITE_URL);
            headers.set("X-Title", SITE_NAME);
            // OpenRouter requires auth on every model — even free ones.
            if (openRouterKey != null && !openRouterKey.isBlank()) {
                headers.setBearerAuth(openRouterKey.trim());
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Chat-Error", "missing-api-key")
                        .body(Map.of(
                                "error", "OPENROUTER_API_KEY is not configured on the server.",
                                "language", language));
            }

            Exception lastException = null;
            String usedModel = null;
            long startMs = System.currentTimeMillis();

            for (String model : FALLBACK_MODELS) {
                try {
                    payload.put("model", model);
                    HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);

                    ResponseEntity<String> response = restTemplate.exchange(
                            OPENROUTER_URL, HttpMethod.POST, req, String.class);

                    String body = response.getBody();
                    // OpenRouter returns an HTML error page for some failures
                    // instead of JSON. Detect and try the next model.
                    if (body != null && body.trim().startsWith("<!DOCTYPE")) {
                        log.warn("OpenRouter returned HTML for model={}", model);
                        lastException = new RuntimeException("HTML response from OpenRouter");
                        continue;
                    }

                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> jsonResponse = mapper.readValue(body, Map.class);

                    if (jsonResponse.get("error") != null) {
                        log.warn("OpenRouter error for model={}: {}", model, jsonResponse.get("error"));
                        lastException = new RuntimeException(String.valueOf(jsonResponse.get("error")));
                        continue;
                    }

                    stripHtmlFromResponse(jsonResponse);
                    usedModel = model;
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.info("chat ok model={} language={} latency_ms={} ip={}",
                            model, language, elapsed, ip);

                    return ResponseEntity.status(response.getStatusCode())
                            .header("X-Chat-Model", model)
                            .header("X-Chat-Language", language)
                            .header("X-Chat-Latency-Ms", String.valueOf(elapsed))
                            .body(jsonResponse);

                } catch (HttpStatusCodeException e) {
                    lastException = e;
                    HttpStatus code = HttpStatus.resolve(e.getStatusCode().value());
                    log.warn("chat http error model={} status={} body={}",
                            model, code, truncate(e.getResponseBodyAsString(), 300));
                    // 401 = bad/missing key: no point trying other models.
                    if (code == HttpStatus.UNAUTHORIZED || code == HttpStatus.FORBIDDEN) {
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .header("X-Chat-Error", "openrouter-auth")
                                .header("X-Chat-Model", model)
                                .body(Map.of(
                                        "error", "OpenRouter rejected the API key. " +
                                                "Set OPENROUTER_API_KEY in the server environment.",
                                        "language", language));
                    }
                    // 402/404/429/5xx -> try next model
                } catch (Exception e) {
                    // Network errors, parse errors, timeouts -> try next model
                    lastException = e;
                    log.warn("chat transport error model={}: {}", model, e.toString());
                }
            }

            // All models failed.
            String msg = lastException != null ? lastException.getMessage() : "unknown";
            log.error("All OpenRouter models failed. last={}", msg);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .header("X-Chat-Error", "all-models-failed")
                    .body(Map.of(
                            "error", "All AI models failed. Last error: " + msg,
                            "language", language,
                            "model", usedModel == null ? "" : usedModel));

        } catch (Exception e) {
            log.error("chat unhandled error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to process chat request: " + e.getMessage(),
                            "language", language));
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    //  Bilingual live-system-data builder.
    //  The data itself is language-neutral; we just tell the model how to
    //  render the section headings in the chosen language.
    // ───────────────────────────────────────────────────────────────────────
    private String buildLiveContext() {
        List<Car> allCars = carRepository.findAll();
        List<Rental> allRentals = rentalRepository.findAll();

        List<Car> availableCars = allCars.stream().filter(Car::isAvailable).toList();
        List<Rental> activeRentals = allRentals.stream()
                .filter(r -> r.getStatus() == Rental.Status.ACTIVE).toList();
        List<Rental> returnedRentals = allRentals.stream()
                .filter(r -> r.getStatus() == Rental.Status.RETURNED).toList();

        double totalRevenue = allRentals.stream()
                .mapToDouble(Rental::getTotalPrice).sum();

        StringBuilder sb = new StringBuilder();
        sb.append("LIVE SYSTEM DATA (authoritative — always use these exact figures, never estimate or recalculate):\n\n");

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

        // --- Category breakdown ---
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

        // --- Active rentals ---
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

        // --- Full rental history (capped) ---
        sb.append("FULL RENTAL HISTORY (most recent first, capped at 30):\n");
        if (allRentals.isEmpty()) {
            sb.append("  (no rentals have been recorded yet)\n");
        }
        List<Rental> sortedHistory = new ArrayList<>(allRentals);
        sortedHistory.sort(Comparator.comparing(
                (Rental r) -> r.getStartDate() != null ? r.getStartDate() : java.time.LocalDate.MIN)
                .reversed());
        int shown = 0;
        for (Rental r : sortedHistory) {
            if (shown++ >= 30) {
                sb.append("  …and ").append(sortedHistory.size() - 30).append(" more (omitted to keep prompt small)\n");
                break;
            }
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
            Map<String, Object> insights = carRentalSystem.getDashboardInsights();
            sb.append("DASHBOARD INSIGHTS:\n");
            sb.append("- Most Rented Car: ").append(insights.get("mostRentedCar"))
                    .append(" (").append(insights.get("mostRentedCount")).append(" rentals)\n");
            sb.append("- Top Customer: ").append(insights.get("topCustomer"))
                    .append(" (").append(insights.get("topCustomerRentals")).append(" rentals)\n");
            sb.append("- Busiest Month: ").append(insights.get("busiestMonth"))
                    .append(" ($").append(fmt((Double) insights.get("busiestMonthRevenue"))).append(")\n");
            sb.append("- Average Rental Duration: ").append(insights.get("avgRentalDays")).append(" days\n");
            sb.append("- Total Unique Customers: ").append(insights.get("totalCustomers")).append("\n\n");
        } catch (Exception e) {
            // silently skip
        }

        // --- Customer ratings ---
        try {
            Map<String, Double> carRatings = carRentalSystem.getAllCarRatings();
            sb.append("CAR RATINGS:\n");
            if (carRatings.isEmpty()) {
                sb.append("  (no ratings yet)\n");
            }
            for (Map.Entry<String, Double> entry : carRatings.entrySet()) {
                sb.append("  * Car ").append(entry.getKey())
                        .append(": ").append(entry.getValue()).append(" / 5.0 stars\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            // silently skip
        }

        // --- Features ---
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
        sb.append("- AI Assistant (English + Bengali): Ask questions about any of the above features\n");

        // --- Loyalty ---
        try {
            Map<String, Integer> customerRentalCounts = new HashMap<>();
            for (Rental r : allRentals) {
                if (r.getCustomer() != null) {
                    String name = r.getCustomer().getName();
                    customerRentalCounts.merge(name != null ? name : "Unknown", 1, Integer::sum);
                }
            }
            sb.append("\nCUSTOMER LOYALTY TIERS:\n");
            if (customerRentalCounts.isEmpty()) {
                sb.append("  (no customers yet)\n");
            }
            for (Map.Entry<String, Integer> entry : customerRentalCounts.entrySet()) {
                int count = entry.getValue();
                String discount = count >= 10 ? "15%" : count >= 5 ? "10%" : count >= 3 ? "5%" : "0%";
                sb.append("  * ").append(entry.getKey()).append(": ").append(count)
                        .append(" rental(s) — Loyalty Discount: ").append(discount).append("\n");
            }
        } catch (Exception e) {
            // silently skip
        }

        return sb.toString();
    }

    private String carLabel(Rental r) {
        return r.getCar() != null ? r.getCar().getBrand() + " " + r.getCar().getModel() : "Unknown car";
    }

    private String customerLabel(Rental r) {
        if (r.getCustomer() == null) return "Unknown customer";
        String name = r.getCustomer().getName();
        return (name != null && !name.isBlank()) ? name : "Customer";
    }

    private String fmt(double value) {
        return String.format("%.2f", value);
    }

    // ───────────────────────────────────────────────────────────────────────
    //  Language normalization + rate-limit helpers
    // ───────────────────────────────────────────────────────────────────────
    private String normalizeLanguage(Object raw) {
        if (raw == null) return "en";
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (s.equals("bn") || s.equals("bangla") || s.equals("bengali")
                || s.equals("বাংলা")) {
            return "bn";
        }
        return "en";
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
    }

    private boolean allowRequest(String ip) {
        long now = Instant.now().toEpochMilli();
        long[] bucket = RATE_BUCKETS.computeIfAbsent(ip, k -> new long[]{0, now});
        synchronized (bucket) {
            if (now - bucket[1] > RATE_LIMIT_WINDOW_MS) {
                bucket[0] = 0;
                bucket[1] = now;
            }
            if (bucket[0] >= RATE_LIMIT_PER_HOUR) return false;
            bucket[0]++;
            return true;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
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
                // Strip ALL HTML tags
                content = content.replaceAll("<[^>]*>", "");
                message.put("content", content);
            }
        } catch (Exception e) {
            // silently skip
        }
    }
}
