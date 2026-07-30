package carrental.api;

import carrental.model.User;
import carrental.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a default admin account on first startup so the app is never empty:
 *   email:    admin@rentify.local
 *   password: rentify123
 *
 * Idempotent — skipped if the user already exists.
 */
@Component
public class UserSeedRunner implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserSeedRunner(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            users.save(new User(
                    "admin@rentify.local",
                    "Rentify Admin",
                    encoder.encode("rentify123"),
                    "ADMIN"));
        }
    }
}