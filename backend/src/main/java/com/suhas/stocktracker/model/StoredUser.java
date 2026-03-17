package com.suhas.stocktracker.model;

public record StoredUser(
    String username,
    String name,
    String email,
    String passwordHash,
    String role
) {
}
