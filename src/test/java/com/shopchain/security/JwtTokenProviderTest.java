package com.shopchain.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {
    @Test
    void tokenVersionInvalidatesTokensIssuedBeforePasswordResetOrLogout() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 60_000L);
        provider.init();
        org.springframework.security.core.userdetails.UserDetails principal = User.withUsername("member")
                .password("hash").authorities("ROLE_USER").build();
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        String before = provider.generateToken(authentication, 3);
        String after = provider.generateToken(authentication, 4);

        assertThat(provider.isTokenVersionCurrent(before, 3)).isTrue();
        assertThat(provider.isTokenVersionCurrent(before, 4)).isFalse();
        assertThat(provider.isTokenVersionCurrent(after, 4)).isTrue();
        assertThat(provider.isTokenVersionCurrent("not-a-jwt", 4)).isFalse();
    }
}
