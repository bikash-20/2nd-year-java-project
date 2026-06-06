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

    public Customer() {}

    public Customer(String customerId, String name, String phone) {
        this.customerId = customerId;
        this.name = name;
        this.phone = phone;
    }

    public Long   getId()           { return id; }
    public String getCustomerId()   { return customerId; }
    public void   setCustomerId(String v) { this.customerId = v; }
    public String getName()         { return name; }
    public void   setName(String v) { this.name = v; }
    public String getPhone()        { return phone; }
    public void   setPhone(String v){ this.phone = v; }
}
