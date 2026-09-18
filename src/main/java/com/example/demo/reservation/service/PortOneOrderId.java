package com.example.demo.reservation.service;

import java.util.UUID;

public final class PortOneOrderId {
    private PortOneOrderId() {}

    public static String create() {
        return "KPTEST-" + UUID.randomUUID().toString().replace("-", ""); // 39 characters
    }

    public static String providerId(String stored) {
        // Compatibility only for the previous 43-character UUID format observed truncated by the provider.
        return stored != null && stored.matches("KPTEST-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                ? stored.substring(0, 40) : stored;
    }
}
