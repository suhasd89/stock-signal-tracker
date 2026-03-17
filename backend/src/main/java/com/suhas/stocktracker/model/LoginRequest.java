package com.suhas.stocktracker.model;

public record LoginRequest(
    String username,
    String password
) {
}
