package ti4.spring.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/auth")
public class DiscordAuthController {

    private final DiscordAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(@RequestBody AuthRequest request) {
        if (!StringUtils.hasText(request.getUserId()) || !StringUtils.hasText(request.getCode())) {
            return ResponseEntity.badRequest().build();
        }

        AuthResponse response = authService.authenticate(request.getUserId(), request.getCode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        if (!StringUtils.hasText(request.getUserId()) || !StringUtils.hasText(request.getRefreshToken())) {
            return ResponseEntity.badRequest().build();
        }

        AuthResponse response = authService.refresh(request.getUserId(), request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
}
