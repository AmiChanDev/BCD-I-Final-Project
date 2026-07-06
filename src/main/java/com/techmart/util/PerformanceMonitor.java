package com.techmart.util;

import com.techmart.model.PerformanceMetric;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

@Singleton
@Startup
public class PerformanceMonitor {

    private static final Logger LOG = Logger.getLogger(PerformanceMonitor.class.getName());

    // Keep a rolling window of the last 500 metrics to avoid unbounded growth
    private static final int MAX_HISTORY = 500;

    // Per-component invocation counters
    private final Map<String, AtomicLong> invocationCounts = new ConcurrentHashMap<>();

    // Per-component total elapsed time (ms) — for avg latency calculation
    private final Map<String, AtomicLong> totalElapsedMs = new ConcurrentHashMap<>();

    // Rolling list of individual metric snapshots
    private final List<PerformanceMetric> metricHistory =
            Collections.synchronizedList(new ArrayList<>());

    // Application start time
    private LocalDateTime startTime;

    @PostConstruct
    public void init() {
        this.startTime = LocalDateTime.now();
        LOG.info("PerformanceMonitor initialized at " + startTime);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void record(String component, String operation, long elapsedMs) {
        String key = component + "." + operation;

        invocationCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        totalElapsedMs.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(elapsedMs);

        PerformanceMetric metric = new PerformanceMetric(component, operation, elapsedMs, "ms");

        synchronized (metricHistory) {
            if (metricHistory.size() >= MAX_HISTORY) {
                metricHistory.remove(0);
            }
            metricHistory.add(metric);
        }
    }

    @Lock(LockType.READ)
    public double getAverageLatency(String component, String operation) {
        String key = component + "." + operation;
        AtomicLong count = invocationCounts.get(key);
        AtomicLong total = totalElapsedMs.get(key);
        if (count == null || count.get() == 0) return 0.0;
        return (double) total.get() / count.get();
    }

    @Lock(LockType.READ)
    public long getInvocationCount(String component, String operation) {
        String key = component + "." + operation;
        AtomicLong count = invocationCounts.get(key);
        return count == null ? 0 : count.get();
    }

    @Lock(LockType.READ)
    public List<Map<String, Object>> getSummary() {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (String key : invocationCounts.keySet()) {
            AtomicLong count = invocationCounts.get(key);
            AtomicLong total = totalElapsedMs.get(key);
            double avg = (count != null && count.get() > 0)
                    ? (double) total.get() / count.get() : 0.0;

            Map<String, Object> row = new ConcurrentHashMap<>();
            row.put("key", key);
            row.put("invocations", count != null ? count.get() : 0L);
            row.put("avgLatencyMs", avg);
            summary.add(row);
        }
        return summary;
    }

    @Lock(LockType.READ)
    public List<PerformanceMetric> getRecentMetrics() {
        synchronized (metricHistory) {
            return new ArrayList<>(metricHistory);
        }
    }

    @Lock(LockType.READ)
    public LocalDateTime getStartTime() {
        return startTime;
    }
}
