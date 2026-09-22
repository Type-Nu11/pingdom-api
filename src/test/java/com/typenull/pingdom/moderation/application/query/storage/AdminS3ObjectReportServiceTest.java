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

    /**
     * 공통 S3 고아 객체 보고 서비스를 대체해 관리자 응답 변환만 검증.
     */
    @BeforeEach
    void setUp() {
        service = new AdminS3ObjectReportService(mapImageS3OrphanReportService);
    }

    /**
     * 공통 dry-run 보고서의 prefix·스캔 한도·DB 키 수·S3 객체 수·고아 수·고아 키 목록이 관리자 응답에 유지되는지 검증.
     */
    @Test
    void mapsOrphanObjectReport() {
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
