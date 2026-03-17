package com.suhas.stocktracker.service;

import com.suhas.stocktracker.model.AppUser;
import com.suhas.stocktracker.model.SignupRequest;
import com.suhas.stocktracker.model.StoredUser;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$");

    private final DatabaseService databaseService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(DatabaseService databaseService, PasswordEncoder passwordEncoder) {
        this.databaseService = databaseService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    void seedAdminUser() {
        if (databaseService.fetchUserByUsername("admin") != null) {
            return;
        }
        databaseService.upsertUser(new StoredUser(
            "admin",
            "Administrator",
            "admin@stocktracker.local",
            passwordEncoder.encode("alohamora"),
            "ADMIN"
        ));
    }

    public AppUser signup(SignupRequest request) {
        validateSignup(request);
        if (databaseService.fetchUserByUsername(request.username().trim()) != null) {
            throw new IllegalArgumentException("Username already exists.");
        }
        if (databaseService.fetchUserByEmail(request.email().trim().toLowerCase(Locale.ROOT)) != null) {
            throw new IllegalArgumentException("Email already exists.");
        }

        StoredUser user = new StoredUser(
            request.username().trim(),
            request.name().trim(),
            request.email().trim().toLowerCase(Locale.ROOT),
            passwordEncoder.encode(request.password()),
            "USER"
        );
        databaseService.upsertUser(user);
        return toAppUser(user);
    }

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        StoredUser user = databaseService.fetchUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new User(
            user.username(),
            user.passwordHash(),
            java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))
        );
    }

    public AppUser currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        StoredUser user = databaseService.fetchUserByUsername(authentication.getName());
        return user == null ? null : toAppUser(user);
    }

    private void validateSignup(SignupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Signup request is required.");
        }
        if (blank(request.username()) || blank(request.name()) || blank(request.email()) || blank(request.password())) {
            throw new IllegalArgumentException("Username, name, email, and password are required.");
        }
        if (!request.email().contains("@")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (!PASSWORD_PATTERN.matcher(request.password()).matches()) {
            throw new IllegalArgumentException(
                "Password must be at least 8 characters and include uppercase, lowercase, number, and special character."
            );
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private AppUser toAppUser(StoredUser user) {
        return new AppUser(user.username(), user.name(), user.email(), user.role());
    }
}
