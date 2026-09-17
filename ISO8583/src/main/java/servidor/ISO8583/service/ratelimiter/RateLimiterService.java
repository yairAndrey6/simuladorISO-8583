package servidor.ISO8583.service.ratelimiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class RateLimiterService {

    @Value("${iso8583.rate-limit.max-requests:4}")
    private int maxRequests = 4;

    @Value("${iso8583.rate-limit.window-seconds:60}")
    private long windowSeconds = 60;

    @jakarta.annotation.PostConstruct
    public void init() {
    }

    private static class CounterWindow {
        final AtomicInteger count = new AtomicInteger(0);
        final long expiresAt;

        CounterWindow(long windowDurationMs) {
            this.expiresAt = System.currentTimeMillis() + windowDurationMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final ConcurrentHashMap<String, CounterWindow> limits = new ConcurrentHashMap<>();

    public boolean isAllowed(String terminalId, String stan, String rrn) {
        String key = terminalId != null ? terminalId.trim() : "UNKNOWN";
        long windowMs = windowSeconds * 1000L;

        CounterWindow window = limits.compute(key, (k, current) -> {
            if (current == null || current.isExpired()) {
                CounterWindow nw = new CounterWindow(windowMs);
                nw.count.set(1);
                return nw;
            } else {
                current.count.incrementAndGet();
                return current;
            }
        });

        int currentCount = window.count.get();
        boolean allowed = currentCount <= maxRequests;


        if (!allowed) {
            log.warn("Límite superado para terminal",
                    terminalId, currentCount, maxRequests, windowSeconds);
        }

        return allowed;
    }

    public void reset(String terminalId) {
        if (terminalId != null) {
            limits.remove(terminalId.trim());
            log.info("Contador reiniciado para terminal: ", terminalId.trim());
        }
    }

    public void resetAll() {
        limits.clear();
    }

    public int getMaxRequests() {
        return maxRequests;
    }
}
