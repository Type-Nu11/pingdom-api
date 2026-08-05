package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceInformationAccessPolicyTest {

    @Mock private MerchantPlaceMemberRepository memberRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private UserRepository userRepository;

    private MerchantPlaceInformationAccessPolicy accessPolicy;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);
        accessPolicy = new MerchantPlaceInformationAccessPolicy(
                memberRepository,
                ownerPlaceRepository,
                userRepository,
                clock
        );
        when(userRepository.findById(20L)).thenReturn(Optional.of(
                User.builder().id(20L).role(UserRole.USER).build()
        ));
        when(ownerPlaceRepository.existsById(10L)).thenReturn(true);
    }

    @Test
    void activeManagerCanManageInformation() {
        when(memberRepository.findByPlaceIdAndUserId(10L, 20L)).thenReturn(Optional.of(
                MerchantPlaceMember.builder()
                        .placeId(10L)
                        .userId(20L)
                        .role(MerchantPlaceMemberRole.MANAGER)
                        .status(MerchantPlaceMemberStatus.ACTIVE)
                        .build()
        ));

        accessPolicy.requireManager(20L, 10L);
    }

    @Test
    void staffCannotManageInformation() {
        when(memberRepository.findByPlaceIdAndUserId(10L, 20L)).thenReturn(Optional.of(
                MerchantPlaceMember.builder()
                        .placeId(10L)
                        .userId(20L)
                        .role(MerchantPlaceMemberRole.STAFF)
                        .status(MerchantPlaceMemberStatus.ACTIVE)
                        .build()
        ));

        assertThatThrownBy(() -> accessPolicy.requireManager(20L, 10L))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED));
    }
}
