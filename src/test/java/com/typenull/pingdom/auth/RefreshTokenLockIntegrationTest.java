package com.typenull.pingdom.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typenull.pingdom.identity.domain.User;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenLockIntegrationTest extends AuthRegressionIntegrationTestSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void refreshTokenStateLockSerializesConcurrentUpdates() throws Exception {
        User user = createUser("refreshTokenLockUser");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                userRepository.findByIdForUpdate(user.getId()).orElseThrow();
                firstLockAcquired.countDown();
                await(releaseFirstLock);
            }));
            assertTrue(firstLockAcquired.await(3, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> {
                secondAttempted.countDown();
                transactionTemplate.executeWithoutResult(status -> {
                    userRepository.findByIdForUpdate(user.getId()).orElseThrow();
                    secondLockAcquired.countDown();
                });
            });
            assertTrue(secondAttempted.await(3, TimeUnit.SECONDS));
            assertFalse(secondLockAcquired.await(300, TimeUnit.MILLISECONDS));

            releaseFirstLock.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
            assertTrue(secondLockAcquired.await(1, TimeUnit.SECONDS));
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("행 잠금 해제 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("행 잠금 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }
}
