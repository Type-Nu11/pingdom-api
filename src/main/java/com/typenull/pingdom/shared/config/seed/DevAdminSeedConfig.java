package com.typenull.pingdom.shared.config.seed;

import com.typenull.pingdom.identity.domain.TravelPurpose;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * dev·local 프로필에서 관리자와 API 확인용 사용자·장소·게시글을 별도 트랜잭션으로 시드한다.
 * 존재하는 항목은 덮어쓰지 않으며 관리자는 명시적 비밀번호 설정이 필요하다.
 */
@Configuration
@Profile({"dev", "local"})
@Slf4j
public class DevAdminSeedConfig {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    @Value("${seed.admin.enabled:true}")
    private boolean adminSeedEnabled;

    @Value("${seed.admin.username:admin}")
    private String adminUsername;

    @Value("${seed.admin.email:admin@pingdom.local}")
    private String adminEmail;

    @Value("${seed.admin.password:}")
    private String adminPassword;

    @Value("${seed.dev-data.enabled:true}")
    private boolean devDataSeedEnabled;

    @Value("${seed.dev-data.user-password:user1234!}")
    private String devUserPassword;

    /** 관리자 시드가 활성일 때 필수 설정을 검증하고 username 또는 email이 이미 있으면 생성을 생략한다. */
    @Bean
    public ApplicationRunner devAdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        return args -> {
            if (!adminSeedEnabled) {
                log.info("Dev admin seed 스킵: seed.admin.enabled=false");
                return;
            }

            transactionTemplate.executeWithoutResult(status -> {
                validateAdminConfig();

                if (userRepository.existsByUsername(adminUsername)) {
                    log.info("Dev admin seed 스킵: 이미 존재하는 username 입니다. username={}", adminUsername);
                    return;
                }
                if (userRepository.existsByEmail(adminEmail)) {
                    log.info("Dev admin seed 스킵: 이미 존재하는 email 입니다. email={}", adminEmail);
                    return;
                }

                User admin = User.builder()
                        .username(adminUsername)
                        .email(adminEmail)
                        .emailVerified(true)
                        .password(passwordEncoder.encode(adminPassword))
                        .birthYear(2000)
                        .language("ko")
                        .country("KR")
                        .role(UserRole.ADMIN)
                        .build();

                userRepository.save(admin);
                log.info("Dev admin user seeded. username={}", adminUsername);
            });
        };
    }

    /** 고정 개발 데이터를 하나의 트랜잭션에 생성한다. 이미지 URL과 S3 key는 샘플 문자열이며 실제 객체를 업로드하지 않는다. */
    @Bean
    public ApplicationRunner devDataSeeder(
            UserRepository userRepository,
            MapPlaceRepository mapPlaceRepository,
            MapImageRepository mapImageRepository,
            MapBookmarkRepository mapBookmarkRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        return args -> {
            if (!devDataSeedEnabled) {
                log.info("Dev data seed 스킵: seed.dev-data.enabled=false");
                return;
            }

            transactionTemplate.executeWithoutResult(status -> {
                if (!StringUtils.hasText(devUserPassword)) {
                    log.warn("Dev data seed 스킵: seed.dev-data.user-password 값이 비어있습니다.");
                    return;
                }

                User tourist01 = seedUser(userRepository, passwordEncoder, "tourist01", "tourist01@local", "KR", UserRole.USER, Set.of(TravelPurpose.K_POP, TravelPurpose.CAFE));
                User tourist02 = seedUser(userRepository, passwordEncoder, "tourist02", "tourist02@local", "JP", UserRole.USER, Set.of(TravelPurpose.BEAUTY, TravelPurpose.FASHION));
                User merchant01 = seedUser(userRepository, passwordEncoder, "merchant01", "merchant01@local", "KR", UserRole.MERCHANT_OWNER, Set.of(TravelPurpose.FOOD, TravelPurpose.POP_UP));

                List<MapPlace> places = List.of(
                        seedPlace(mapPlaceRepository, "dev-seed-place-001", "핑덤 성수 팝업", "서울 성동구 연무장길 17", "서울 성동구 연무장길 17", "서울 성동구 성수동2가 314-5", "04782", "팝업스토어", "Pingdom Seongsu Pop-up", "성수동 팝업과 전시 테스트에 사용하는 개발용 장소입니다.", 37.544579, 127.055978, merchant01, Set.of(TouristCategory.POP_UP, TouristCategory.FASHION)),
                        seedPlace(mapPlaceRepository, "dev-seed-place-002", "핑덤 한남 카페", "서울 용산구 이태원로54길 58", "서울 용산구 이태원로54길 58", "서울 용산구 한남동 683-134", "04400", "카페", "Pingdom Hannam Cafe", "카페 추천과 북마크 테스트에 사용하는 개발용 장소입니다.", 37.536259, 127.000245, tourist01, Set.of(TouristCategory.CAFE, TouristCategory.BEAUTY)),
                        seedPlace(mapPlaceRepository, "dev-seed-place-003", "핑덤 홍대 라이브홀", "서울 마포구 와우산로21길 19-3", "서울 마포구 와우산로21길 19-3", "서울 마포구 서교동 364-22", "04041", "공연장", "Pingdom Hongdae Live Hall", "K-pop, nightlife 추천 테스트에 사용하는 개발용 장소입니다.", 37.552145, 126.922764, tourist02, Set.of(TouristCategory.K_POP, TouristCategory.NIGHTLIFE))
                );

                seedImage(mapImageRepository, mapPlaceRepository, places.get(0), tourist01, "성수 팝업 방문", 12L);
                seedImage(mapImageRepository, mapPlaceRepository, places.get(1), tourist02, "한남 카페 라떼", 7L);
                seedImage(mapImageRepository, mapPlaceRepository, places.get(2), tourist01, "홍대 라이브 공연", 19L);

                seedBookmark(mapBookmarkRepository, tourist01, places.get(1));
                seedBookmark(mapBookmarkRepository, tourist01, places.get(2));
                seedBookmark(mapBookmarkRepository, tourist02, places.get(0));

                log.info("Dev data seeded. users=[tourist01,tourist02,merchant01], places={}", places.size());
            });
        };
    }

    private void validateAdminConfig() {
        if (!StringUtils.hasText(adminUsername) || !StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            throw new IllegalArgumentException("Dev admin seed 필수 설정(username, email, password)이 비어있습니다.");
        }
    }

    /** username이 같은 사용자를 재사용하고 없는 경우에만 비밀번호를 인코딩해 새 계정을 저장한다. */
    private User seedUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String username,
            String email,
            String country,
            UserRole role,
            Set<TravelPurpose> travelPurposes
    ) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(username)
                        .email(email)
                        .emailVerified(true)
                        .password(passwordEncoder.encode(devUserPassword))
                        .birthYear(2000)
                        .language("ko")
                        .country(country)
                        .role(role)
                        .travelPurposes(travelPurposes)
                        .build()));
    }

    /** 고정 Kakao 식별자로 중복 생성을 피한다. WGS84 공간 좌표는 경도·위도 순서로 구성한다. */
    private MapPlace seedPlace(
            MapPlaceRepository mapPlaceRepository,
            String kakaoPlaceId,
            String name,
            String address,
            String roadAddress,
            String jibunAddress,
            String postalCode,
            String category,
            String englishName,
            String touristSummary,
            Double latitude,
            Double longitude,
            User user,
            Set<TouristCategory> touristCategories
    ) {
        return mapPlaceRepository.findByKakaoPlaceId(kakaoPlaceId)
                .orElseGet(() -> mapPlaceRepository.save(MapPlace.builder()
                        .name(name)
                        .address(address)
                        .roadAddress(roadAddress)
                        .jibunAddress(jibunAddress)
                        .postalCode(postalCode)
                        .geocodingSource(GeocodingSource.ADMIN)
                        .operatingStatus(PlaceOperatingStatus.OPERATING)
                        .category(category)
                        .englishName(englishName)
                        .touristSummary(touristSummary)
                        .touristCategories(new LinkedHashSet<>(touristCategories))
                        .imageUrl("https://cdn.pingdom.local/dev/" + kakaoPlaceId + ".jpg")
                        .kakaoPlaceId(kakaoPlaceId)
                        .latitude(latitude)
                        .longitude(longitude)
                        .location(WGS84.createPoint(new Coordinate(longitude, latitude)))
                        .userId(user.getId())
                        .registrant(user.getUsername())
                        .build()));
    }

    /** 사용자·장소 게시글이 없을 때만 생성하고 장소 사진 수를 함께 증가시켜 재시드 중복 집계를 피한다. */
    private void seedImage(
            MapImageRepository mapImageRepository,
            MapPlaceRepository mapPlaceRepository,
            MapPlace place,
            User user,
            String title,
            Long likeCount
    ) {
        if (mapImageRepository.existsByUserIdAndMapPlace_Id(user.getId(), place.getId())) {
            return;
        }

        MapImage image = MapImage.builder()
                .imageUrl("https://cdn.pingdom.local/dev/images/" + place.getKakaoPlaceId() + ".jpg")
                .s3Key("dev/images/" + place.getKakaoPlaceId() + ".jpg")
                .thumbnailUrl("https://cdn.pingdom.local/dev/thumbnails/" + place.getKakaoPlaceId() + ".jpg")
                .thumbnailS3Key("dev/thumbnails/" + place.getKakaoPlaceId() + ".jpg")
                .title(title)
                .description("dev 프로필에서 API 확인에 사용하는 seed 게시글입니다.")
                .userId(user.getId())
                .username(user.getUsername())
                .likeCount(likeCount)
                .mapPlace(place)
                .build();

        mapImageRepository.save(image);
        place.increasePhotoCount();
        mapPlaceRepository.save(place);
    }

    private void seedBookmark(
            MapBookmarkRepository mapBookmarkRepository,
            User user,
            MapPlace place
    ) {
        if (!mapBookmarkRepository.existsByUserIdAndPlaceId(user.getId(), place.getId())) {
            mapBookmarkRepository.save(MapBookmark.builder()
                    .userId(user.getId())
                    .placeId(place.getId())
                    .build());
        }
    }
}
