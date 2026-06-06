package carrental.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "rentals")
public class Rental {

    public enum Status { ACTIVE, RETURNED }

    private static final DateTimeFormatter FMT     = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "car_id")
    private Car car;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private int days;
    private double totalPrice;

    // Date-based booking
    private LocalDate startDate;
    private LocalDate endDate;

    private LocalDateTime rentedAt;
    private LocalDateTime returnedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    public Rental() {}

    // Constructor for date-based booking
    public Rental(Car car, Customer customer, LocalDate startDate, LocalDate endDate) {
        this.car       = car;
        this.customer  = customer;
        this.startDate = startDate;
        this.endDate   = endDate;
        this.days      = (int) ChronoUnit.DAYS.between(startDate, endDate);
        this.totalPrice = car.calculatePrice(this.days);
        this.rentedAt  = LocalDateTime.now();
        this.status    = Status.ACTIVE;
    }

    public Long     getId()         { return id; }
    public Car      getCar()        { return car; }
    public Customer getCustomer()   { return customer; }
    public int      getDays()       { return days; }
    public double   getTotalPrice() { return totalPrice; }
    public Status   getStatus()     { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate()   { return endDate; }

    public String getStartDateStr() { return startDate != null ? startDate.format(DATE_FMT) : "-"; }
    public String getEndDateStr()   { return endDate   != null ? endDate.format(DATE_FMT)   : "-"; }
    public String getRentedAtStr()  { return rentedAt  != null ? rentedAt.format(FMT)       : "-"; }
    public String getReturnedAtStr(){ return returnedAt != null ? returnedAt.format(FMT)    : "-"; }

    public void markReturned() {
        this.returnedAt = LocalDateTime.now();
        this.status     = Status.RETURNED;
    }
}
