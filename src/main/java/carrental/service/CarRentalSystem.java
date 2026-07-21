package carrental.service;

import carrental.model.ActivityLog;
import carrental.model.Car;
import carrental.model.Customer;
import carrental.model.Rental;
import carrental.repository.ActivityLogRepository;
import carrental.repository.CarRepository;
import carrental.repository.CustomerRepository;
import carrental.repository.RentalRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CarRentalSystem implements CommandLineRunner {

    private final CarRepository carRepo;
    private final CustomerRepository customerRepo;
    private final RentalRepository rentalRepo;
    private final ActivityLogRepository activityLogRepo;

    public CarRentalSystem(CarRepository carRepo,
            CustomerRepository customerRepo,
            RentalRepository rentalRepo,
            ActivityLogRepository activityLogRepo) {
        this.carRepo = carRepo;
        this.customerRepo = customerRepo;
        this.rentalRepo = rentalRepo;
        this.activityLogRepo = activityLogRepo;
    }

    @Override
    public void run(String... args) {
        if (carRepo.count() == 0) {
            // ── Sedans ──
            carRepo.save(new Car("C001", "Toyota", "Camry", 60.0, "Sedan"));
            carRepo.save(new Car("C002", "Honda", "Accord", 70.0, "Sedan"));
            carRepo.save(new Car("C012", "Honda", "Civic", 55.0, "Sedan"));
            carRepo.save(new Car("C017", "Toyota", "Corolla", 50.0, "Sedan"));
            // ── SUVs ──
            carRepo.save(new Car("C003", "Mahindra", "Thar", 150.0, "SUV"));
            carRepo.save(new Car("C006", "Hyundai", "Creta", 80.0, "SUV"));
            carRepo.save(new Car("C009", "Audi", "Q7", 190.0, "SUV"));
            carRepo.save(new Car("C011", "Toyota", "Fortuner", 130.0, "SUV"));
            carRepo.save(new Car("C016", "Kia", "Seltos", 75.0, "SUV"));
            // ── Sports ──
            carRepo.save(new Car("C004", "Ford", "Mustang", 200.0, "Sports"));
            carRepo.save(new Car("C010", "Porsche", "911", 350.0, "Sports"));
            carRepo.save(new Car("C015", "Chevrolet", "Camaro", 180.0, "Sports"));
            carRepo.save(new Car("C018", "BMW", "M4", 280.0, "Sports"));
            // ── Luxury ──
            carRepo.save(new Car("C005", "BMW", "X5", 250.0, "Luxury"));
            carRepo.save(new Car("C008", "Mercedes-Benz", "E-Class", 220.0, "Luxury"));
            carRepo.save(new Car("C014", "Range Rover", "Velar", 300.0, "Luxury"));
            // ── Electric ──
            carRepo.save(new Car("C007", "Tesla", "Model 3", 180.0, "Electric"));
            // ── Hatchback ──
            carRepo.save(new Car("C013", "Maruti Suzuki", "Swift", 40.0, "Hatchback"));
        }
    }

    // ── Cars ──────────────────────────────────────────────────────────────────
    public List<Car> getAllCars() {
        return carRepo.findAll();
    }

    public List<Car> getAvailableCars() {
        return carRepo.findByAvailableTrue();
    }

    public List<Car> getRentedCars() {
        return carRepo.findByAvailableFalse();
    }

    public Car addCar(String carId, String brand, String model, double price, String category, String imageUrl) {
        if (carRepo.existsById(carId))
            throw new IllegalArgumentException("Car ID " + carId + " already exists.");
        Car car = new Car(carId, brand, model, price, category);
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            car.setImageUrl(imageUrl.trim());
        }
        car = carRepo.save(car);
        logActivity(ActivityLog.Action.CAR_ADDED,
                "Added " + brand + " " + model + " (" + carId + ") to fleet — $" + price + "/day, " + category);
        return car;
    }

    public void deleteCar(String carId) {
        Car car = carRepo.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        if (!car.isAvailable())
            throw new IllegalStateException("Cannot delete a car that is currently rented.");
        carRepo.deleteById(carId);
        logActivity(ActivityLog.Action.CAR_DELETED,
                "Deleted " + car.getBrand() + " " + car.getModel() + " (" + carId + ") from fleet");
    }

    // ── Customers ─────────────────────────────────────────────────────────────
    public Customer addCustomer(String name, String phone) {
        // Check if customer with this phone already exists
        List<Customer> existing = customerRepo.findByPhoneContaining(phone);
        for (Customer c : existing) {
            if (c.getPhone().equals(phone)) return c;
        }
        long count = customerRepo.count();
        String id = "CUS" + String.format("%03d", count + 1);
        return customerRepo.save(new Customer(id, name, phone));
    }

    public double applyLoyaltyDiscount(Customer customer, double basePrice) {
        if (customer.getLoyaltyDiscount() > 0) {
            return basePrice * (1 - customer.getLoyaltyDiscount());
        }
        return basePrice;
    }

    // ── Activity Log ──────────────────────────────────────────────────────────
    public void logActivity(ActivityLog.Action action, String description) {
        activityLogRepo.save(new ActivityLog(action, description));
    }

    public List<ActivityLog> getRecentActivity() {
        return activityLogRepo.findAllByOrderByTimestampDesc();
    }

    public List<Customer> searchCustomersByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return List.of();
        return customerRepo.findByPhoneContaining(phone.trim());
    }

    public Customer getCustomerById(Long id) {
        return customerRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    public List<Rental> getCustomerRentals(Customer customer) {
        return rentalRepo.findAll().stream()
                .filter(r -> r.getCustomer() != null && r.getCustomer().getId().equals(customer.getId()))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getCustomerSummary(Customer customer) {
        List<Rental> rentals = getCustomerRentals(customer);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRentals", rentals.size());
        summary.put("activeRentals", rentals.stream().filter(r -> r.getStatus() == Rental.Status.ACTIVE).count());
        summary.put("returnedRentals", rentals.stream().filter(r -> r.getStatus() == Rental.Status.RETURNED).count());
        summary.put("totalSpent", rentals.stream().mapToDouble(Rental::getTotalPrice).sum());
        summary.put("avgRating", rentals.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(Rental::getRating)
                .average()
                .orElse(0.0));
        return summary;
    }

    // ── Rentals (date-based) ──────────────────────────────────────────────────
    public Rental rentCar(String carId, String name, String phone,
            LocalDate startDate, LocalDate endDate, String notes) {
        Car car = carRepo.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        if (!car.isAvailable())
            throw new IllegalStateException("Car " + carId + " is not available.");
        if (!startDate.isBefore(endDate))
            throw new IllegalArgumentException("End date must be after start date.");
        if (startDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Start date cannot be in the past.");

        // Double-booking check
        List<Rental> overlapping = rentalRepo.findOverlapping(car, startDate, endDate);
        if (!overlapping.isEmpty())
            throw new IllegalStateException("Car is already booked for those dates.");

        Customer customer = addCustomer(name, phone);
        car.rent();
        carRepo.save(car);
        Rental rental = new Rental(car, customer, startDate, endDate);
        // Apply loyalty discount for returning customers
        double discountedPrice = applyLoyaltyDiscount(customer, rental.getTotalPrice());
        if (discountedPrice < rental.getTotalPrice()) {
            rental.setTotalPrice(discountedPrice);
        }
        if (notes != null && !notes.trim().isEmpty()) {
            rental.setNotes(notes.trim());
        }
        rental = rentalRepo.save(rental);
        customer.incrementRentalCount();
        customerRepo.save(customer);
        logActivity(ActivityLog.Action.CAR_RENTED,
                "Rented " + car.getBrand() + " " + car.getModel() + " (" + carId + ") to " + name
                        + " | " + startDate + " to " + endDate + " | $" + String.format("%.2f", rental.getTotalPrice())
                        + (customer.getLoyaltyDiscount() > 0 ? " (" + (int)(customer.getLoyaltyDiscount()*100) + "% loyalty discount applied)" : ""));
        return rental;
    }

    public Rental returnCar(String carId) {
        Car car = carRepo.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        Rental rental = rentalRepo.findByCarAndStatus(car, Rental.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active rental for car " + carId));
        car.returnCar();
        carRepo.save(car);
        rental.markReturned();
        rental = rentalRepo.save(rental);
        logActivity(ActivityLog.Action.CAR_RETURNED,
                "Returned " + car.getBrand() + " " + car.getModel() + " (" + carId + ") from " + rental.getCustomer().getName()
                        + " | Duration: " + rental.getDays() + " day(s) | Total: $" + String.format("%.2f", rental.getTotalPrice()));
        return rental;
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    public long totalCars() {
        return carRepo.count();
    }

    public long availableCars() {
        return carRepo.findByAvailableTrue().size();
    }

    public long rentedCars() {
        return carRepo.findByAvailableFalse().size();
    }

    public double totalRevenue() {
        return rentalRepo.findAll().stream().mapToDouble(Rental::getTotalPrice).sum();
    }

    public List<Rental> getActiveRentals() {
        return rentalRepo.findByStatus(Rental.Status.ACTIVE);
    }

    public List<Rental> getRentalHistory() {
        return rentalRepo.findByStatus(Rental.Status.RETURNED);
    }

    public List<Rental> getAllRentals() {
        return rentalRepo.findAll();
    }

    // ── Ratings ────────────────────────────────────────────────────────────────
    public Rental rateRental(Long rentalId, int rating) {
        Rental rental = rentalRepo.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
        if (rental.getStatus() != Rental.Status.RETURNED) {
            throw new IllegalStateException("Can only rate returned rentals");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        rental.setRating(rating);
        return rentalRepo.save(rental);
    }

    // Returns map of carId -> average rating for all cars that have ratings
    public Map<String, Double> getAllCarRatings() {
        List<Rental> returned = rentalRepo.findByStatus(Rental.Status.RETURNED);
        Map<String, List<Integer>> ratingsByCar = new HashMap<>();
        for (Rental r : returned) {
            if (r.getRating() != null && r.getCar() != null) {
                ratingsByCar.computeIfAbsent(r.getCar().getCarId(), k -> new ArrayList<>()).add(r.getRating());
            }
        }
        Map<String, Double> averages = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : ratingsByCar.entrySet()) {
            double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            averages.put(entry.getKey(), Math.round(avg * 10.0) / 10.0);
        }
        return averages;
    }

    // ── Dashboard Insights ──────────────────────────────────────────────
    public Map<String, Object> getDashboardInsights() {
        List<Rental> allRentals = rentalRepo.findAll();
        Map<String, Object> insights = new LinkedHashMap<>();

        // Most rented car
        Map<Car, Long> carCounts = allRentals.stream()
                .filter(r -> r.getCar() != null)
                .collect(Collectors.groupingBy(Rental::getCar, Collectors.counting()));
        Optional<Map.Entry<Car, Long>> mostRented = carCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        if (mostRented.isPresent()) {
            insights.put("mostRentedCar", mostRented.get().getKey().getDisplayName());
            insights.put("mostRentedCount", mostRented.get().getValue());
        } else {
            insights.put("mostRentedCar", "N/A");
            insights.put("mostRentedCount", 0L);
        }

        // Top customer
        Map<Customer, Long> customerCounts = allRentals.stream()
                .filter(r -> r.getCustomer() != null)
                .collect(Collectors.groupingBy(Rental::getCustomer, Collectors.counting()));
        Optional<Map.Entry<Customer, Long>> topCustomer = customerCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        if (topCustomer.isPresent()) {
            insights.put("topCustomer", topCustomer.get().getKey().getName());
            insights.put("topCustomerRentals", topCustomer.get().getValue());
        } else {
            insights.put("topCustomer", "N/A");
            insights.put("topCustomerRentals", 0L);
        }

        // Busiest month (by revenue)
        Map<String, Double> monthRevenue = new LinkedHashMap<>();
        for (Rental r : allRentals) {
            if (r.getStartDate() != null) {
                String monthKey = r.getStartDate().getMonth().name().substring(0, 3);
                monthRevenue.merge(monthKey, r.getTotalPrice(), Double::sum);
            }
        }
        Optional<Map.Entry<String, Double>> busiestMonth = monthRevenue.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        insights.put("busiestMonth", busiestMonth.map(Map.Entry::getKey).orElse("N/A"));
        insights.put("busiestMonthRevenue", busiestMonth.map(Map.Entry::getValue).orElse(0.0));

        // Average rental duration
        double avgDays = allRentals.stream()
                .mapToInt(Rental::getDays)
                .average()
                .orElse(0.0);
        insights.put("avgRentalDays", Math.round(avgDays * 10.0) / 10.0);

        // Unique customers
        long uniqueCustomers = allRentals.stream()
                .filter(r -> r.getCustomer() != null)
                .map(r -> r.getCustomer().getId())
                .distinct()
                .count();
        insights.put("totalCustomers", uniqueCustomers);

        return insights;
    }

    // ── Chart data ────────────────────────────────────────────────────────────

    // Monthly revenue for current year: returns list of [month, revenue]
    public List<Map<String, Object>> getMonthlyRevenue() {
        int year = LocalDate.now().getYear();
        Map<Month, Double> map = new LinkedHashMap<>();
        for (Month m : Month.values())
            map.put(m, 0.0);

        rentalRepo.findAll().forEach(r -> {
            if (r.getStartDate() != null && r.getStartDate().getYear() == year) {
                map.merge(r.getStartDate().getMonth(), r.getTotalPrice(), Double::sum);
            }
        });

        return map.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", e.getKey().name().substring(0, 3));
            row.put("revenue", e.getValue());
            return row;
        }).collect(Collectors.toList());
    }

    // Category breakdown: returns list of [category, count]
    public List<Map<String, Object>> getCategoryBreakdown() {
        Map<String, Long> map = rentalRepo.findAll().stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCar().getCategory(),
                        Collectors.counting()));
        return map.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", e.getKey());
            row.put("count", e.getValue());
            return row;
        }).collect(Collectors.toList());
    }
}
