package com.suhas.stocktracker.model;

public record AuthResponse(
    boolean ok,
    String message,
    AppUser user
) {
}
