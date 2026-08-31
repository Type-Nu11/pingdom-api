package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaceRegistrationMediaPromotionServiceTest {

    private final PlaceMediaRepository placeMediaRepository = org.mockito.Mockito.mock(PlaceMediaRepository.class);
    private final S3ObjectStorage storage = org.mockito.Mockito.mock(S3ObjectStorage.class);
    private final PlaceRegistrationMediaPromotionService service = new PlaceRegistrationMediaPromotionService(
            placeMediaRepository,
            storage,
            Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void promotesOnlyActiveRepresentativeImagesInDisplayOrderAndSetsCanonicalImage() {
        MapPlace place = org.mockito.Mockito.mock(MapPlace.class);
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        PlaceRegistrationAttachment second = attachment(12L, 1, "second.webp", "image/webp");
        PlaceRegistrationAttachment first = attachment(11L, 0, "first.jpg", "image/jpeg");
        PlaceRegistrationAttachment sensitiveDocument = org.mockito.Mockito.mock(PlaceRegistrationAttachment.class);
        when(place.getId()).thenReturn(70069L);
        when(place.getImageUrl()).thenReturn(null, "https://cdn.pingdom.test/places/70069/exploration/7/registration/11.jpg");
        when(place.getDescription()).thenReturn(null);
        when(application.getApplicantUserId()).thenReturn(7L);
        when(application.getDescription()).thenReturn("신청 장소 설명");
        when(application.getAttachments()).thenReturn(List.of(second, sensitiveDocument, first));
        when(sensitiveDocument.isActive()).thenReturn(true);
        when(sensitiveDocument.getDocumentType()).thenReturn(PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT);
        when(placeMediaRepository.findBySourceRegistrationAttachmentId(11L)).thenReturn(Optional.empty());
        when(placeMediaRepository.findBySourceRegistrationAttachmentId(12L)).thenReturn(Optional.empty());
        when(placeMediaRepository.save(any(PlaceMedia.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.publicUrl(any())).thenAnswer(invocation -> "https://cdn.pingdom.test/" + invocation.getArgument(0));

        var result = service.promote(place, application);

        ArgumentCaptor<PlaceMedia> mediaCaptor = ArgumentCaptor.forClass(PlaceMedia.class);
        verify(placeMediaRepository, org.mockito.Mockito.times(2)).save(mediaCaptor.capture());
        assertThat(mediaCaptor.getAllValues())
                .extracting(PlaceMedia::getSourceRegistrationAttachmentId)
                .containsExactly(11L, 12L);
        assertThat(mediaCaptor.getAllValues())
                .extracting(PlaceMedia::getDisplayOrder)
                .containsExactly(0, 1);
        verify(storage).copy(
                eq("private/merchant-place-applications/77/representative_image/first.jpg"),
                eq("places/70069/exploration/7/registration/11.jpg")
        );
        verify(place).updateImageUrl("https://cdn.pingdom.test/places/70069/exploration/7/registration/11.jpg");
        verify(place).updateDescription("신청 장소 설명");
        assertThat(result.promotedCount()).isEqualTo(2);
        assertThat(result.alreadyPromotedCount()).isZero();
    }

    @Test
    void skipsAlreadyPromotedAttachmentToMakeBackfillIdempotent() {
        MapPlace place = org.mockito.Mockito.mock(MapPlace.class);
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        PlaceRegistrationAttachment attachment = attachment(11L, 0, "first.jpg", "image/jpeg");
        when(application.getAttachments()).thenReturn(List.of(attachment));
        when(placeMediaRepository.findBySourceRegistrationAttachmentId(11L))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(PlaceMedia.class)));

        var result = service.promote(place, application);

        verify(storage, org.mockito.Mockito.never()).copy(any(), any());
        verify(placeMediaRepository, org.mockito.Mockito.never()).save(any());
        assertThat(result.promotedCount()).isZero();
        assertThat(result.alreadyPromotedCount()).isOne();
    }

    private PlaceRegistrationAttachment attachment(Long id, int displayOrder, String filename, String contentType) {
        PlaceRegistrationAttachment attachment = org.mockito.Mockito.mock(PlaceRegistrationAttachment.class);
        when(attachment.isActive()).thenReturn(true);
        when(attachment.getId()).thenReturn(id);
        when(attachment.getDocumentType()).thenReturn(PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE);
        when(attachment.getDisplayOrder()).thenReturn(displayOrder);
        when(attachment.getStorageKey()).thenReturn("private/merchant-place-applications/77/representative_image/" + filename);
        when(attachment.getOriginalFilename()).thenReturn(filename);
        when(attachment.getContentType()).thenReturn(contentType);
        return attachment;
    }
}
