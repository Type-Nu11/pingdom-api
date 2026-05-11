package com.typenull.pingdom.domain.firebase.controller;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.firebase.enums.NotificationType;
import com.typenull.pingdom.domain.firebase.service.FcmService;
import com.typenull.pingdom.domain.firebase.dto.FcmTokenRequest;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping ("/firebase")
public class FcmController {

    private final FcmService fcmService;
    private final UserRepository userRepository;
    private final MapImageRepository mapImageRepository;

    @GetMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@AuthenticationPrincipal JwtAuthenticatedUser user
            , @RequestBody FcmTokenRequest request) {

        Long userId = user.userId();
        fcmService.updateFcmToken(userId, request.token());
        return ResponseEntity.ok().build();
    }

    // 맵 id로 오너 감별, userId로 좋아요 한 사람 감별
    @PostMapping("/like")
    public void likePlace(Long imageId, Long userId) throws FirebaseMessagingException {

        MapImage mapImage = mapImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("이미지 없음"));

        User owner = userRepository.findById(mapImage.getUserId())
                .orElseThrow(() -> new RuntimeException("유저 없음"));
        if (owner.getFcmToken() != null) {
            fcmService.sendNotification(
                    owner.getFcmToken(),
                    NotificationType.NEW_LIKE,
                    liker.getNickname()
            );
        }
    }
}
