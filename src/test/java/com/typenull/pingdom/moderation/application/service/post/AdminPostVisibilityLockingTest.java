package com.typenull.pingdom.moderation.application.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.application.service.MapImageStateLockService;
import com.typenull.pingdom.post.application.service.MapImageStateLockService.LockedImage;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPostVisibilityLockingTest {

    @Mock private MapImageRepository images;
    @Mock private PostReportRepository reports;
    @Mock private MapPlaceRepository places;
    @Mock private MapImageStateLockService imageLocks;
    @Mock private S3ObjectDeleteOutboxPublisher s3;
    @Mock private AdminAuditLogService audit;
    @Mock private AdminRoleAuthorizationService authorization;

    @Test
    void repeatedHideAndRestoreCountOnlyActualTransitionsOnLockedPlace() {
        PlaceGrowthService growth = spy(new PlaceGrowthService(places));
        AdminPostServiceImpl service = new AdminPostServiceImpl(images, reports, growth,
                imageLocks, s3, audit, authorization, Clock.systemUTC());
        MapPlace place = MapPlace.builder().id(10L).photoCount(2L).build();
        MapImage image = MapImage.builder().id(3L).mapPlace(place).build();
        when(imageLocks.findForUpdate(3L)).thenReturn(Optional.of(new LockedImage(image, place)));

        service.hidePost(3L, "hide", 99L);
        service.hidePost(3L, "hide again", 99L);
        assertThat(place.currentPhotoCount()).isEqualTo(1L);
        service.restorePost(3L, "restore", 99L);
        service.restorePost(3L, "restore again", 99L);

        assertThat(place.currentPhotoCount()).isEqualTo(2L);
        assertThat(image.isVisible()).isTrue();
        verify(imageLocks, times(4)).findForUpdate(3L);
        verify(growth).decreasePhotoCount(place);
        verify(growth).increasePhotoCount(place);
        verify(growth, never()).decreasePhotoCount(10L);
        verify(growth, never()).increasePhotoCount(10L);
        verifyNoInteractions(places, images, reports, s3);
    }
}
