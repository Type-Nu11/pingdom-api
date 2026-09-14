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

    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @AfterEach void cleanup() {
        jdbcTemplate.update("DELETE FROM community_place_daily_view");
        jdbcTemplate.update("DELETE FROM community_post_like");
        jdbcTemplate.update("DELETE FROM community_post_place");
        jdbcTemplate.update("DELETE FROM community_post");
        jdbcTemplate.update("DELETE FROM map_place");
        userRepository.deleteAllInBatch();
    }

    @Test
    void 좋아요와_게시글_경유_장소_조회가_PostgreSQL에서_연결된다() throws Exception {
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
