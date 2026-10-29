package com.shopchain.service;

import com.shopchain.entity.User;
import com.shopchain.repository.RoleRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.security.JwtTokenProvider;
import com.shopchain.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {
    @Test
    void passwordResetIncrementsTokenVersionSoExistingTokensBecomeStale() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        SmtpMailService smtp = mock(SmtpMailService.class);
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        User user = User.builder().id(7L).email("member@example.test").password("old-hash")
                .authTokenVersion(4).enabled(true).build();
        when(users.findByEmailIgnoreCase("member@example.test")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(encoder.encode("new-password")).thenReturn("new-hash");
        AuthServiceImpl auth = new AuthServiceImpl(users, roles, encoder, smtp, tokenProvider, authenticationManager);

        auth.sendForgotPasswordOtp("member@example.test");
        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(smtp).send(eq("member@example.test"), anyString(), body.capture());
        Matcher otp = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(body.getValue());
        assertThat(otp.find()).isTrue();

        auth.resetPassword("member@example.test", otp.group(1), "new-password");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getAuthTokenVersion()).isEqualTo(5);
        verify(users).save(user);
    }

    @Test
    void logoutRevokesAllCurrentAccountTokens() {
        UserRepository users = mock(UserRepository.class);
        User user = User.builder().id(7L).username("member").authTokenVersion(2).build();
        when(users.findByUsername("member")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        AuthServiceImpl auth = new AuthServiceImpl(users, mock(RoleRepository.class), mock(PasswordEncoder.class),
                mock(SmtpMailService.class), mock(JwtTokenProvider.class), mock(AuthenticationManager.class));

        auth.logout("member");

        assertThat(user.getAuthTokenVersion()).isEqualTo(3);
        verify(users).save(user);
    }

    @Test
    void lockedAccountIsRejectedByAuthenticationManagerBeforeTokenIsCreated() {
        UserRepository users = mock(UserRepository.class);
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenThrow(new DisabledException("disabled"));
        AuthServiceImpl auth = new AuthServiceImpl(users, mock(RoleRepository.class), mock(PasswordEncoder.class),
                mock(SmtpMailService.class), tokenProvider, manager);

        assertThatThrownBy(() -> auth.login("disabled", "password"))
                .isInstanceOf(DisabledException.class);
        verifyNoInteractions(tokenProvider);
        verify(users, never()).findByUsername(anyString());
    }
}
