package com.typenull.pingdom.users;

import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.auth.security.JwtTokenProvider;
import com.typenull.pingdom.domain.users.dto.ChangePasswordRequest;
import com.typenull.pingdom.domain.users.service.ChangeInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeInfoServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ChangeInfoService changeInfoService;

    @Test
     void 비밀번호_변경_성공() {
        String token = "token";
        Long userId = 1L;

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("1234");
        request.setNewPassword("abcd");
        request.setConfirmPassword("abcd");

        User user = mock(User.class);

        when(jwtTokenProvider.getUserIdFromAccessToken(token)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("abcd")).thenReturn("encoded");

        changeInfoService.changePassword(request, token);

        verify(user).changePassword("encoded");
    }

    @Test
    void 비밀번호_불일치_실패() {
        String token = "token";
        Long userId = 1L;

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");

        User user = mock(User.class);

        when(jwtTokenProvider.getUserIdFromAccessToken(token)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> {
            changeInfoService.changePassword(request, token);
        });
    }
}