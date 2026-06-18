package carrental.service;

import carrental.model.Car;
import carrental.model.Customer;
import carrental.model.Rental;
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

    public CarRentalSystem(CarRepository carRepo,
            CustomerRepository customerRepo,
            RentalRepository rentalRepo) {
        this.carRepo = carRepo;
        this.customerRepo = customerRepo;
        this.rentalRepo = rentalRepo;
    }

    @Override
    public void run(String... args) {
        if (carRepo.count() == 0) {
            carRepo.save(new Car("C001", "Toyota", "Camry", 60.0, "Sedan"));
            carRepo.save(new Car("C002", "Honda", "Accord", 70.0, "Sedan"));
            carRepo.save(new Car("C003", "Mahindra", "Thar", 150.0, "SUV"));
            carRepo.save(new Car("C004", "Ford", "Mustang", 200.0, "Sports"));
            carRepo.save(new Car("C005", "BMW", "X5", 250.0, "Luxury"));
            carRepo.save(new Car("C006", "Hyundai", "Creta", 80.0, "SUV"));
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

    public Car addCar(String carId, String brand, String model, double price, String category) {
        if (carRepo.existsById(carId))
            throw new IllegalArgumentException("Car ID " + carId + " already exists.");
        return carRepo.save(new Car(carId, brand, model, price, category));
    }

    public void deleteCar(String carId) {
        Car car = carRepo.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        if (!car.isAvailable())
            throw new IllegalStateException("Cannot delete a car that is currently rented.");
        carRepo.deleteById(carId);
    }

    // ── Customers ─────────────────────────────────────────────────────────────
    public Customer addCustomer(String name, String phone) {
        long count = customerRepo.count();
        String id = "CUS" + String.format("%03d", count + 1);
        return customerRepo.save(new Customer(id, name, phone));
    }

    // ── Rentals (date-based) ──────────────────────────────────────────────────
    public Rental rentCar(String carId, String name, String phone,
            LocalDate startDate, LocalDate endDate) {
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
        return rentalRepo.save(new Rental(car, customer, startDate, endDate));
    }

    public Rental returnCar(String carId) {
        Car car = carRepo.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        Rental rental = rentalRepo.findByCarAndStatus(car, Rental.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active rental for car " + carId));
        car.returnCar();
        carRepo.save(car);
        rental.markReturned();
        return rentalRepo.save(rental);
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
