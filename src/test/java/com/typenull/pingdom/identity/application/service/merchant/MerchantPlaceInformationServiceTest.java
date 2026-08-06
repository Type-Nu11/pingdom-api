package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceInformationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceInformationUpdateRequest;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInformationRepository;
import com.typenull.pingdom.identity.event.MerchantPlaceInformationUpdatedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceInformationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    @Mock private MerchantPlaceInformationRepository informationRepository;
    @Mock private MerchantPlaceInformationAccessPolicy accessPolicy;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private Clock clock;

    @InjectMocks private MerchantPlaceInformationService informationService;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-08-05T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void managerCanCreateInformationAndEventMarksCreation() {
        MerchantPlaceInformationUpdateRequest request = new MerchantPlaceInformationUpdateRequest(
                "K-컬처 체험 공간",
                "010-1234-5678",
                "https://example.com/place",
                "https://example.com/reserve"
        );
        when(informationRepository.findByPlaceIdForUpdate(10L)).thenReturn(Optional.empty());
        when(informationRepository.save(any(MerchantPlaceInformation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantPlaceInformationResponse response = informationService.upsert(20L, 10L, request);

        verify(accessPolicy).requireManager(20L, 10L);
        assertThat(response.placeId()).isEqualTo(10L);
        assertThat(response.description()).isEqualTo("K-컬처 체험 공간");
        ArgumentCaptor<MerchantPlaceInformationUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(MerchantPlaceInformationUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().created()).isTrue();
    }

    @Test
    void managerCanUpdateExistingInformation() {
        MerchantPlaceInformation information = MerchantPlaceInformation.create(
                10L,
                "기존 소개",
                null,
                null,
                null,
                20L,
                NOW.minusDays(1)
        );
        when(informationRepository.findByPlaceIdForUpdate(10L)).thenReturn(Optional.of(information));
        when(informationRepository.save(any(MerchantPlaceInformation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantPlaceInformationResponse response = informationService.upsert(
                20L,
                10L,
                new MerchantPlaceInformationUpdateRequest(
                        "변경된 소개", null, null, "https://example.com/reserve"
                )
        );

        assertThat(response.description()).isEqualTo("변경된 소개");
        assertThat(response.reservationUrl()).isEqualTo("https://example.com/reserve");
        verify(eventPublisher).publishEvent(new MerchantPlaceInformationUpdatedEvent(20L, 10L, false, NOW));
    }

    @Test
    void missingInformationIsReportedAfterPermissionCheck() {
        when(informationRepository.findByPlaceId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> informationService.get(20L, 10L))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(MerchantOwnerErrorCode.PLACE_INFORMATION_NOT_FOUND));

        verify(accessPolicy).requireManager(20L, 10L);
    }
}
