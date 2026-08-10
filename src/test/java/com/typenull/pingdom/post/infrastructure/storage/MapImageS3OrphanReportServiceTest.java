package com.typenull.pingdom.post.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class MapImageS3OrphanReportServiceTest {

    @Mock
    private S3ObjectStorage s3ObjectStorage;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private MapImageRepository mapImageRepository;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private MapImageS3OrphanReportService service;

    @BeforeEach
    void setUp() {
        service = new MapImageS3OrphanReportService(s3ObjectStorage, redisTemplate, mapImageRepository);
    }

    @Test
    void reportOrphanObjectsComparesOriginalAndThumbnailKeysWithSharedCriteria() {
        List<String> listedKeys = List.of("map/used.jpg", "map/orphan.jpg", "map/thumbnails/used-thumb.jpg");
        when(s3ObjectStorage.listKeys("map/", 100)).thenReturn(new S3ObjectStorage.S3ListResult(listedKeys, false));
        when(mapImageRepository.findUsedOriginalS3Keys(listedKeys)).thenReturn(List.of("map/used.jpg"));
        when(mapImageRepository.findUsedThumbnailS3Keys(listedKeys))
                .thenReturn(List.of("map/thumbnails/used-thumb.jpg"));
        when(mapImageRepository.countOriginalS3Keys()).thenReturn(1L);
        when(mapImageRepository.countThumbnailS3Keys()).thenReturn(1L);

        MapImageS3OrphanReportService.S3OrphanDryRunReport report = service.reportOrphanObjects("map/", 100);

        assertEquals("map/", report.prefix());
        assertEquals(2, report.dbKeyCount());
        assertEquals(3, report.s3ObjectCount());
        assertEquals(1, report.orphanObjectCount());
        assertEquals(List.of("map/orphan.jpg"), report.orphanKeys());
    }

    @Test
    void getMapImageS3OrphanReportReadsCachedReportPage() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(hashOperations.get(any(), any())).thenAnswer(invocation -> switch (String.valueOf((Object) invocation.getArgument(1))) {
            case "status" -> "COMPLETED";
            case "generatedAt" -> "2026-06-25T21:00:00";
            case "dbKeyCount" -> "2";
            case "s3KeyCount" -> "4";
            case "deleteCandidateCount" -> "3";
            default -> null;
        });
        when(listOperations.range(any(), eq(0L), eq(1L)))
                .thenReturn(List.of("map/orphan-1.jpg", "map/orphan-2.jpg"));

        MapImageS3OrphanReportService.S3OrphanReport report = service.getMapImageS3OrphanReport("report-1", 1, 2);

        assertEquals(2, report.dbKeyCount());
        assertEquals(4, report.s3KeyCount());
        assertEquals(3, report.deleteCandidateCount());
        assertEquals(2, report.totalPages());
        assertEquals(true, report.hasNext());
        assertEquals("map/orphan-1.jpg", report.deleteCandidates().getFirst().key());
    }

    @Test
    void deleteMapImageS3CandidatesRechecksDatabaseUsageBeforeDeleting() {
        stubCompletedReport();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(any(), eq("map/orphan.jpg"))).thenReturn(true);
        when(setOperations.isMember(any(), eq("map/active.jpg"))).thenReturn(true);
        List<String> candidateKeys = List.of("map/orphan.jpg", "map/active.jpg");
        when(mapImageRepository.findUsedOriginalS3Keys(candidateKeys)).thenReturn(List.of("map/active.jpg"));
        when(mapImageRepository.findUsedThumbnailS3Keys(candidateKeys)).thenReturn(List.of());

        MapImageS3OrphanReportService.S3OrphanDeleteResult result =
                service.deleteMapImageS3Candidates("report-1", candidateKeys);

        assertEquals(2, result.requestedKeyCount());
        assertEquals(List.of("map/orphan.jpg"), result.deletedKeys());
        assertEquals(1, result.failedKeyCount());
        assertEquals("map/active.jpg", result.failedKeys().getFirst().key());
        assertEquals("DB(MapImage)에서 사용 중인 S3 객체입니다.", result.failedKeys().getFirst().reason());
        verify(s3ObjectStorage).delete("map/orphan.jpg");
        verify(s3ObjectStorage, never()).delete("map/active.jpg");
    }

    @Test
    void deleteMapImageS3CandidatesRejectsKeysMissingFromReportCandidates() {
        stubCompletedReport();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(any(), eq("map/expired.jpg"))).thenReturn(false);

        MapImageS3OrphanReportService.S3OrphanDeleteResult result =
                service.deleteMapImageS3Candidates("report-1", List.of("map/expired.jpg"));

        assertEquals(1, result.requestedKeyCount());
        assertEquals(0, result.deletedKeyCount());
        assertEquals(1, result.failedKeyCount());
        assertEquals("리포트 삭제 후보에 없는 S3 객체입니다.", result.failedKeys().getFirst().reason());
        verify(s3ObjectStorage, never()).delete(any());
        verify(mapImageRepository, never()).findUsedOriginalS3Keys(any());
    }

    private void stubCompletedReport() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(any(), eq("status"))).thenReturn("COMPLETED");
    }
}
