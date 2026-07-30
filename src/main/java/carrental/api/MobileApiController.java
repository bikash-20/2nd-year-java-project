package carrental.api;

import carrental.api.dto.Dtos.*;
import carrental.model.ActivityLog;
import carrental.model.Car;
import carrental.model.Customer;
import carrental.model.Rental;
import carrental.service.CarRentalSystem;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobile REST surface — wraps the existing CarRentalSystem service methods
 * so the Flutter app gets JSON contracts without changing business logic.
 *
 * Everything that mutates state requires a JWT (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1")
public class MobileApiController {

    private final CarRentalSystem system;
    private final carrental.service.PdfService pdfService;
    private final carrental.repository.RentalRepository rentalRepo;

    public MobileApiController(CarRentalSystem system,
                               carrental.service.PdfService pdfService,
                               carrental.repository.RentalRepository rentalRepo) {
        this.system = system;
        this.pdfService = pdfService;
        this.rentalRepo = rentalRepo;
    }

    // ── Health ────────────────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "rentify-mobile-api");
        body.put("totalCars", system.totalCars());
        body.put("availableCars", system.availableCars());
        body.put("rentedCars", system.rentedCars());
        body.put("totalRevenue", system.totalRevenue());
        body.put("totalRentals", system.getAllRentals().size());
        return ResponseEntity.ok(body);
    }

    // ── Dashboard ─────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto> dashboard() {
        Map<String, Double> ratings = system.getAllCarRatings();
        Map<String, Object> insights = system.getDashboardInsights();
        return ResponseEntity.ok(new DashboardDto(
                system.totalCars(),
                system.availableCars(),
                system.rentedCars(),
                system.totalRevenue(),
                system.getAllCars().stream().map(c -> toCarDto(c, ratings)).toList(),
                new DashboardInsightsDto(
                        str(insights.get("mostRentedCar")),
                        lng(insights.get("mostRentedCount")),
                        str(insights.get("topCustomer")),
                        lng(insights.get("topCustomerRentals")),
                        str(insights.get("busiestMonth")),
                        dbl(insights.get("busiestMonthRevenue")),
                        dbl(insights.get("avgRentalDays")),
                        lng(insights.get("totalCustomers"))
                ),
                ratings
        ));
    }

    // ── Cars / Fleet ──────────────────────────────────────────────────────
    @GetMapping("/cars")
    public List<CarDto> cars() {
        Map<String, Double> ratings = system.getAllCarRatings();
        return system.getAllCars().stream().map(c -> toCarDto(c, ratings)).toList();
    }

    @GetMapping("/cars/available")
    public List<CarDto> availableCars() {
        Map<String, Double> ratings = system.getAllCarRatings();
        return system.getAvailableCars().stream().map(c -> toCarDto(c, ratings)).toList();
    }

    @PostMapping("/cars")
    public ResponseEntity<?> addCar(@Valid @RequestBody AddCarRequest req) {
        try {
            Car saved = system.addCar(
                    req.carId().toUpperCase().trim(),
                    req.brand().trim(),
                    req.model().trim(),
                    req.price(),
                    req.category().trim(),
                    req.imageUrl());
            return ResponseEntity.status(HttpStatus.CREATED).body(toCarDto(saved, Map.of()));
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/cars/{carId}")
    public ResponseEntity<?> deleteCar(@PathVariable String carId) {
        try {
            system.deleteCar(carId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    // ── Rentals ───────────────────────────────────────────────────────────
    @GetMapping("/rentals/active")
    public List<RentalDto> activeRentals() {
        return system.getActiveRentals().stream().map(MobileApiController::toRentalDto).toList();
    }

    @GetMapping("/rentals/history")
    public List<RentalDto> rentalHistory() {
        return system.getRentalHistory().stream().map(MobileApiController::toRentalDto).toList();
    }

    @GetMapping("/rentals/all")
    public List<RentalDto> allRentals() {
        return system.getAllRentals().stream().map(MobileApiController::toRentalDto).toList();
    }

    @PostMapping("/rentals")
    public ResponseEntity<?> rent(@Valid @RequestBody RentRequest req) {
        try {
            Rental r = system.rentCar(
                    req.carId().toUpperCase().trim(),
                    req.name().trim(),
                    req.phone().trim(),
                    req.startDate(),
                    req.endDate(),
                    req.notes());
            return ResponseEntity.status(HttpStatus.CREATED).body(toRentalDto(r));
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/rentals/return")
    public ResponseEntity<?> returnCar(@Valid @RequestBody ReturnRequest req) {
        try {
            Rental r = system.returnCar(req.carId().toUpperCase().trim());
            return ResponseEntity.ok(toRentalDto(r));
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/rentals/rate")
    public ResponseEntity<?> rate(@Valid @RequestBody RateRequest req) {
        try {
            Rental r = system.rateRental(req.rentalId(), req.rating());
            return ResponseEntity.ok(toRentalDto(r));
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/rentals/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        try {
            Rental r = rentalRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
            byte[] pdf = pdfService.generateRentalReceipt(r);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=receipt-" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Customers ─────────────────────────────────────────────────────────
    @GetMapping("/customers/search")
    public List<CustomerDto> searchCustomers(@RequestParam String phone) {
        return system.searchCustomersByPhone(phone).stream().map(MobileApiController::toCustomerDto).toList();
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> customerProfile(@PathVariable Long id) {
        try {
            Customer c = system.getCustomerById(id);
            Map<String, Object> summary = system.getCustomerSummary(c);
            return ResponseEntity.ok(new CustomerProfileDto(
                    toCustomerDto(c),
                    new CustomerSummaryDto(
                            lng(summary.get("totalRentals")),
                            lng(summary.get("activeRentals")),
                            lng(summary.get("returnedRentals")),
                            dbl(summary.get("totalSpent")),
                            dbl(summary.get("avgRating"))
                    ),
                    system.getCustomerRentals(c).stream().map(MobileApiController::toRentalDto).toList()
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Charts / Analytics ────────────────────────────────────────────────
    @GetMapping("/charts")
    public ChartsDto charts() {
        return new ChartsDto(
                system.getMonthlyRevenue().stream().map(m ->
                        new MonthlyRevenueDto(str(m.get("month")), dbl(m.get("revenue")))).toList(),
                system.getCategoryBreakdown().stream().map(m ->
                        new CategoryBreakdownDto(str(m.get("category")), lng(m.get("count")))).toList(),
                system.totalRevenue(),
                system.getAllRentals().size()
        );
    }

    // ── Activity log ──────────────────────────────────────────────────────
    @GetMapping("/activity")
    public List<ActivityDto> activity() {
        return system.getRecentActivity().stream().map(a ->
                new ActivityDto(a.getId(),
                        a.getAction() != null ? a.getAction().name() : null,
                        a.getDescription(),
                        a.getTimestampStr(),
                        a.getActionIcon(),
                        a.getActionLabel())).toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────────
    private static CarDto toCarDto(Car c, Map<String, Double> ratings) {
        Double avg = ratings.get(c.getCarId());
        return new CarDto(c.getCarId(), c.getBrand(), c.getModel(), c.getCategory(),
                c.getBasePricePerDay(), c.isAvailable(), c.getImageUrl(), avg);
    }

    private static RentalDto toRentalDto(Rental r) {
        return new RentalDto(
                r.getId(),
                r.getCar() != null ? r.getCar().getCarId() : null,
                r.getCar() != null ? r.getCar().getBrand() : null,
                r.getCar() != null ? r.getCar().getModel() : null,
                r.getCar() != null ? r.getCar().getCategory() : null,
                r.getCustomer() != null ? r.getCustomer().getName() : null,
                r.getCustomer() != null ? r.getCustomer().getPhone() : null,
                r.getDays(),
                r.getTotalPrice(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getStartDate(),
                r.getEndDate(),
                r.getRentedAtStr(),
                r.getReturnedAtStr(),
                r.getRating(),
                r.getNotes());
    }

    private static CustomerDto toCustomerDto(Customer c) {
        String tier;
        if (c.getLoyaltyDiscount() >= 0.15) tier = "PLATINUM";
        else if (c.getLoyaltyDiscount() >= 0.10) tier = "GOLD";
        else if (c.getLoyaltyDiscount() >= 0.05) tier = "SILVER";
        else tier = "STANDARD";
        return new CustomerDto(c.getId(), c.getCustomerId(), c.getName(), c.getPhone(),
                c.getRentalCount(), c.getLoyaltyDiscount(), tier);
    }

    private static ResponseEntity<?> badRequest(String msg) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("bad_request", msg));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static double dbl(Object o) { return o == null ? 0.0 : ((Number) o).doubleValue(); }
    private static long   lng(Object o) { return o == null ? 0L  : ((Number) o).longValue(); }
}