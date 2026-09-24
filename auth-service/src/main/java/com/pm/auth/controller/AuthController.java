package com.pm.auth.controller;

import com.pm.auth.dto.LoginRequestDTO;
import com.pm.auth.dto.LoginResponseDTO;
import com.pm.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints. Reached through the gateway route {@code /auth/**} (StripPrefix=1), so the
 * gateway forwards {@code /auth/login -> /login} and {@code /auth/validate -> /validate}.
 */
@RestController
@Tag(name = "Auth", description = "Authentication and token validation")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a JWT")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.authenticate(request)
                .map(token -> ResponseEntity.ok(new LoginResponseDTO(token)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate a bearer token (used by the API gateway)")
    public ResponseEntity<Void> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean valid = authService.validateToken(authHeader.substring(7));
        return valid
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
