package org.tanzu.thstudio.config;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TaupHatProperties properties;

    public AuthController(TaupHatProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/user")
    public ResponseEntity<?> currentUser(@AuthenticationPrincipal OAuth2User user) {
        if (properties.security().localMode()) {
            return ResponseEntity.ok(Map.of(
                    "name", "Local Developer",
                    "email", "dev@localhost",
                    "localMode", true
            ));
        }
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(Map.of(
                "name", user.getAttribute("name"),
                "email", user.getAttribute("email"),
                "picture", user.getAttribute("picture")
        ));
    }
}
