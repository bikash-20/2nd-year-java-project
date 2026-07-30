package carrental.api;

import carrental.api.dto.Dtos;
import carrental.model.User;
import carrental.repository.UserRepository;
import carrental.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * POST /api/v1/auth/register   { email, displayName, password }  -> 200 { token, user }
 * POST /api/v1/auth/login      { email, password }               -> 200 { token, user }
 * GET  /api/v1/auth/me                                          -> 200 { user }
 *
 * Stateless: server stores nothing beyond the user row.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody Dtos.RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new Dtos.ErrorResponse("email_in_use", "An account with that email already exists."));
        }
        User u = new User(req.email().toLowerCase().trim(),
                          req.displayName().trim(),
                          encoder.encode(req.password()),
                          "USER");
        u = users.save(u);
        return ok(u);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Dtos.LoginRequest req) {
        var userOpt = users.findByEmailIgnoreCase(req.email());
        if (userOpt.isEmpty() || !encoder.matches(req.password(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new Dtos.ErrorResponse("invalid_credentials", "Email or password is incorrect."));
        }
        return ok(userOpt.get());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new Dtos.ErrorResponse("unauthorized", "Missing or invalid bearer token."));
        }
        return ResponseEntity.ok(toDto(user));
    }

    private ResponseEntity<?> ok(User u) {
        String token = jwt.issue(u.getId(), u.getEmail(), u.getRole());
        long exp = Instant.now().toEpochMilli() + jwt.getTtlMillis();
        return ResponseEntity.ok(new Dtos.AuthResponse(token, exp, toDto(u)));
    }

    private Dtos.UserDto toDto(User u) {
        return new Dtos.UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole());
    }
}
