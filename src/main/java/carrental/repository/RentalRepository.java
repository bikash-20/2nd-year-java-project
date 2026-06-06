package carrental.repository;

import carrental.model.Car;
import carrental.model.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RentalRepository extends JpaRepository<Rental, Long> {
    List<Rental> findByStatus(Rental.Status status);
    Optional<Rental> findByCarAndStatus(Car car, Rental.Status status);

    // Check overlapping bookings for double-booking prevention
    @Query("SELECT r FROM Rental r WHERE r.car = :car AND r.status = 'ACTIVE' " +
           "AND r.startDate < :endDate AND r.endDate > :startDate")
    List<Rental> findOverlapping(@Param("car") Car car,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);
}
