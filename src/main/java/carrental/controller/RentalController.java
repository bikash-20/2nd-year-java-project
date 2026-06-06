package carrental.controller;

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

@Controller
public class RentalController {

    private final CarRentalSystem system;
    private final PdfService      pdfService;
    private final RentalRepository rentalRepo;

    public RentalController(CarRentalSystem system, PdfService pdfService, RentalRepository rentalRepo) {
        this.system     = system;
        this.pdfService = pdfService;
        this.rentalRepo = rentalRepo;
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("cars",      system.getAllCars());
        model.addAttribute("totalCars", system.totalCars());
        model.addAttribute("available", system.availableCars());
        model.addAttribute("rented",    system.rentedCars());
        model.addAttribute("revenue",   system.totalRevenue());
        return "dashboard";
    }

    // ── Charts ────────────────────────────────────────────────────────────────
    @GetMapping("/charts")
    public String charts(Model model) {
        model.addAttribute("monthlyRevenue",    system.getMonthlyRevenue());
        model.addAttribute("categoryBreakdown", system.getCategoryBreakdown());
        model.addAttribute("totalRevenue",      system.totalRevenue());
        model.addAttribute("totalRentals",      system.getAllRentals().size());
        return "charts";
    }

    // ── Manage Cars ───────────────────────────────────────────────────────────
    @GetMapping("/cars")
    public String carsPage(Model model) {
        model.addAttribute("cars", system.getAllCars());
        return "cars";
    }

    @PostMapping("/cars/add")
    public String addCar(@RequestParam String carId,
                         @RequestParam String brand,
                         @RequestParam String model,
                         @RequestParam double price,
                         @RequestParam String category,
                         RedirectAttributes ra) {
        try {
            system.addCar(carId.toUpperCase().trim(), brand.trim(), model.trim(), price, category);
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
                         RedirectAttributes ra) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end   = LocalDate.parse(endDate);
            Rental rental   = system.rentCar(carId, name, phone, start, end);
            ra.addFlashAttribute("rental",  rental);
            ra.addFlashAttribute("success", true);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rent/success";
    }

    @GetMapping("/rent/success")
    public String rentSuccess(Model model) {
        if (!model.containsAttribute("success")) return "redirect:/rent";
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
        model.addAttribute("rentedCars",    system.getRentedCars());
        model.addAttribute("activeRentals", system.getActiveRentals());
        return "return";
    }

    @PostMapping("/return")
    public String doReturn(@RequestParam String carId, RedirectAttributes ra) {
        try {
            Rental rental = system.returnCar(carId);
            ra.addFlashAttribute("rental",  rental);
            ra.addFlashAttribute("success", true);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/return/success";
    }

    @GetMapping("/return/success")
    public String returnSuccess(Model model) {
        if (!model.containsAttribute("success")) return "redirect:/return";
        return "return-success";
    }

    // ── History ───────────────────────────────────────────────────────────────
    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("activeRentals",   system.getActiveRentals());
        model.addAttribute("returnedRentals", system.getRentalHistory());
        model.addAttribute("totalRevenue",    system.totalRevenue());
        model.addAttribute("totalCount",      system.getAllRentals().size());
        return "history";
    }
}
