package carrental.controller;

import carrental.model.Customer;
import carrental.model.Rental;
import carrental.repository.RentalRepository;
import carrental.service.CarRentalSystem;
import carrental.service.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class RentalController {

    private final CarRentalSystem system;
    private final PdfService pdfService;
    private final RentalRepository rentalRepo;

    public RentalController(CarRentalSystem system, PdfService pdfService, RentalRepository rentalRepo) {
        this.system = system;
        this.pdfService = pdfService;
        this.rentalRepo = rentalRepo;
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("cars", system.getAllCars());
        model.addAttribute("totalCars", system.totalCars());
        model.addAttribute("available", system.availableCars());
        model.addAttribute("rented", system.rentedCars());
        model.addAttribute("revenue", system.totalRevenue());
        model.addAttribute("insights", system.getDashboardInsights());
        model.addAttribute("carRatings", system.getAllCarRatings());
        return "dashboard";
    }

    // ── About / Developer Page ─────────────────────────────────────────────────
    @GetMapping("/about")
    public String aboutPage() {
        return "about";
    }

    // ── Rental Agreement ───────────────────────────────────────────────────────
    @GetMapping("/agreement/{id}")
    public String rentalAgreement(@PathVariable Long id, Model model) {
        try {
            Rental rental = rentalRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
            model.addAttribute("rental", rental);
        } catch (Exception e) {
            return "redirect:/";
        }
        return "agreement";
    }

    // ── Activity Log ───────────────────────────────────────────────────────────
    @GetMapping("/activity")
    public String activityPage(Model model) {
        model.addAttribute("activities", system.getRecentActivity());
        return "activity";
    }

    // ── Customer Profile ──────────────────────────────────────────────────────
    @GetMapping("/customer")
    public String customerPage() {
        return "customer";
    }

    @PostMapping("/customer/search")
    public String customerSearch(@RequestParam String phone, RedirectAttributes ra) {
        List<Customer> results = system.searchCustomersByPhone(phone);
        ra.addFlashAttribute("searchResults", results);
        ra.addFlashAttribute("searchPhone", phone);
        return "redirect:/customer";
    }

    @GetMapping("/customer/profile")
    public String customerProfile(@RequestParam Long id, Model model) {
        try {
            Customer customer = system.getCustomerById(id);
            model.addAttribute("profile", customer);
            model.addAttribute("summary", system.getCustomerSummary(customer));
            model.addAttribute("rentals", system.getCustomerRentals(customer));
        } catch (Exception e) {
            return "redirect:/customer";
        }
        return "customer";
    }

    // ── Charts ────────────────────────────────────────────────────────────────
    @GetMapping("/charts")
    public String charts(Model model) {
        model.addAttribute("monthlyRevenue", system.getMonthlyRevenue());
        model.addAttribute("categoryBreakdown", system.getCategoryBreakdown());
        model.addAttribute("totalRevenue", system.totalRevenue());
        model.addAttribute("totalRentals", system.getAllRentals().size());
        return "charts";
    }

    // ── Manage Cars ───────────────────────────────────────────────────────────
    @GetMapping("/cars")
    public String carsPage(Model model) {
        model.addAttribute("cars", system.getAllCars());
        model.addAttribute("carRatings", system.getAllCarRatings());
        return "cars";
    }

    @PostMapping("/cars/add")
    public String addCar(@RequestParam String carId,
            @RequestParam String brand,
            @RequestParam String model,
            @RequestParam double price,
            @RequestParam String category,
            @RequestParam(defaultValue = "") String imageUrl,
            RedirectAttributes ra) {
        try {
            system.addCar(carId.toUpperCase().trim(), brand.trim(), model.trim(), price, category, imageUrl);
            ra.addFlashAttribute("success", "Car " + carId.toUpperCase() + " added successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cars";
    }

    @PostMapping("/cars/delete")
    public String deleteCar(@RequestParam String carId, RedirectAttributes ra) {
        try {
            system.deleteCar(carId);
            ra.addFlashAttribute("success", "Car " + carId + " deleted successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cars";
    }

    // ── Rent ──────────────────────────────────────────────────────────────────
    @GetMapping("/rent")
    public String rentPage(Model model) {
        model.addAttribute("availableCars", system.getAvailableCars());
        model.addAttribute("today", LocalDate.now().toString());
        return "rent";
    }

    @PostMapping("/rent")
    public String doRent(@RequestParam String carId,
            @RequestParam String name,
            @RequestParam String phone,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "") String notes,
            RedirectAttributes ra) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            Rental rental = system.rentCar(carId, name, phone, start, end, notes);
            ra.addFlashAttribute("rental", rental);
            ra.addFlashAttribute("success", true);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rent/success";
    }

    @GetMapping("/rent/success")
    public String rentSuccess(Model model) {
        if (!model.containsAttribute("success"))
            return "redirect:/rent";
        return "rent-success";
    }

    // ── PDF Download ──────────────────────────────────────────────────────────
    @GetMapping("/receipt/{id}")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long id) {
        try {
            Rental rental = rentalRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
            byte[] pdf = pdfService.generateRentalReceipt(rental);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=receipt-" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Return ────────────────────────────────────────────────────────────────
    @GetMapping("/return")
    public String returnPage(Model model) {
        model.addAttribute("rentedCars", system.getRentedCars());
        model.addAttribute("activeRentals", system.getActiveRentals());
        return "return";
    }

    @PostMapping("/return")
    public String doReturn(@RequestParam String carId, RedirectAttributes ra) {
        try {
            Rental rental = system.returnCar(carId);
            ra.addFlashAttribute("rental", rental);
            ra.addFlashAttribute("success", true);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/return/success";
    }

    @GetMapping("/return/success")
    public String returnSuccess(Model model) {
        if (!model.containsAttribute("success"))
            return "redirect:/return";
        return "return-success";
    }

    // ── History ───────────────────────────────────────────────────────────────
    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("activeRentals", system.getActiveRentals());
        model.addAttribute("returnedRentals", system.getRentalHistory());
        model.addAttribute("totalRevenue", system.totalRevenue());
        model.addAttribute("totalCount", system.getAllRentals().size());
        return "history";
    }

    // ── Rate Rental ─────────────────────────────────────────────────────────────
    @PostMapping("/rate")
    public String rateRental(@RequestParam Long rentalId,
            @RequestParam int rating,
            RedirectAttributes ra) {
        try {
            system.rateRental(rentalId, rating);
            ra.addFlashAttribute("rated", true);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/history";
    }

    // ── Export CSV ──────────────────────────────────────────────────────────────
    @GetMapping("/export/csv")
    public ResponseEntity<String> exportCsv() {
        List<Rental> allRentals = system.getAllRentals();
        String csv = allRentals.stream()
                .map(r -> {
                    String carName = r.getCar() != null ? r.getCar().getDisplayName() : "N/A";
                    String customerName = r.getCustomer() != null ? r.getCustomer().getName() : "N/A";
                    String phone = r.getCustomer() != null ? r.getCustomer().getPhone() : "N/A";
                    String rating = r.getRating() != null ? String.valueOf(r.getRating()) : "";
                    String notes = r.getNotes() != null ? r.getNotes() : "";
                    return String.join(",",
                            String.valueOf(r.getId()),
                            escapeCsv(carName),
                            escapeCsv(customerName),
                            escapeCsv(phone),
                            r.getStartDateStr(),
                            r.getEndDateStr(),
                            String.valueOf(r.getDays()),
                            String.format("%.2f", r.getTotalPrice()),
                            r.getStatus().toString(),
                            rating,
                            escapeCsv(notes));
                })
                .collect(Collectors.joining("\n"));

        String header = "ID,Car,Customer,Phone,Start Date,End Date,Days,Total,Status,Rating,Notes\n";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rentify-rentals.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(header + csv);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
