package com.example.demo.trip.integration;

import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringCacheTest {
    @Test
    void returnsValueUntilTtlExpires() {
        MutableClock clock = new MutableClock();
        ExpiringCache<String, String> cache = new ExpiringCache<>(clock);
        cache.put("busan", "sunny", Duration.ofMinutes(10));

        assertThat(cache.get("busan")).contains("sunny");
        clock.advance(Duration.ofMinutes(11));
        assertThat(cache.get("busan")).isEmpty();
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-12T06:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant; }
    }
}
