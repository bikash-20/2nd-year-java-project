package carrental.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Single file for every request/response DTO the /api/v1 surface uses.
 * Keeps the contract discoverable in one place.
 */
public class Dtos {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 2, max = 80) String displayName,
            @NotBlank @Size(min = 6, max = 100) String password) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record AuthResponse(String token, long expiresAt, UserDto user) {}

    public record UserDto(Long id, String email, String displayName, String role) {}

    public record ErrorResponse(String error, String detail) {}

    // ── Fleet ─────────────────────────────────────────────────────────────
    public record CarDto(
            String carId,
            String brand,
            String model,
            String category,
            double basePricePerDay,
            boolean available,
            String imageUrl,
            Double averageRating) {}

    public record AddCarRequest(
            @NotBlank String carId,
            @NotBlank String brand,
            @NotBlank String model,
            double price,
            @NotBlank String category,
            String imageUrl) {}

    public record DeleteCarRequest(@NotBlank String carId) {}

    // ── Rental ────────────────────────────────────────────────────────────
    public record RentRequest(
            @NotBlank String carId,
            @NotBlank String name,
            @NotBlank String phone,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {}

    public record ReturnRequest(@NotBlank String carId) {}

    public record RateRequest(Long rentalId, int rating) {}

    public record RentalDto(
            Long id,
            String carId,
            String carBrand,
            String carModel,
            String carCategory,
            String customerName,
            String customerPhone,
            int days,
            double totalPrice,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String rentedAt,
            String returnedAt,
            Integer rating,
            String notes) {}

    // ── Customer ──────────────────────────────────────────────────────────
    public record CustomerDto(
            Long id,
            String customerId,
            String name,
            String phone,
            int rentalCount,
            double loyaltyDiscount,
            String loyaltyTier) {}

    public record CustomerSummaryDto(
            long totalRentals,
            long activeRentals,
            long returnedRentals,
            double totalSpent,
            double avgRating) {}

    public record CustomerProfileDto(CustomerDto customer, CustomerSummaryDto summary, List<RentalDto> rentals) {}

    // ── Dashboard / Insights ──────────────────────────────────────────────
    public record DashboardInsightsDto(
            String mostRentedCar,
            long mostRentedCount,
            String topCustomer,
            long topCustomerRentals,
            String busiestMonth,
            double busiestMonthRevenue,
            double avgRentalDays,
            long totalCustomers) {}

    public record DashboardDto(
            long totalCars,
            long available,
            long rented,
            double revenue,
            List<CarDto> cars,
            DashboardInsightsDto insights,
            Map<String, Double> carRatings) {}

    // ── Charts ────────────────────────────────────────────────────────────
    public record MonthlyRevenueDto(String month, double revenue) {}
    public record CategoryBreakdownDto(String category, long count) {}
    public record ChartsDto(
            List<MonthlyRevenueDto> monthlyRevenue,
            List<CategoryBreakdownDto> categoryBreakdown,
            double totalRevenue,
            long totalRentals) {}

    // ── Activity log ──────────────────────────────────────────────────────
    public record ActivityDto(Long id, String action, String description, String timestamp, String icon, String label) {}
}
