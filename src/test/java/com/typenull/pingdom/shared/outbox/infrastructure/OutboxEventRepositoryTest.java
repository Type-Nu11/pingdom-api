package com.typenull.pingdom.shared.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

class OutboxEventRepositoryTest {

    /**
     * 준비 이벤트와 stale 이벤트 선점 조회 모두 SKIP LOCKED용 lock timeout hint를 선언하는지 확인한다. 실제 DB 경합 검증은 아니다.
     */
    @Test
    void lockingQueriesUseSkipLockedHint() {
        assertSkipLockedHint("findReadyEventsForUpdate");
        assertSkipLockedHint("findStaleProcessingEventsForUpdate");
    }

    /**
     * 수동 재시도 조회 메서드가 PESSIMISTIC_WRITE를 선언해 상태 변경 대상의 잠금을 요구하는지 reflection으로 검증한다.
     */
    @Test
    void locksManualRetryLookup() {
        Method method = Arrays.stream(OutboxEventRepository.class.getMethods())
                .filter(candidate -> candidate.getName().equals("findByEventIdForUpdate"))
                .findFirst()
                .orElseThrow();

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * 지정 저장소 메서드의 QueryHints에 jakarta.persistence.lock.timeout=-2가 있는지 확인한다.
     */
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
