package com.typenull.pingdom.moderation.application.query.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.moderation.api.dto.storage.AdminS3OrphanObjectReportResponse;
import com.typenull.pingdom.post.infrastructure.storage.MapImageS3OrphanReportService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminS3ObjectReportServiceTest {

    @Mock
    private MapImageS3OrphanReportService mapImageS3OrphanReportService;

    private AdminS3ObjectReportService service;

    @BeforeEach
    void setUp() {
        service = new AdminS3ObjectReportService(mapImageS3OrphanReportService);
    }

    @Test
    void reportOrphanObjectsMapsSharedReportResultToExistingResponse() {
        when(mapImageS3OrphanReportService.reportOrphanObjects("map/", 100))
                .thenReturn(new MapImageS3OrphanReportService.S3OrphanDryRunReport(
                        "map/",
                        100,
                        false,
                        2,
                        3,
                        1,
                        List.of("map/orphan.jpg")
                ));

        AdminS3OrphanObjectReportResponse response = service.reportOrphanObjects("map/", 100);

        assertEquals("map/", response.prefix());
        assertEquals(100, response.scanLimit());
        assertEquals(2, response.dbKeyCount());
        assertEquals(3, response.s3ObjectCount());
        assertEquals(1, response.orphanObjectCount());
        assertEquals(List.of("map/orphan.jpg"), response.orphanKeys());
    }
}
