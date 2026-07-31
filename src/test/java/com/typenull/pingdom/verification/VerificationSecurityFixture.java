package com.typenull.pingdom.verification;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.verification.domain.LocationCheckIn;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import javax.imageio.ImageIO;

public final class VerificationSecurityFixture {
    public static final double PLACE_LATITUDE = 37.5665;
    public static final double PLACE_LONGITUDE = 126.9780;

    private VerificationSecurityFixture() {
    }

    public static User user(String username, UserRole role) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build();
    }

    public static MapPlace place(Long registrantId) {
        return MapPlace.builder()
                .name("서울 시청")
                .address("서울특별시 중구 세종대로 110")
                .latitude(PLACE_LATITUDE)
                .longitude(PLACE_LONGITUDE)
                .userId(registrantId)
                .registrant("verification-fixture")
                .build();
    }

    public static LocationCheckIn checkIn(Long userId, Long placeId, Instant now) {
        return LocationCheckIn.proximityMatched(
                userId,
                placeId,
                LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Seoul")),
                now.minusSeconds(10),
                now,
                3.5
        );
    }

    public static byte[] jpegBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", output);
        return output.toByteArray();
    }
}
