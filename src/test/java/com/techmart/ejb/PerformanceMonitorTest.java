package com.techmart.ejb;

import com.techmart.model.PerformanceMetric;
import com.techmart.util.PerformanceMonitor;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PerformanceMonitor singleton utility.
 *
 * PerformanceMonitor has no injected dependencies so no mocks are needed —
 * we test it as a plain Java object.
 *
 * Tests cover:
 *   - Basic record/read cycle
 *   - Average latency calculation accuracy
 *   - Rolling history window enforcement (max 500 entries)
 *   - Concurrent write safety (no lost updates under parallel load)
 *   - Summary aggregation correctness
 *   - Zero-invocation edge cases
 */
@DisplayName("PerformanceMonitor Unit Tests")
class PerformanceMonitorTest {

    private PerformanceMonitor monitor;

    @BeforeEach
    void setUp() throws Exception {
        monitor = new PerformanceMonitor();
        // Simulate @PostConstruct
        monitor.getClass().getDeclaredMethod("init").setAccessible(true);
        monitor.getClass().getDeclaredMethod("init").invoke(monitor);
    }

    // ----------------------------------------------------------------
    // Basic record and retrieval
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("record() and retrieval")
    class RecordAndRetrievalTests {

        @Test
        @DisplayName("Invocation count is 1 after a single record")
        void invocationCountAfterSingleRecord() {
            monitor.record("TestComponent", "testOp", 42L);
            assertEquals(1L, monitor.getInvocationCount("TestComponent", "testOp"));
        }

        @Test
        @DisplayName("Invocation count accumulates across multiple records")
        void invocationCountAccumulates() {
            monitor.record("TestComponent", "testOp", 10L);
            monitor.record("TestComponent", "testOp", 20L);
            monitor.record("TestComponent", "testOp", 30L);
            assertEquals(3L, monitor.getInvocationCount("TestComponent", "testOp"));
        }

        @Test
        @DisplayName("Average latency is accurate after multiple records")
        void averageLatencyAccuracy() {
            monitor.record("TestComponent", "testOp", 100L);
            monitor.record("TestComponent", "testOp", 200L);
            monitor.record("TestComponent", "testOp", 300L);

            double avg = monitor.getAverageLatency("TestComponent", "testOp");
            assertEquals(200.0, avg, 0.001);
        }

        @Test
        @DisplayName("Average latency is 0 before any records")
        void averageLatencyIsZeroBeforeRecords() {
            double avg = monitor.getAverageLatency("NoSuchComponent", "noSuchOp");
            assertEquals(0.0, avg, 0.001);
        }

        @Test
        @DisplayName("Invocation count is 0 before any records")
        void invocationCountIsZeroBeforeRecords() {
            assertEquals(0L, monitor.getInvocationCount("NoSuchComponent", "noOp"));
        }

        @Test
        @DisplayName("Different component.operation keys are tracked independently")
        void differentKeysTrackedIndependently() {
            monitor.record("ComponentA", "opX", 50L);
            monitor.record("ComponentA", "opX", 50L);
            monitor.record("ComponentB", "opY", 200L);

            assertEquals(2L, monitor.getInvocationCount("ComponentA", "opX"));
            assertEquals(1L, monitor.getInvocationCount("ComponentB", "opY"));
            assertEquals(50.0,  monitor.getAverageLatency("ComponentA", "opX"),  0.001);
            assertEquals(200.0, monitor.getAverageLatency("ComponentB", "opY"), 0.001);
        }
    }

    // ----------------------------------------------------------------
    // Rolling history window
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Rolling history window")
    class RollingHistoryTests {

        @Test
        @DisplayName("History list never exceeds MAX_HISTORY (500) entries")
        void historyNeverExceedsMaxSize() {
            for (int i = 0; i < 600; i++) {
                monitor.record("Component", "op", i);
            }

            List<PerformanceMetric> history = monitor.getRecentMetrics();
            assertTrue(history.size() <= 500,
                "History size " + history.size() + " exceeds maximum of 500");
        }

