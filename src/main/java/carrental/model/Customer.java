package carrental.model;

import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerId;
    private String name;
    private String phone;
    private int rentalCount;
    private double loyaltyDiscount;

    public Customer() {}

    public Customer(String customerId, String name, String phone) {
        this.customerId = customerId;
        this.name = name;
        this.phone = phone;
        this.rentalCount = 0;
        this.loyaltyDiscount = 0.0;
    }

    public Long   getId()           { return id; }
    public String getCustomerId()   { return customerId; }
    public void   setCustomerId(String v) { this.customerId = v; }
    public String getName()         { return name; }
    public void   setName(String v) { this.name = v; }
    public String getPhone()        { return phone; }
    public void   setPhone(String v){ this.phone = v; }
    public int    getRentalCount()  { return rentalCount; }
    public void   setRentalCount(int v) { this.rentalCount = v; }
    public double getLoyaltyDiscount() { return loyaltyDiscount; }
    public void   setLoyaltyDiscount(double v) { this.loyaltyDiscount = v; }

    public void incrementRentalCount() {
        this.rentalCount++;
        if (this.rentalCount >= 10) this.loyaltyDiscount = 0.15;
        else if (this.rentalCount >= 5) this.loyaltyDiscount = 0.10;
        else if (this.rentalCount >= 3) this.loyaltyDiscount = 0.05;
    }
}
