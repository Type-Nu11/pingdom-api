package com.typenull.pingdom.place.infrastructure.persistence.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListItem;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListResponse;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.place.application.service.place.PlaceSearchCondition;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 PostGIS에서 메뉴 OR 검색·중복 없는 페이지 집계·필터 후 거리순 제한을 검증한다. */
@Tag("postgres-integration")
@Testcontainers
@Transactional
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class PlaceMenuSearchPostgreSqlIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlaceQueryService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwt;
    private long ownerId;

    @BeforeEach
    void fixtureOwner() {
        jdbc.update("DELETE FROM map_place");
        ownerId = users.saveAndFlush(User.builder().username("menu-search-owner")
                .email("menu-search@example.invalid").password("unused").birthYear(1995)
                .language("ko").country("KR").role(UserRole.USER).build()).getId();
    }

    @Test
    void searchesNamesOrMenusWithoutDuplicatingPlacesOrPageCounts() {
        long named = place("원조 갈비탕집", "음식점", 37.5);
        menu(named, "갈비탕", "AVAILABLE");
        menu(named, "특 갈비탕", "AVAILABLE");
        long menuOnly = place("할머니 식당", "음식점", 37.501);
        menu(menuOnly, "한우 갈비탕", "AVAILABLE");
        place("평범한 식당", "음식점", 37.502);

        var first = search("  갈비탕  ", "음식점", 1, 1, false);
        var second = search("갈비탕", "음식점", 2, 1, false);
        assertThat(first.totalCount()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.totalCount()).isEqualTo(2);
        assertThat(second.hasNext()).isFalse();
        assertThat(first.places()).extracting(PlaceListItem::id).containsExactly(menuOnly);
        assertThat(second.places()).extracting(PlaceListItem::id).containsExactly(named);
    }

    @Test
    void stillFindsNamesEnglishNamesAndAddressesWithoutRegisteredMenus() {
        long named = place("원조 갈비탕집", "음식점", 37.501);
        long english = place("영문 식당", "음식점", 37.502);
        jdbc.update("UPDATE map_place SET english_name='Beef Soup House' WHERE map_place_id=?", english);
        long addressed = place("주소 식당", "음식점", 37.503);
        jdbc.update("UPDATE map_place SET road_address='갈비탕로 10' WHERE map_place_id=?", addressed);

        assertThat(search("갈비탕", null, 1, 20, false).places())
                .extracting(PlaceListItem::id).containsExactlyInAnyOrder(named, addressed);
        assertThat(search("beef soup", null, 1, 20, false).places())
                .extracting(PlaceListItem::id).containsExactly(english);
    }

    @Test
    void usesOnlyPublicMenuNamesAndKeepsCategoryAsAnAndFilter() {
        long available = place("식당 A", "음식점", 37.5);
        menu(available, "갈비탕", "AVAILABLE");
        long soldOut = place("식당 B", "음식점", 37.501);
        menu(soldOut, "갈비탕", "SOLD_OUT");
        long hidden = place("식당 C", "음식점", 37.502);
        menu(hidden, "갈비탕", "HIDDEN");
        menu(hidden, "갈비탕", "INACTIVE");
        menu(hidden, "김치찌개", "AVAILABLE");
        jdbc.update("UPDATE place_menu SET description='갈비탕' WHERE name='김치찌개'");
        long cafe = place("카페", "카페", 37.503);
        menu(cafe, "갈비탕", "AVAILABLE");

        assertThat(search("갈비탕", "음식점", 1, 20, false).places())
                .extracting(PlaceListItem::id).containsExactlyInAnyOrder(available, soldOut);
        assertThat(search("갈비탕", null, 1, 20, false).totalCount()).isEqualTo(3);
    }

    @Test
    void filtersRadiusAndVisibilityBeforeNearestLimit() {
        long first = place("첫 식당", "음식점", 37.501);
        long second = place("둘째 식당", "음식점", 37.502);
        long third = place("셋째 식당", "음식점", 37.503);
        long fourth = place("넷째 식당", "음식점", 37.504);
        long outside = place("먼 식당", "음식점", 38.5);
        long hidden = place("숨김 식당", "음식점", 37.5001);
        long closed = place("폐업 식당", "음식점", 37.5002);
        for (long id : new long[]{first, second, third, fourth, outside, hidden, closed}) {
            menu(id, "갈비탕", "AVAILABLE");
        }
        jdbc.update("UPDATE map_place SET discovery_status='HIDDEN' WHERE map_place_id=?", hidden);
        jdbc.update("UPDATE map_place SET operating_status='PERMANENTLY_CLOSED' WHERE map_place_id=?", closed);

        var result = search("갈비탕", null, 1, 3, true);
        assertThat(result.totalCount()).isEqualTo(4);
        assertThat(result.places()).extracting(PlaceListItem::id).containsExactly(first, second, third);
        assertThat(result.places()).extracting(PlaceListItem::distanceMeters).isSorted();
        assertThat(search("갈비탕", null, 1, 20, true).places()).hasSize(4);
    }

    @Test
    void preservesKeywordFreeExplorationAndEmptyResults() {
        place("식당", "음식점", 37.501);
        place("카페", "카페", 37.502);
        for (String keyword : Arrays.asList(null, "", "   ")) {
            var result = search(keyword, null, 1, 20, true);
            assertThat(result.places()).hasSize(2);
            assertThat(result.totalCount()).isEqualTo(2);
        }
        var empty = search("등록되지 않은 메뉴", null, 1, 20, true);
        assertThat(empty.places()).isEmpty();
        assertThat(empty.totalCount()).isZero();
        assertThat(empty.hasNext()).isFalse();
    }

    @Test
    void ignoresEnglishCaseAndEscapesLikeWildcardsInMenuNames() {
        long literal = place("메뉴 식당", "음식점", 37.501);
        menu(literal, "BeEf Soup 100% A_B C\\D", "AVAILABLE");
        long other = place("다른 식당", "음식점", 37.502);
        menu(other, "1000 A-B CD", "AVAILABLE");
        for (String keyword : new String[]{"beef soup", "100%", "A_B", "C\\D"}) {
            assertThat(search(keyword, null, 1, 20, false).places())
                    .extracting(PlaceListItem::id).containsExactly(literal);
        }
    }

    @Test
    void returnsExistingApiFormatWithoutReservationParameters() throws Exception {
        long placeId = place("할머니 식당", "음식점", 37.501);
        menu(placeId, "갈비탕", "AVAILABLE");
        String bearer = "Bearer " + jwt.generateAccessToken(ownerId, "menu-search-owner", "USER");
        mockMvc.perform(get("/places").header(HttpHeaders.AUTHORIZATION, bearer)
                        .param("keyword", "갈비탕").param("category", "음식점")
                        .param("latitude", "37.5").param("longitude", "127.0")
                        .param("radiusKm", "3").param("sort", "NEAREST").param("limit", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.places[0].id").value(placeId))
                .andExpect(jsonPath("$.places[0].name").value("할머니 식당"));
        mockMvc.perform(get("/places").header(HttpHeaders.AUTHORIZATION, bearer).param("keyword", "없는메뉴"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.places").isEmpty())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    private PlaceListResponse search(String keyword, String category, int page, int limit, boolean nearby) {
        return service.listPlaces(new PlaceSearchCondition(page, limit, keyword, category, null,
                nearby ? 37.5 : null, nearby ? 127.0 : null, nearby ? 3.0 : null,
                nearby ? "NEAREST" : "LATEST"));
    }

    /** 좌표와 geography 검색용 geometry를 함께 저장해 실제 반경 필터를 통과시킨다. */
    private long place(String name, String category, double latitude) {
        return jdbc.queryForObject("""
                INSERT INTO map_place (place_name, address, category, latitude, longitude,
                    registrant, photo_count, location)
                VALUES (?, '서울', ?, ?, 127.0, 'test', 0,
                    ST_SetSRID(ST_MakePoint(127.0, ?), 4326)) RETURNING map_place_id
                """, Long.class, name, PlaceCategoryPolicy.normalize(category), latitude, latitude);
    }

    private void menu(long placeId, String name, String status) {
        jdbc.update("""
                INSERT INTO place_menu (place_id, merchant_owner_user_id, name, price_amount,
                    currency, status, display_order, created_at, updated_at)
                VALUES (?, ?, ?, 12000, 'KRW', ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, placeId, ownerId, name, status);
    }
}
