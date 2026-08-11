package com.typenull.pingdom.identity;

import com.typenull.pingdom.identity.api.dto.travel.CurrentActivityIntentUpdateRequest;
import com.typenull.pingdom.identity.api.dto.travel.TravelScheduleCreateRequest;
import com.typenull.pingdom.identity.api.dto.travel.TravelScheduleUpdateRequest;
import com.typenull.pingdom.identity.application.service.retention.TravelDataRetentionProperties;
import com.typenull.pingdom.identity.application.service.retention.TravelDataRetentionService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class UserTravelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TravelScheduleRepository travelScheduleRepository;

    @Autowired
    private UserCurrentActivityIntentRepository currentActivityIntentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        currentActivityIntentRepository.deleteAll();
        travelScheduleRepository.deleteAll();
        userRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        currentActivityIntentRepository.deleteAll();
        travelScheduleRepository.deleteAll();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createsUpdatesCancelsAndListsOnlyMyTravelSchedules() throws Exception {
        User user = saveUser("travelScheduleOwner");
        LocalDate startDate = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        LocalDate endDate = startDate.plusDays(2);

        mockMvc.perform(post("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleCreateRequest(startDate, endDate))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.status").value("UPCOMING"));

        Long scheduleId = travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(user.getId())
                .getFirst()
                .getId();
        LocalDate updatedEndDate = endDate.plusDays(1);
        mockMvc.perform(patch("/users/me/travel-schedules/{scheduleId}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleUpdateRequest(startDate, updatedEndDate))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(updatedEndDate.toString()));

        mockMvc.perform(post("/users/me/travel-schedules/{scheduleId}/cancel", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(patch("/users/me/travel-schedules/{scheduleId}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleUpdateRequest(startDate, updatedEndDate))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRAVEL_SCHEDULE_NOT_EDITABLE"));

        mockMvc.perform(get("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules.length()").value(1))
                .andExpect(jsonPath("$.schedules[0].status").value("CANCELLED"));
    }

    @Test
    void rejectsInvalidPeriodAndHidesAnotherUsersSchedule() throws Exception {
        User owner = saveUser("travelScheduleOwner2");
        User otherUser = saveUser("travelScheduleOther");
        LocalDate startDate = LocalDate.now(ZoneOffset.UTC).plusDays(2);

        mockMvc.perform(post("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleCreateRequest(startDate, startDate.minusDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRAVEL_SCHEDULE_PERIOD"));

        mockMvc.perform(post("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleCreateRequest(startDate, startDate))))
                .andExpect(status().isCreated());
        Long scheduleId = travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(owner.getId())
                .getFirst()
                .getId();

        mockMvc.perform(patch("/users/me/travel-schedules/{scheduleId}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(otherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleUpdateRequest(startDate, startDate.plusDays(1)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_SCHEDULE_NOT_FOUND"));
    }

    @Test
    void rejectsPastAndOverlappingPeriodsWithDistinctErrorCodes() throws Exception {
        User user = saveUser("travelScheduleValidation");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstStart = today.plusDays(2);
        LocalDate firstEnd = firstStart.plusDays(2);

        mockMvc.perform(post("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleCreateRequest(
                                today.minusDays(1),
                                today.plusDays(1)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRAVEL_SCHEDULE_START_DATE_IN_PAST"));

        mockMvc.perform(post("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleCreateRequest(firstStart, firstEnd))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleCreateRequest(
                                firstStart.plusDays(1),
                                firstEnd.plusDays(1)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRAVEL_SCHEDULE_PERIOD_OVERLAP"));

        LocalDate secondStart = firstEnd.plusDays(2);
        LocalDate secondEnd = secondStart.plusDays(1);
        mockMvc.perform(post("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleCreateRequest(secondStart, secondEnd))))
                .andExpect(status().isCreated());
        Long secondScheduleId = travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(user.getId())
                .getLast()
                .getId();

        mockMvc.perform(patch("/users/me/travel-schedules/{scheduleId}", secondScheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleUpdateRequest(
                                firstStart.plusDays(1),
                                firstEnd.plusDays(1)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRAVEL_SCHEDULE_PERIOD_OVERLAP"));

        mockMvc.perform(patch("/users/me/travel-schedules/{scheduleId}", secondScheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleUpdateRequest(
                                today.minusDays(1),
                                today.plusDays(1)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRAVEL_SCHEDULE_START_DATE_IN_PAST"));
    }

    @Test
    void managesCurrentActivityIntentAndIncludesActiveTravelDataInExport() throws Exception {
        User user = saveUser("travelDataExport");
        LocalDate startDate = LocalDate.now(ZoneOffset.UTC).plusDays(1);

        mockMvc.perform(post("/users/me/travel-schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TravelScheduleCreateRequest(startDate, startDate.plusDays(2)))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/users/me/current-activity-intent")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CurrentActivityIntentUpdateRequest(CurrentActivityIntent.CAFE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CAFE"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        mockMvc.perform(get("/users/me/current-activity-intent")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CAFE"));

        mockMvc.perform(get("/users/me/export")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelSchedules.length()").value(1))
                .andExpect(jsonPath("$.currentActivityIntent.intent").value("CAFE"));

        mockMvc.perform(delete("/users/me/current-activity-intent")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isNoContent());

        assertThat(currentActivityIntentRepository.findByUser_Id(user.getId())).isEmpty();
    }

    @Test
    void travelEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/users/me/travel-schedules"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/me/current-activity-intent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retainsTravelDataForSevenDaysAfterWithdrawalThenDeletesIt() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 10, 0);
        User user = saveUser("travelDataRetention");
        TravelSchedule schedule = travelScheduleRepository.saveAndFlush(TravelSchedule.create(
                user,
                now.toLocalDate().minusDays(3),
                now.toLocalDate().minusDays(1)
        ));
        UserCurrentActivityIntent activityIntent = currentActivityIntentRepository.saveAndFlush(
                UserCurrentActivityIntent.create(user, CurrentActivityIntent.EXPLORE, now.plusHours(2))
        );
        user.withdraw(
                "withdrawn_user_" + user.getId(),
                "withdrawn_user_%d@withdrawn.local".formatted(user.getId()),
                "encoded-withdrawn-password",
                now.minusDays(7)
        );
        userRepository.saveAndFlush(user);

        TravelDataRetentionService retentionService = new TravelDataRetentionService(
                userRepository,
                travelScheduleRepository,
                currentActivityIntentRepository,
                new TravelDataRetentionProperties(Duration.ofDays(7), 100),
                Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC)
        );

        transactionTemplate.executeWithoutResult(status -> retentionService.purgeExpiredData());

        assertThat(travelScheduleRepository.findById(schedule.getId())).isEmpty();
        assertThat(currentActivityIntentRepository.findById(activityIntent.getId())).isEmpty();
    }

    @Test
    void deletesTravelDataBeyondTheFirstCleanupBatch() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 10, 0);
        User firstUser = saveUser("travelDataRetentionFirst");
        User secondUser = saveUser("travelDataRetentionSecond");
        TravelSchedule firstSchedule = travelScheduleRepository.saveAndFlush(TravelSchedule.create(
                firstUser,
                now.toLocalDate().minusDays(3),
                now.toLocalDate().minusDays(1)
        ));
        TravelSchedule secondSchedule = travelScheduleRepository.saveAndFlush(TravelSchedule.create(
                secondUser,
                now.toLocalDate().minusDays(3),
                now.toLocalDate().minusDays(1)
        ));
        withdrawUser(firstUser, now.minusDays(7));
        withdrawUser(secondUser, now.minusDays(7));

        TravelDataRetentionService retentionService = new TravelDataRetentionService(
                userRepository,
                travelScheduleRepository,
                currentActivityIntentRepository,
                new TravelDataRetentionProperties(Duration.ofDays(7), 1),
                Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC)
        );

        transactionTemplate.executeWithoutResult(status -> retentionService.purgeExpiredData());
        transactionTemplate.executeWithoutResult(status -> retentionService.purgeExpiredData());

        assertThat(travelScheduleRepository.findById(firstSchedule.getId())).isEmpty();
        assertThat(travelScheduleRepository.findById(secondSchedule.getId())).isEmpty();
    }

    private User saveUser(String username) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build());
    }

    private void withdrawUser(User user, LocalDateTime withdrawnAt) {
        user.withdraw(
                "withdrawn_user_" + user.getId(),
                "withdrawn_user_%d@withdrawn.local".formatted(user.getId()),
                "encoded-withdrawn-password",
                withdrawnAt
        );
        userRepository.saveAndFlush(user);
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
