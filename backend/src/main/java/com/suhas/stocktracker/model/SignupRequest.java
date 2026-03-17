package com.suhas.stocktracker.model;

public record SignupRequest(
    String username,
    String name,
    String email,
    String password
) {
}
