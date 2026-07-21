package carrental.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "activity_log")
public class ActivityLog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public enum Action {
        CAR_ADDED,
        CAR_DELETED,
        CAR_RENTED,
        CAR_RETURNED,
        CUSTOMER_SEARCHED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Action action;

    @Column(length = 300)
    private String description;

    private LocalDateTime timestamp;

    public ActivityLog() {
    }

    public ActivityLog(Action action, String description) {
        this.action = action;
        this.description = description;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Action getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getTimestampStr() {
        return timestamp != null ? timestamp.format(FMT) : "-";
    }

    public String getActionIcon() {
        switch (action) {
            case CAR_ADDED:    return "➕";
            case CAR_DELETED:  return "🗑";
            case CAR_RENTED:   return "🔑";
            case CAR_RETURNED: return "✅";
            default:           return "📋";
        }
    }

    public String getActionLabel() {
        switch (action) {
            case CAR_ADDED:    return "Car Added";
            case CAR_DELETED:  return "Car Deleted";
            case CAR_RENTED:   return "Car Rented";
            case CAR_RETURNED: return "Car Returned";
            default:           return "Activity";
        }
    }
}
