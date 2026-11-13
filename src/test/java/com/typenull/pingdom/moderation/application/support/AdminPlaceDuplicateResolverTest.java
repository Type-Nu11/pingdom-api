package com.typenull.pingdom.moderation.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminPlaceDuplicateResolverTest {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final AdminPlaceDuplicateResolver resolver = new AdminPlaceDuplicateResolver();

    @Test
    void excludesEmptyInputIsolatedPlacesAndRepeatedIds() {
        MapPlace isolated = place(1L, "카페", "서울", null, 0);

        assertThat(resolver.countGroups(List.of())).isZero();
        assertThat(resolver.countGroups(List.of(isolated))).isZero();
        assertThat(resolver.countGroups(List.of(isolated, isolated))).isZero();
        assertThat(resolver.countGroups(List.of(isolated,
                place(2L, "다른 카페", "서울", null, 0)))).isZero();
    }

    @Test
    void normalizesNameAddressAndIgnoresBlankKakaoIds() {
        List<MapPlace> places = List.of(
                place(1L, " C a F e ", " 서울 시 ", " ", 0),
                place(2L, "cafe", "서울시", null, 49),
                place(3L, "다른 장소", "부산", "\t", 49)
        );

        assertThat(resolver.countGroups(places)).isEqualTo(1);
        assertMatchesAnalysis(places);
    }

    @Test
    void connectsMatchingKakaoIdsAcrossNamesAndDistances() {
        List<MapPlace> places = List.of(
                place(1L, "카페", "서울", " kakao-1 ", 0),
                place(2L, "식당", "부산", "kakao-1", 100_000),
                place(3L, "상점", "대구", "KAKAO-1", 200_000)
        );

        assertThat(resolver.countGroups(places)).isEqualTo(1);
        assertMatchesAnalysis(places);
    }

    @Test
    void countsAnIndirectDistanceChainAsOneGroup() {
        List<MapPlace> places = List.of(
                place(1L, "카페", "서울", null, 0),
                place(2L, "카페", "서울", null, 40),
                place(3L, "카페", "서울", null, 80)
        );

        assertThat(resolver.areDuplicates(places.get(0), places.get(2))).isFalse();
        assertThat(resolver.countGroups(places)).isEqualTo(1);
        assertMatchesAnalysis(places);
    }

    @Test
    void joinsMixedKakaoAndNameAddressConnectionsGlobally() {
        List<MapPlace> places = mixedGroups();

        assertThat(resolver.countGroups(places)).isEqualTo(2);
        assertMatchesAnalysis(places);
    }

    @ParameterizedTest
    @CsvSource({"49.999, 1", "50.001, 0"})
    void preservesTheDistanceThreshold(double meters, long expectedGroups) {
        List<MapPlace> places = List.of(
                place(1L, "카페", "서울", null, 0),
                place(2L, "카페", "서울", null, meters)
        );

        assertThat(resolver.countGroups(places)).isEqualTo(expectedGroups);
        assertMatchesAnalysis(places);
    }

    @Test
    void preservesExistingFiftyMeterFloatingPointCalculation() {
        List<MapPlace> places = List.of(
                place(1L, "카페", "서울", null, 0),
                place(2L, "카페", "서울", null, 50)
        );

        assertMatchesAnalysis(places);
        assertThat(resolver.countGroups(places) == 1)
                .isEqualTo(resolver.areDuplicates(places.get(0), places.get(1)));
    }

    @Test
    void ignoresInputOrderAndKeepsDetailedCandidatesUnchanged() {
        List<MapPlace> places = mixedGroups();
        var before = resolver.analyze(places);
        Random random = new Random(1875);

        for (int iteration = 0; iteration < 10; iteration++) {
            List<MapPlace> shuffled = new ArrayList<>(places);
            Collections.shuffle(shuffled, random);
            assertThat(resolver.countGroups(shuffled)).isEqualTo(before.groups().size());
        }

        assertThat(resolver.analyze(places)).isEqualTo(before);
        assertThat(before.groups().get(0).memberPlaceIds()).containsExactly(1L, 2L, 3L);
        assertThat(before.candidatesOf(2L)).extracting(AdminPlaceDuplicateResolver.DuplicateCandidate::placeId)
                .containsExactly(1L, 3L);
        assertThat(before.candidatesOf(2L)).extracting(AdminPlaceDuplicateResolver.DuplicateCandidate::reason)
                .containsExactly("KAKAO_PLACE_ID", "NAME_ADDRESS_COORDINATE");
        assertThat(resolver.areDuplicates(places.get(0), places.get(1))).isTrue();
        assertThat(resolver.areDuplicates(places.get(0), places.get(2))).isFalse();
    }

    @Test
    void matchesExistingAnalysisForDeterministicMixedData() {
        Random random = new Random(1875);

        for (int sample = 0; sample < 40; sample++) {
            List<MapPlace> places = new ArrayList<>();
            int size = random.nextInt(25);
            for (long id = 1; id <= size; id++) {
                String name = switch (random.nextInt(5)) {
                    case 0 -> null;
                    case 1 -> " C A F E ";
                    default -> "cafe" + random.nextInt(3);
                };
                String address = random.nextInt(4) == 0 ? null : "주소" + random.nextInt(3);
                String kakaoId = random.nextInt(3) == 0 ? " key-" + random.nextInt(4) + " " : null;
                places.add(place(id, name, address, kakaoId, random.nextInt(6) * 40));
            }

            assertMatchesAnalysis(places);
            Collections.shuffle(places, random);
            assertMatchesAnalysis(places);
        }
    }

    @Test
    void comparesDistancesOnlyWithinIndependentNameAddressGroups() {
        int groupCount = 50;
        List<MapPlace> places = new ArrayList<>();
        for (int group = 0; group < groupCount; group++) {
            places.add(spy(place(group * 2L + 1, "카페" + group, "주소" + group, null, 0)));
            places.add(spy(place(group * 2L + 2, "카페" + group, "주소" + group, null, 10)));
        }

        assertThat(resolver.analyze(places).groups()).hasSize(groupCount);
        long fullAnalysisDistanceReads = latitudeReads(places);
        places.forEach(place -> clearInvocations(place));

        assertThat(resolver.countGroups(places)).isEqualTo(groupCount);
        long countDistanceReads = latitudeReads(places);

        // 기존 쌍별 거리 계산은 두 장소의 위도를 읽으며, 집계는 같은 키의 50쌍만 읽는다.
        assertThat(fullAnalysisDistanceReads / 2).isEqualTo(4_950);
        assertThat(countDistanceReads / 2).isEqualTo(50);
    }

    private void assertMatchesAnalysis(List<MapPlace> places) {
        assertThat(resolver.countGroups(places)).isEqualTo(resolver.analyze(places).groups().size());
    }

    private long latitudeReads(List<MapPlace> places) {
        return places.stream().flatMap(place -> mockingDetails(place).getInvocations().stream())
                .filter(invocation -> invocation.getMethod().getName().equals("getLatitude"))
                .count();
    }

    private List<MapPlace> mixedGroups() {
        return List.of(
                place(1L, "Alpha", "서울", "same-kakao", 10_000),
                place(2L, "Beta", "부산", "same-kakao", 0),
                place(3L, " B e t a ", " 부 산 ", null, 40),
                place(4L, "Gamma", "대구", null, 0),
                place(5L, "Gamma", "대구", null, 10),
                place(6L, "고립", "제주", null, 0)
        );
    }

    private MapPlace place(long id, String name, String address, String kakaoId, double northMeters) {
        return MapPlace.builder()
                .id(id)
                .name(name)
                .address(address)
                .kakaoPlaceId(kakaoId)
                .latitude(Math.toDegrees(northMeters / EARTH_RADIUS_METERS))
                .longitude(0d)
                .build();
    }
}
