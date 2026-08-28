package com.cloudbox.master.auth;

import com.cloudbox.master.auth.dto.LoginRequest;
import com.cloudbox.master.auth.dto.LoginResponse;
import com.cloudbox.master.common.UnauthorizedException;
import com.cloudbox.master.security.JwtService;
import com.cloudbox.master.user.User;
import com.cloudbox.master.user.UserRepository;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds()));
    }
}
