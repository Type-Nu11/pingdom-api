package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.api.dto.profile.ChangePasswordRequest;
import com.typenull.pingdom.identity.api.dto.profile.ChangeUsernameRequest;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ChangeInfoServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ChangeInfoService changeInfoService;

    @Test
    void keepsCurrentUsernameWithoutAnotherWrite() {
        User user = usernameUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        changeInfoService.changeUsername(new ChangeUsernameRequest("current-user"), 1L);

        assertThat(user.getUsername()).isEqualTo("current-user");
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).flush();
    }

    @Test
    void changesAvailableUsername() {
        User user = usernameUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        changeInfoService.changeUsername(new ChangeUsernameRequest("available-user"), 1L);

        assertThat(user.getUsername()).isEqualTo("available-user");
        verify(userRepository).existsByUsername("available-user");
        verify(userRepository).flush();
    }

    @Test
    void rejectsAnotherUsersUsernameBeforeMutation() {
        User user = usernameUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken-user")).thenReturn(true);

        assertThatThrownBy(() -> changeInfoService.changeUsername(new ChangeUsernameRequest("taken-user"), 1L))
                .isInstanceOfSatisfying(UsersException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UsersErrorCode.USERNAME_ALREADY_EXISTS));

        assertThat(user.getUsername()).isEqualTo("current-user");
        verify(userRepository, never()).flush();
    }

    @Test
    void mapsExactUsernameUniqueRaceToExistingProfileContract() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(usernameUser()));
        DataIntegrityViolationException failure = integrityFailure("uk_users_username", "23505");
        doThrow(failure).when(userRepository).flush();

        assertThatThrownBy(() -> changeInfoService.changeUsername(new ChangeUsernameRequest("raced-user"), 1L))
                .isInstanceOfSatisfying(UsersException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UsersErrorCode.USERNAME_ALREADY_EXISTS));
    }

    @Test
    void preservesOtherIntegrityFailuresEvenWhenMessageMentionsUsernameConstraint() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(usernameUser()));
        DataIntegrityViolationException failure = integrityFailure("uk_users_email", "23505");
        doThrow(failure).when(userRepository).flush();

        assertThatThrownBy(() -> changeInfoService.changeUsername(new ChangeUsernameRequest("next-user"), 1L))
                .isSameAs(failure);
    }

    @Test
    void doesNotTreatAConstraintNameSubstringAsUsernameUniqueViolation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(usernameUser()));
        DataIntegrityViolationException failure = integrityFailure("other_uk_users_username", "23505");
        doThrow(failure).when(userRepository).flush();

        assertThatThrownBy(() -> changeInfoService.changeUsername(new ChangeUsernameRequest("next-user"), 1L))
                .isSameAs(failure);
    }

    @Test
    void preservesNonUniqueConstraintFailure() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(usernameUser()));
        DataIntegrityViolationException failure = integrityFailure("uk_users_username", "23514");
        doThrow(failure).when(userRepository).flush();

        assertThatThrownBy(() -> changeInfoService.changeUsername(new ChangeUsernameRequest("next-user"), 1L))
                .isSameAs(failure);
    }

    @Test
    void preservesUserOptimisticConflictForTheExistingHandler() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(usernameUser()));
        ObjectOptimisticLockingFailureException failure = new ObjectOptimisticLockingFailureException(User.class, 1L);
        doThrow(failure).when(userRepository).flush();

        assertThatThrownBy(() -> changeInfoService.changeUsername(new ChangeUsernameRequest("next-user"), 1L))
                .isSameAs(failure);
    }

    @Test
    void rejectsMissingUser() {
        assertThatThrownBy(() -> changeInfoService.changeUsername(new ChangeUsernameRequest("next-user"), 1L))
                .isInstanceOfSatisfying(UsersException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UsersErrorCode.USER_NOT_FOUND));
        verify(userRepository, never()).flush();
    }

    private User usernameUser() {
        return User.builder().id(1L).username("current-user").build();
    }

    private DataIntegrityViolationException integrityFailure(String constraint, String sqlState) {
        return new DataIntegrityViolationException("uk_users_username is mentioned but not trusted",
                new IllegalStateException("wrapped", new ConstraintViolationException(
                        "hidden detail", new SQLException("db failure", sqlState), constraint)));
    }

    /**
     * 현재 비밀번호가 인코더 검증을 통과하면 새 비밀번호를 암호화한 값으로 사용자 변경을 호출하는지 검증.
     */
    @Test
    void changesVerifiedPassword() {
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

    /**
     * 현재 비밀번호가 저장값과 일치하지 않으면 AuthException으로 변경을 거절하는지 검증.
     */
    @Test
    void rejectsIncorrectCurrentPassword() {
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
