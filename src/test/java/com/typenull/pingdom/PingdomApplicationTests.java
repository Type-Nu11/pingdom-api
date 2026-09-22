package com.typenull.pingdom;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")
@SpringBootTest
class PingdomApplicationTests {

    /**
     * SpringBootTest가 애플리케이션 컨텍스트와 의존 빈을 구성할 수 있는지 확인.
     * 본문의 별도 assertion 없이 컨텍스트 초기화 실패 자체가 테스트 실패가 됨.
     */
    @Test
    void contextLoads() {
    }

}
