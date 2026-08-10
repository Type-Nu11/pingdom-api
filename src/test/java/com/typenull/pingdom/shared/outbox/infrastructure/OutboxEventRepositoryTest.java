package com.typenull.pingdom.shared.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

class OutboxEventRepositoryTest {

    @Test
    void lockingQueriesUseSkipLockedHint() {
        assertSkipLockedHint("findReadyEventsForUpdate");
        assertSkipLockedHint("findStaleProcessingEventsForUpdate");
    }

    @Test
    void manualRetryLookupUsesPessimisticWriteLock() {
        Method method = Arrays.stream(OutboxEventRepository.class.getMethods())
                .filter(candidate -> candidate.getName().equals("findByEventIdForUpdate"))
                .findFirst()
                .orElseThrow();

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private void assertSkipLockedHint(String methodName) {
        Method method = Arrays.stream(OutboxEventRepository.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        QueryHints queryHints = method.getAnnotation(QueryHints.class);

        assertThat(queryHints).isNotNull();
        assertThat(queryHints.value())
                .anySatisfy(hint -> {
                    assertThat(hint.name()).isEqualTo("jakarta.persistence.lock.timeout");
                    assertThat(hint.value()).isEqualTo("-2");
                });
    }
}
