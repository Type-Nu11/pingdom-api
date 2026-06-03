package com.typenull.pingdom.users;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.api.dto.profile.ChangePasswordRequest;
import com.typenull.pingdom.identity.application.service.ChangeInfoService;
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
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ChangeInfoService changeInfoService;

    @Test
    void 비밀번호_변경_성공() {
        Long userId = 1L;
        ChangePasswordRequest request = new ChangePasswordRequest("1234", "abcd1234", "abcd1234");

        User user = mock(User.class);
        when(user.getPassword()).thenReturn("encoded_1234");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", "encoded_1234")).thenReturn(true);
        when(passwordEncoder.encode("abcd1234")).thenReturn("encoded_abcd1234");

        changeInfoService.changePassword(request, userId);

        verify(user).changePassword("encoded_abcd1234");
    }

    @Test
    void 비밀번호_불일치_실패() {
        Long userId = 1L;
        ChangePasswordRequest request = new ChangePasswordRequest("wrong", "abcd1234", "abcd1234");

        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("wrong"), any())).thenReturn(false);

        assertThrows(AuthException.class, () -> {
            changeInfoService.changePassword(request, userId);
        });
    }
}