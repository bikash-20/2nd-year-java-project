package carrental.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cars")
public class Car {

    @Id
    @Column(name = "car_id", nullable = false, unique = true, length = 10)
    private String carId;

    private String brand;
    private String model;
    private double basePricePerDay;
    private String category;
    private boolean available = true;

    public Car() {}

    public Car(String carId, String brand, String model, double basePricePerDay, String category) {
        this.carId = carId;
        this.brand = brand;
        this.model = model;
        this.basePricePerDay = basePricePerDay;
        this.category = category;
        this.available = true;
    }

    public String getCarId()           { return carId; }
    public void   setCarId(String v)   { this.carId = v; }
    public String getBrand()           { return brand; }
    public void   setBrand(String v)   { this.brand = v; }
    public String getModel()           { return model; }
    public void   setModel(String v)   { this.model = v; }
    public double getBasePricePerDay() { return basePricePerDay; }
    public void   setBasePricePerDay(double v) { this.basePricePerDay = v; }
    public String getCategory()        { return category; }
    public void   setCategory(String v){ this.category = v; }
    public boolean isAvailable()       { return available; }
    public void    setAvailable(boolean v){ this.available = v; }

    public double calculatePrice(int days) { return basePricePerDay * days; }
    public void rent()      { available = false; }
    public void returnCar() { available = true; }
    public String getDisplayName() { return brand + " " + model; }
}
