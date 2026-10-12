package com.typenull.pingdom.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
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
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false", "fcm.enabled=false", "outbox.enabled=false"
})
@AutoConfigureMockMvc
class CommunityAppContractPostgreSqlIntegrationTest {
    @Container private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    /**
     * native 쿼리와 마이그레이션이 실제 PostGIS 컨테이너를 사용하도록 PostgreSQL 접속 정보를 등록.
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


    @Autowired private ObjectMapper mapper;
    @MockBean private S3ObjectStorage storage;
    private User author;
    private String bearer;
    @BeforeEach void setup() {
        author = userRepository.saveAndFlush(User.builder().username("contract-author").email("contract-author@example.invalid")
                .emailVerified(true).password("unused").birthYear(1995).language("ko").country("KR").role(UserRole.USER).build());
        bearer = token(author);
        when(storage.putAtKey(any(), anyString(), anyString())).thenAnswer(call ->
                new S3ObjectStorage.S3PutResult(call.getArgument(2), "https://example.invalid/"+call.getArgument(2)));
    }
    private String token(User user) {
        return "Bearer "+jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
    @AfterEach void cleanup() {
        jdbcTemplate.update("delete from community_comment_like");
        jdbcTemplate.update("delete from community_post_image");
        jdbcTemplate.update("delete from community_post_comment");
        jdbcTemplate.update("delete from community_post_like");
        jdbcTemplate.update("delete from community_post_place");
        jdbcTemplate.update("delete from community_post");
        jdbcTemplate.update("delete from map_place");
        jdbcTemplate.update("delete from place_administrative_region where region_code='99998'");
        userRepository.deleteAllInBatch();
    }
    private long create(String body) throws Exception {
        String result=mockMvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION,bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).path("postId").asLong();
    }
    private long upload() throws Exception {
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6S0AAAAASUVORK5CYII=");
        String response=mockMvc.perform(multipart("/community/images").file(new MockMultipartFile("file","test.png","image/png",png))
                .header(HttpHeaders.AUTHORIZATION,bearer)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("imageId").asLong();
    }
    @Test void orderedImagesCountryAndViewCountPreserveOldFields() throws Exception {
        long first=upload(),second=upload();
        long postId=create("""
            {"categoryId":"TRAVEL","title":"후기","content":"서울 여행 미리보기","countryCode":"KR","region":"서울","imageIds":[%d,%d]}
            """.formatted(second,first));
        mockMvc.perform(get("/community/posts/{id}",postId).header(HttpHeaders.AUTHORIZATION,bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.images[0].imageId").value(second))
            .andExpect(jsonPath("$.images[1].imageId").value(first)).andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(get("/community/posts/{id}",postId).header(HttpHeaders.AUTHORIZATION,bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.viewCount").value(2));
        mockMvc.perform(get("/community/posts").param("countryCode","KR").header(HttpHeaders.AUTHORIZATION,bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.posts[0].postId").value(postId))
            .andExpect(jsonPath("$.posts[0].contentPreview").value("서울 여행 미리보기"))
            .andExpect(jsonPath("$.posts[0].imageCount").value(2)).andExpect(jsonPath("$.posts[0].viewCount").value(2))
            .andExpect(jsonPath("$.posts[0].category.categoryId").value("TRAVEL"));
        mockMvc.perform(get("/community/categories/TRAVEL/posts").param("countryCode","US").header(HttpHeaders.AUTHORIZATION,bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        mockMvc.perform(get("/community/posts").param("countryCode","ZZ").header(HttpHeaders.AUTHORIZATION,bearer))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_COUNTRY"));
        mockMvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION,bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":\"TRAVEL\",\"title\":\"재사용\",\"content\":\"본문\",\"imageIds\":["+second+"]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        mockMvc.perform(delete("/community/images/{id}",first).header(HttpHeaders.AUTHORIZATION,bearer))
            .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_event where aggregate_type='COMMUNITY_IMAGE' and aggregate_id=?",Long.class,Long.toString(first))).isEqualTo(1);
    }
    @Test void repliesLikesAndHiddenParentsRespectVisibility() throws Exception {
        long postId=create("{\"categoryId\":\"TRAVEL\",\"title\":\"제목\",\"content\":\"본문\"}");
        String response=mockMvc.perform(post("/community/posts/{id}/comments",postId).header(HttpHeaders.AUTHORIZATION,bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"부모\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long parent=mapper.readTree(response).path("commentId").asLong();
        response=mockMvc.perform(post("/community/posts/{id}/comments",postId).header(HttpHeaders.AUTHORIZATION,bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"답글\",\"parentCommentId\":"+parent+"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long reply=mapper.readTree(response).path("commentId").asLong();
        long otherPost=create("{\"categoryId\":\"TRAVEL\",\"title\":\"다른 글\",\"content\":\"본문\"}");
        mockMvc.perform(post("/community/posts/{id}/comments",otherPost).header(HttpHeaders.AUTHORIZATION,bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"타 글 답글\",\"parentCommentId\":"+parent+"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARENT_COMMENT"));
        mockMvc.perform(get("/community/posts/{id}/comments",postId).param("parentCommentId",Long.toString(parent))
            .header(HttpHeaders.AUTHORIZATION,bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        for(int i=0;i<2;i++) mockMvc.perform(post("/community/comments/{id}/likes",reply).header(HttpHeaders.AUTHORIZATION,bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(get("/community/posts/{id}/comments",postId).header(HttpHeaders.AUTHORIZATION,bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments[0].parentCommentId").value(parent))
                .andExpect(jsonPath("$.comments[0].liked").value(true));
        mockMvc.perform(post("/community/posts/{id}/comments",postId).header(HttpHeaders.AUTHORIZATION,bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"중첩\",\"parentCommentId\":"+reply+"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARENT_COMMENT"));
        for(int i=0;i<2;i++) mockMvc.perform(delete("/community/comments/{id}/likes",reply).header(HttpHeaders.AUTHORIZATION,bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(0));
        jdbcTemplate.update("update community_post_comment set hidden=true where community_post_comment_id=?",parent);
        mockMvc.perform(get("/community/posts/{id}/comments",postId).header(HttpHeaders.AUTHORIZATION,bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        mockMvc.perform(post("/community/comments/{id}/likes",reply).header(HttpHeaders.AUTHORIZATION,bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/community/posts").header(HttpHeaders.AUTHORIZATION,bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posts[0].commentCount").value(0))
                .andExpect(jsonPath("$.posts[1].commentCount").value(0));
    }
    @Test void rejectsOtherUsersDuplicateExpiredAndInvalidImagesWithoutPublishingPost() throws Exception {
        long image=upload();
        User other=userRepository.saveAndFlush(User.builder().username("contract-other").email("contract-other@example.invalid")
            .password("unused").birthYear(1995).language("ko").country("KR").role(UserRole.USER).build());
        String body="{\"categoryId\":\"TRAVEL\",\"title\":\"제목\",\"content\":\"본문\",\"imageIds\":["+image+"]}";
        mockMvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION,token(other)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        mockMvc.perform(delete("/community/images/{id}",image).header(HttpHeaders.AUTHORIZATION,token(other))).andExpect(status().isNotFound());
        mockMvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION,bearer).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("["+image+"]","["+image+","+image+"]")))
            .andExpect(status().isBadRequest());
        jdbcTemplate.update("update community_post_image set expires_at=current_timestamp - interval '1 day' where id=?",image);
        mockMvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION,bearer).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("select count(*) from community_post",Long.class)).isZero();
        mockMvc.perform(multipart("/community/images").file(new MockMultipartFile("file","fake.png","image/png","invalid".getBytes()))
            .header(HttpHeaders.AUTHORIZATION,bearer)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_FILE_INVALID"));
        verify(storage,times(1)).putAtKey(any(),anyString(),anyString());
    }

    @Test void linkedPlaceReturnsDisplayRegionCategoryAndImage() throws Exception {
        jdbcTemplate.update("insert into place_administrative_region(region_code,sido,sigungu,region_name,updated_at) values('99998','테스트 시','테스트 구','테스트 시구',current_timestamp)");
        Long placeId=jdbcTemplate.queryForObject("""
                insert into map_place(place_name,address,latitude,longitude,registrant,category,region_code,image_url)
                values('장소','테스트 주소',37.5,127.0,'test','관광','99998','https://example.invalid/place.png') returning map_place_id
                """,Long.class);
        long postId=create("{\"categoryId\":\"PLACE\",\"title\":\"장소 후기\",\"content\":\"본문\",\"placeIds\":["+placeId+"]}");
        mockMvc.perform(get("/community/posts/{id}",postId).header(HttpHeaders.AUTHORIZATION,bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.places[0].region").value("테스트 시구"))
            .andExpect(jsonPath("$.places[0].category").value("관광"))
            .andExpect(jsonPath("$.places[0].imageUrl").value("https://example.invalid/place.png"));
    }
}
