package com.cloudbox.master.security;

import com.cloudbox.master.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DASHBOARD_COOKIE_NAME = "cloudbox_access_token";
    private static final String CLUSTER_STATUS_WEBSOCKET_PATH = "/ws/cluster-status";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request.getHeader("Authorization"));
        if (token == null && CLUSTER_STATUS_WEBSOCKET_PATH.equals(request.getRequestURI())) {
            token = dashboardCookieToken(request.getCookies());
        }
        if (token != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtService.validateToken(token)) {
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            String email = jwtService.extractEmail(token);
            userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(
                                user.getEmail(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                authentication.setDetails(user.getId());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        } catch (JwtException | IllegalArgumentException exception) {
            // O token pode expirar entre a validação e a extração dos claims.
        }
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private String dashboardCookieToken(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (DASHBOARD_COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
