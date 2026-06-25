package com.typenull.pingdom.moderation.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.moderation.api.dto.storage.AdminS3OrphanObjectReportResponse;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminS3ObjectReportServiceTest {

    @Mock
    private MapImageRepository mapImageRepository;

    @Mock
    private S3ObjectStorage s3ObjectStorage;

    private AdminS3ObjectReportService service;

    @BeforeEach
    void setUp() {
        service = new AdminS3ObjectReportService(mapImageRepository, s3ObjectStorage);
    }

    @Test
    void reportOrphanObjectsComparesS3ObjectsWithOriginalAndThumbnailKeys() {
        when(s3ObjectStorage.listKeys("map/", 100))
                .thenReturn(new S3ObjectStorage.S3ListResult(
                        List.of("map/used.jpg", "map/orphan.jpg", "map/thumbnails/used-thumb.jpg"),
                        false
                ));
        List<String> listedKeys = List.of("map/used.jpg", "map/orphan.jpg", "map/thumbnails/used-thumb.jpg");
        when(mapImageRepository.findUsedOriginalS3Keys(listedKeys)).thenReturn(List.of("map/used.jpg"));
        when(mapImageRepository.findUsedThumbnailS3Keys(listedKeys)).thenReturn(List.of("map/thumbnails/used-thumb.jpg"));
        when(mapImageRepository.countOriginalS3Keys()).thenReturn(1L);
        when(mapImageRepository.countThumbnailS3Keys()).thenReturn(1L);

        AdminS3OrphanObjectReportResponse response = service.reportOrphanObjects("map/", 100);

        assertEquals("map/", response.prefix());
        assertEquals(100, response.scanLimit());
        assertEquals(2, response.dbKeyCount());
        assertEquals(3, response.s3ObjectCount());
        assertEquals(1, response.orphanObjectCount());
        assertEquals(List.of("map/orphan.jpg"), response.orphanKeys());
    }
}
