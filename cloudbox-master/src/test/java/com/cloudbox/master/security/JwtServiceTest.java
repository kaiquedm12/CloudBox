package com.cloudbox.master.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbox.master.user.User;
import com.cloudbox.master.user.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            "Y2xvdWRib3gtdGVzdC1qd3Qtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=";

    @Test
    void generatesAndValidatesTokenForUser() {
        JwtService jwtService = new JwtService(SECRET, 3600);
        User user = user();

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo(user.getEmail());
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwtService = new JwtService(SECRET, 3600);
        String token = jwtService.generateToken(user());

        assertThat(jwtService.validateToken(token + "alterado")).isFalse();
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@cloudbox.local");
        user.setRole(UserRole.ADMIN);
        return user;
    }
}
