package io.github.cskwork.finlab.auth.web;

import io.github.cskwork.finlab.auth.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password", description = "Returns a short-lived RS256 bearer token. Passwords are checked against a one-way hash and never returned.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        var result = auth.login(request.email(), request.password(), servletRequest.getRemoteAddr());
        return new LoginResponse(result.userId(), result.email(), result.role(), result.token());
    }

    @Schema(name = "LoginRequest", description = "Email and transient password credentials")
    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password", description = "Between 1 and 72 UTF-8 bytes") String password) {
        @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다")
        @JsonIgnore
        @Schema(hidden = true)
        public boolean isPasswordWithinByteLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
    @Schema(name = "LoginResponse", description = "Authenticated identity and a short-lived access token")
    public record LoginResponse(long userId, String email, String role, String token) {}
}
