package com.typenull.pingdom.integration.concurrency;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 두 작업의 출발을 맞추고 성공값과 예외를 모아 DB 동시성 테스트가 승자·패자를 비교하도록 지원.
 */
final class ConcurrentScenario {

    /**
     * barrier와 executor를 정적 run 호출마다 생성하므로 실행 상태를 인스턴스에 보관하지 않도록 생성을 차단.
     */
    private ConcurrentScenario() {
    }

    /**
     * 두 작업을 barrier에서 함께 출발시켜 결과를 입력 순서로 반환. 각 Future 대기와 executor 종료 대기에 각각 timeout을 적용하므로 전체 경과 시간 상한과는 다름.
     */
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

    /**
     * barrier 통과 뒤 작업의 반환값 또는 Throwable을 결과에 담음. barrier 대기 자체의 실패는 Future 예외로 전파.
     */
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
        /**
         * 정상 반환값과 null 실패값으로 작업 결과를 구성.
         */
        static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        /**
         * 예외와 null 반환값을 저장해 다른 작업의 결과와 함께 비교할 수 있게 함.
         */
        static <T> Result<T> failure(Throwable failure) {
            return new Result<>(null, failure);
        }

        /**
         * 반환값이 null인지와 무관하게 실패 예외가 없으면 성공으로 판정.
         */
        boolean succeeded() {
            return failure == null;
        }
    }
}
