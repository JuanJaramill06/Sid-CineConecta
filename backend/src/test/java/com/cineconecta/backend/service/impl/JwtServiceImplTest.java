package com.cineconecta.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.cineconecta.backend.config.JwtProperties;
import com.cineconecta.backend.security.AuthenticatedUser;
import com.cineconecta.backend.security.Role;
import com.cineconecta.backend.service.JwtService.IssuedToken;

class JwtServiceImplTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final Instant T0 = Instant.parse("2026-11-25T12:00:00Z");
    private static final JwtProperties PROPS = new JwtProperties(SECRET, Duration.ofHours(8));

    private static JwtServiceImpl at(Instant instant) {
        return new JwtServiceImpl(PROPS, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static AuthenticatedUser client() {
        return new AuthenticatedUser("1", "juan.perez@example.com", "Juan Pérez", Role.CLIENT, "$2a$10$hash");
    }

    @Test
    void roundTripKeepsIdRoleAndEmailButNoPassword() {
        IssuedToken issued = at(T0).issue(client());

        AuthenticatedUser parsed = at(T0.plusSeconds(60)).parse(issued.token()).orElseThrow();

        assertThat(parsed.id()).isEqualTo("1");
        assertThat(parsed.accountId()).isEqualTo(1);
        assertThat(parsed.role()).isEqualTo(Role.CLIENT);
        assertThat(parsed.email()).isEqualTo("juan.perez@example.com");
        assertThat(parsed.fullName()).isEqualTo("Juan Pérez");
        assertThat(parsed.getPassword()).isNull();
        assertThat(issued.expiresAt()).isEqualTo(T0.plus(Duration.ofHours(8)));
        assertThat(issued.token()).doesNotContain("hash");
    }

    @Test
    void expiredTokenIsRejected() {
        String token = at(T0).issue(client()).token();

        assertThat(at(T0.plus(Duration.ofHours(9))).parse(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = at(T0).issue(client()).token();
        String[] parts = token.split("\\.");
        // Same signature, payload changed to another account
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1])).replace("\"sub\":\"1\"", "\"sub\":\"2\"").getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(at(T0).parse(forged)).isEmpty();
        assertThat(at(T0).parse("not-a-jwt")).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = at(T0).issue(client()).token();
        JwtServiceImpl otherKey = new JwtServiceImpl(new JwtProperties(null, Duration.ofHours(8)),
                Clock.fixed(T0, ZoneOffset.UTC)); // random key

        assertThat(otherKey.parse(token)).isEmpty();
    }

    @Test
    void shortSecretFailsAtStartup() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new JwtServiceImpl(new JwtProperties(shortSecret, Duration.ofHours(1))))
                .isInstanceOf(IllegalStateException.class);
    }
}
