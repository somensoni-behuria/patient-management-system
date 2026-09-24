package com.pm.auth;

import com.pm.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    // Base64 of a >= 256-bit secret (dev only).
    private static final String SECRET =
            "ZGV2LW9ubHktc2VjcmV0LXBsZWFzZS1vdmVycmlkZS1pbi1wcm9kLTAxMjM0NTY3ODlBQkNERUY=";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000L);

    @Test
    void generatesAndValidatesToken() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");

        Claims claims = jwtUtil.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo("user@test.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtUtil.validateToken(tampered))
                .isInstanceOf(JwtException.class);
    }
}
