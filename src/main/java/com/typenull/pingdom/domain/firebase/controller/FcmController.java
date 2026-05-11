package com.typenull.pingdom.domain.firebase.controller;

import com.typenull.pingdom.domain.firebase.dto.FcmImageIdRequest;
import com.typenull.pingdom.domain.firebase.service.FcmService;
import com.typenull.pingdom.domain.firebase.dto.FcmTokenRequest;
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

    @PatchMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@AuthenticationPrincipal JwtAuthenticatedUser user,
                                               @RequestBody FcmTokenRequest request) {
        fcmService.updateFcmToken(user.userId(), request.token());
        return ResponseEntity.ok().build();
    }

    // 맵 id로 오너 감별, userId로 좋아요 한 사람 감별
    @PostMapping("/like")
    public ResponseEntity<Void> likePlace(FcmImageIdRequest request, @AuthenticationPrincipal JwtAuthenticatedUser user)  {
        fcmService.likePlace(request.ImageId(), user.userId());
        return ResponseEntity.ok().build();
    }
}
