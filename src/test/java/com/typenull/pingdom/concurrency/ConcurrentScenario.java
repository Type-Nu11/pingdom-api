package com.typenull.pingdom.concurrency;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class ConcurrentScenario {

    private ConcurrentScenario() {
    }

    static <T> List<Result<T>> run(Duration timeout, Callable<T> first, Callable<T> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Result<T>> firstFuture = executor.submit(attempt(start, first));
            Future<Result<T>> secondFuture = executor.submit(attempt(start, second));
            long timeoutMillis = timeout.toMillis();
            return List.of(
                    firstFuture.get(timeoutMillis, TimeUnit.MILLISECONDS),
                    secondFuture.get(timeoutMillis, TimeUnit.MILLISECONDS)
            );
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("동시성 테스트 작업이 제한 시간 안에 종료되지 않았습니다.");
            }
        }
    }

    private static <T> Callable<Result<T>> attempt(CyclicBarrier start, Callable<T> task) {
        return () -> {
            start.await();
            try {
                return Result.success(task.call());
            } catch (Throwable throwable) {
                return Result.failure(throwable);
            }
        };
    }

    record Result<T>(T value, Throwable failure) {
        static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> failure(Throwable failure) {
            return new Result<>(null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }
    }
}
