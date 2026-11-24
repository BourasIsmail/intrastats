package ma.entraide.intranet.security;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.entraide.intranet.common.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String login, @NotBlank String motDePasse) {}

    public record PasswordRequest(@NotBlank String ancien,
                                  @NotBlank @Size(min = 10, message = "10 caractères minimum") String nouveau) {}

    private final AuthService auth;
    private final CurrentUsers current;
    private final AppProperties props;

    public AuthController(AuthService auth, CurrentUsers current, AppProperties props) {
        this.auth = auth;
        this.current = current;
        this.props = props;
    }

    @PostMapping("/login")
    public AuthService.UserInfo login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.Session session = auth.login(request.login(), request.motDePasse());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(session.token(), props.jwt().ttl()).toString());
        return session.user();
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    @GetMapping("/me")
    public AuthService.UserInfo me() {
        return auth.me(current.get().id());
    }

    @PostMapping("/mot-de-passe")
    public void changePassword(@Valid @RequestBody PasswordRequest request) {
        auth.changePassword(current.get().id(), request.ancien(), request.nouveau());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(props.cookie().name(), value)
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
