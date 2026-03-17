package com.suhas.stocktracker.model;

public record AppUser(
    String username,
    String name,
    String email,
    String role
) {
}