        @Test
        @DisplayName("Recent metrics list is populated after records")
        void recentMetricsPopulatedAfterRecords() {
            monitor.record("C", "op", 10L);
            monitor.record("C", "op", 20L);

            List<PerformanceMetric> recent = monitor.getRecentMetrics();
            assertFalse(recent.isEmpty());
        }

        @Test
        @DisplayName("Each metric entry has correct component and unit")
        void metricEntryHasCorrectFields() {
            monitor.record("MyBean", "myMethod", 75L);

            List<PerformanceMetric> recent = monitor.getRecentMetrics();
            PerformanceMetric last = recent.get(recent.size() - 1);

            assertEquals("MyBean",    last.getComponent());
            assertEquals("myMethod",  last.getMetricName());
            assertEquals(75.0,        last.getValue(), 0.001);
            assertEquals("ms",        last.getUnit());
            assertNotNull(last.getRecordedAt());
        }
    }

    // ----------------------------------------------------------------
    // getSummary()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getSummary()")
    class GetSummaryTests {

        @Test
        @DisplayName("Summary is empty before any records")
        void summaryEmptyBeforeRecords() {
            assertTrue(monitor.getSummary().isEmpty());
        }

        @Test
        @DisplayName("Summary contains one row per unique component.operation key")
        void summaryContainsOneRowPerKey() {
            monitor.record("BeanA", "opA", 100L);
            monitor.record("BeanA", "opA", 200L);  // same key — should not add new row
            monitor.record("BeanB", "opB", 150L);

            List<Map<String, Object>> summary = monitor.getSummary();
            assertEquals(2, summary.size());
        }

        @Test
        @DisplayName("Summary row contains key, invocations, and avgLatencyMs fields")
        void summaryRowContainsRequiredFields() {
            monitor.record("BeanA", "opA", 100L);

            List<Map<String, Object>> summary = monitor.getSummary();
            assertFalse(summary.isEmpty());

            Map<String, Object> row = summary.get(0);
            assertTrue(row.containsKey("key"));
            assertTrue(row.containsKey("invocations"));
            assertTrue(row.containsKey("avgLatencyMs"));
        }

        @Test
        @DisplayName("Summary invocation count matches recorded count")
        void summaryInvocationCountMatchesRecordedCount() {
            monitor.record("BeanC", "opC", 50L);
            monitor.record("BeanC", "opC", 50L);
            monitor.record("BeanC", "opC", 50L);

            List<Map<String, Object>> summary = monitor.getSummary();
            Map<String, Object> row = summary.stream()
                .filter(r -> "BeanC.opC".equals(r.get("key")))
                .findFirst()
                .orElseThrow();

            assertEquals(3L, row.get("invocations"));
        }
    }

    // ----------------------------------------------------------------
    // Concurrent safety
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent write safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("No lost updates under 50 concurrent recording threads")
        void noLostUpdatesUnderConcurrentLoad() throws InterruptedException {
            int threadCount = 50;
            int recordsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                pool.submit(() -> {
                    try {
                        for (int i = 0; i < recordsPerThread; i++) {
                            monitor.record("ConcurrentComponent", "concurrentOp", i);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            pool.shutdown();

            assertEquals(0, errors.get(), "Concurrent recording produced errors");
            assertEquals(
                (long) threadCount * recordsPerThread,
                monitor.getInvocationCount("ConcurrentComponent", "concurrentOp"),
                "Some concurrent updates were lost"
            );
        }

        @Test
        @DisplayName("getRecentMetrics() does not throw under concurrent modifications")
        void getRecentMetricsDoesNotThrowUnderConcurrentModification()
                throws InterruptedException {
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int tid = t;
                pool.submit(() -> {
                    try {
                        for (int i = 0; i < 50; i++) {
                            if (i % 2 == 0) {
                                monitor.record("Thread-" + tid, "op", i);
                            } else {
                                monitor.getRecentMetrics();
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            pool.shutdown();

            assertEquals(0, errors.get(),
                "Concurrent read/write on recent metrics produced exceptions");
        }
    }

    // ----------------------------------------------------------------
    // startTime
    // ----------------------------------------------------------------

    @Test
    @DisplayName("startTime is set to a non-null value after init")
    void startTimeIsSetAfterInit() {
        assertNotNull(monitor.getStartTime());
    }
}
