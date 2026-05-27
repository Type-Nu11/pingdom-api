package com.typenull.pingdom.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.signup.SignupRequest;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.global.s3.S3ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MapPostUploadControllerTest {

    @MockBean
    private S3ObjectStorage s3ObjectStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @BeforeEach
    void setUp() {
        mapImageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void uploadPostStoresTitleDescriptionAndFileMetadata() throws Exception {
        given(s3ObjectStorage.put(any(), eq("map")))
                .willReturn(new S3ObjectStorage.S3PutResult("map/test-key.jpg", "https://example.com/test-key.jpg"));

        String accessToken = signupAndLogin("writer01");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/map/posts/create")
                        .file(file)
                        .param("title", "새 게시글 제목")
                        .param("description", "게시글 부가 설명")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게시글을 저장했습니다."));

        MapImage saved = mapImageRepository.findAll().get(0);
        assertEquals("새 게시글 제목", saved.getTitle());
        assertEquals("게시글 부가 설명", saved.getDescription());
        assertEquals("https://example.com/test-key.jpg", saved.getImageUrl());
        assertEquals("map/test-key.jpg", saved.getS3Key());
        assertNotNull(saved.getUserId());
    }

    @Test
    void uploadPostFailsWhenTitleIsBlank() throws Exception {
        String accessToken = signupAndLogin("writer02");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/map/posts/create")
                        .file(file)
                        .param("title", "   ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("제목은 필수입니다."));
    }

    @Test
    void uploadPostFailsWhenTitleExceedsColumnLimit() throws Exception {
        String accessToken = signupAndLogin("writer03");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/map/posts/create")
                        .file(file)
                        .param("title", "a".repeat(101))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("제목은 100자 이하여야 합니다."));
    }

    @Test
    void uploadPostFailsWhenDescriptionExceedsColumnLimit() throws Exception {
        String accessToken = signupAndLogin("writer04");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/map/posts/create")
                        .file(file)
                        .param("title", "정상 제목")
                        .param("description", "a".repeat(1001))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").value("부가 설명은 1000자 이하여야 합니다."));
    }

    private String signupAndLogin(String username) throws Exception {
        SignupRequest signupRequest = new SignupRequest(
                username,
                "tester",
                username + "@example.com",
                "password123"
        );

        mockMvc.perform(post("/auth/signup")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(username, "password123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }
}
