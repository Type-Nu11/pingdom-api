package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.shared.observability.OutboxMetrics;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Handler 실행 풀과 분리한 heartbeat와 선점별 최대 실행 시간을 관리한다. */
@Component
@Slf4j
public class OutboxLeaseManager {

    private final OutboxEventStateService stateService;
    private final OutboxProperties properties;
    private final ScheduledExecutorService scheduler;
    private final OutboxMetrics metrics;
    private final LongSupplier nanoTime;

    @Autowired
    public OutboxLeaseManager(OutboxEventStateService stateService, OutboxProperties properties, OutboxMetrics metrics) {
        this(stateService, properties, newScheduler(properties), metrics, System::nanoTime);
    }

    OutboxLeaseManager(OutboxEventStateService stateService, OutboxProperties properties,
            ScheduledExecutorService scheduler, OutboxMetrics metrics, LongSupplier nanoTime) {
        this.stateService = stateService;
        this.properties = properties;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.nanoTime = nanoTime;
    }

    private static ScheduledExecutorService newScheduler(OutboxProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                properties.workerConcurrency(), task -> {
                    Thread thread = new Thread(task, "outbox-heartbeat-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    public Lease track(OutboxClaim claim) {
        Lease lease = new Lease(claim, Thread.currentThread(), nanoTime.getAsLong());
        lease.future = scheduler.scheduleWithFixedDelay(lease::heartbeat,
                properties.heartbeatInterval().toMillis(), properties.heartbeatInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        return lease;
    }

    public final class Lease implements AutoCloseable {
        private final OutboxClaim claim;
        private final Thread ownerThread;
        private final long startedAt;
        private ScheduledFuture<?> future;
        private boolean closed;

        private Lease(OutboxClaim claim, Thread ownerThread, long startedAt) {
            this.claim = claim;
            this.ownerThread = ownerThread;
            this.startedAt = startedAt;
        }

        private synchronized void heartbeat() {
            if (closed) {
                return;
            }
            if (nanoTime.getAsLong() - startedAt >= properties.maxExecutionTime().toNanos()) {
                loseOwnership("max_execution_time");
                return;
            }
            try {
                if (!stateService.renewClaim(claim)) {
                    loseOwnership("ownership_lost");
                }
            } catch (RuntimeException exception) {
                metrics.recordLeaseEvent("renewal_error");
                log.warn("Outbox heartbeat 갱신 오류. lease 만료 시 복구 대상이 됩니다. eventId={}",
                        claim.eventId(), exception);
            }
        }

        private void loseOwnership(String reason) {
            close();
            metrics.recordLeaseEvent(reason);
            // 취소는 협조 요청이며 worker 슬롯은 실제 Runnable의 finally에서만 반환한다.
            ownerThread.interrupt();
            log.warn("Outbox 처리권 연장 중지. eventId={}, reason={}", claim.eventId(), reason);
        }

        /** heartbeat를 먼저 해제하여 정상 완료와 갱신의 경합을 소유권 장애로 오인하지 않는다. */
        public synchronized <T> T finish(Supplier<T> completion) {
            close();
            return completion.get();
        }

        @Override
        public synchronized void close() {
            closed = true;
            if (future != null) {
                future.cancel(false);
            }
        }
    }
}
