package com.example.demo.trip.integration;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ExpiringCache<K, V> {
    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public ExpiringCache() { this(Clock.systemUTC()); }
    ExpiringCache(Clock clock) { this.clock = clock; }

    public Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (clock.millis() >= entry.expiresAt()) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(K key, V value, Duration ttl) {
        if (key != null && value != null && !ttl.isNegative() && !ttl.isZero())
            entries.put(key, new Entry<>(value, clock.millis() + ttl.toMillis()));
    }

    private record Entry<V>(V value, long expiresAt) {}
}
