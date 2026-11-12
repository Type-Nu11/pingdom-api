package com.typenull.pingdom.consultation.application;

import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import java.time.Duration;
import java.sql.SQLException;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.concurrent.locks.LockSupport;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;

/** 완료 대기 시간은 세션 만료·처리 lease와 분리하고 provider 실행을 취소하지 않는다. */
final class VoiceAiReplayWait {
    static final int DATABASE_ATTEMPT_TIMEOUT_SECONDS = 1;
    private static final long MAX_WAIT_NANOS = Duration.ofSeconds(30).toNanos();
    private static final long MAX_PAUSE_NANOS = Duration.ofSeconds(1).toNanos();

    private final LongSupplier nanoTime;
    private final LongConsumer park;
    private final long startedAt;
    private long pauseNanos = Duration.ofMillis(100).toNanos();

    static VoiceAiReplayWait start() {
        return new VoiceAiReplayWait(System::nanoTime, LockSupport::parkNanos);
    }

    VoiceAiReplayWait(LongSupplier nanoTime, LongConsumer park) {
        this.nanoTime = nanoTime;
        this.park = park;
        this.startedAt = nanoTime.getAsLong();
    }

    void beforeAttempt() {
        checkWaiting();
        // 다음 짧은 DB 트랜잭션이 남은 대기 예산보다 길어지지 않도록 여유를 확보한다.
        if (remainingNanos() < Duration.ofSeconds(DATABASE_ATTEMPT_TIMEOUT_SECONDS).toNanos()) {
            throw unavailable();
        }
    }

    static boolean isDatabaseWaitFailure(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof QueryTimeoutException
                    || cause instanceof PessimisticLockingFailureException
                    || cause instanceof TransactionTimedOutException
                    || cause instanceof jakarta.persistence.QueryTimeoutException
                    || cause instanceof SQLException sql
                        && ("57014".equals(sql.getSQLState()) || "55P03".equals(sql.getSQLState()))
                    // Hibernate의 트랜잭션 기한 만료는 JpaSystemException 등으로 감싸질 수 있다.
                    || cause instanceof org.hibernate.TransactionException
                        && "transaction timeout expired".equals(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }

    void pause() {
        checkWaiting();
        long delay = Math.min(pauseNanos, remainingNanos());
        long pauseStartedAt = nanoTime.getAsLong();
        long remainingDelay = delay;
        while (remainingDelay > 0) {
            park.accept(remainingDelay);
            checkWaiting();
            // park는 조기에 깨어날 수 있으므로 재조회까지의 간격을 실제 경과 시간으로 지킨다.
            remainingDelay = delay - (nanoTime.getAsLong() - pauseStartedAt);
        }
        pauseNanos = Math.min(pauseNanos * 2, MAX_PAUSE_NANOS);
    }

    private void checkWaiting() {
        if (Thread.currentThread().isInterrupted() || remainingNanos() <= 0) {
            throw unavailable();
        }
    }

    private long remainingNanos() {
        return MAX_WAIT_NANOS - (nanoTime.getAsLong() - startedAt);
    }

    private VoiceAiException unavailable() {
        return new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE);
    }
}
