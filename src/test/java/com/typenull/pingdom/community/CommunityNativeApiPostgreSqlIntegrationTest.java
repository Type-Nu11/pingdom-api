package com.typenull.pingdom.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false", "fcm.enabled=false", "outbox.enabled=false"
})
@AutoConfigureMockMvc
class CommunityNativeApiPostgreSqlIntegrationTest {
    @Container private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    /**
     * native 쿼리와 마이그레이션이 실제 PostGIS 컨테이너를 사용하도록 PostgreSQL 접속 정보를 등록한다.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    /**
     * 조회·좋아요·연결 기록을 먼저 제거한 뒤 게시글·장소·사용자를 정리해 외래 키 충돌을 방지한다.
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM community_place_daily_view");
        jdbcTemplate.update("DELETE FROM community_post_like");
        jdbcTemplate.update("DELETE FROM community_post_place");
        jdbcTemplate.update("DELETE FROM community_post");
        jdbcTemplate.update("DELETE FROM map_place");
        userRepository.deleteAllInBatch();
    }

    /**
     * PostgreSQL에 연결된 게시글과 장소를 준비해 좋아요 요청이 수 1과 true를 반환하는지 검증한다.
     * 동일 사용자의 장소 진입을 반복해도 조회수가 1이며 일반 장소 상세에도 같은 집계가 노출되는지 확인한다.
     */
    @Test
    void connectsNativeCommunityInteractions() throws Exception {
        User user = userRepository.saveAndFlush(User.builder().username("community-native-user")
                .email("community-native-user@example.com").emailVerified(true).password("password")
                .birthYear(1995).language("ko").country("KR").role(UserRole.USER).build());
        Long placeId = jdbcTemplate.queryForObject("""
                INSERT INTO map_place (place_name, address, latitude, longitude, registrant, photo_count)
                VALUES ('Native 장소', '서울시 중구', 37.5, 127.0, 'test', 0) RETURNING map_place_id
                """, Long.class);
        Long postId = jdbcTemplate.queryForObject("""
                INSERT INTO community_post (category_id, title, content, user_id)
                VALUES ('PLACE', 'Native 게시글', '본문', ?) RETURNING community_post_id
                """, Long.class, user.getId());
        jdbcTemplate.update("INSERT INTO community_post_place (community_post_id, map_place_id) VALUES (?, ?)", postId, placeId);
        String bearer = "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        mockMvc.perform(post("/community/posts/{postId}/likes", postId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1)).andExpect(jsonPath("$.liked").value(true));
        mockMvc.perform(post("/community/posts/{postId}/places/{placeId}/view", postId, placeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.communityViewCount").value(1));
        mockMvc.perform(post("/community/posts/{postId}/places/{placeId}/view", postId, placeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.communityViewCount").value(1));
        mockMvc.perform(get("/places/{placeId}", placeId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.communityViewCount").value(1));
    }
}
