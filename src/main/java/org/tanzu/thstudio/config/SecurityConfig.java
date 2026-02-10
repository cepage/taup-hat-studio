package org.tanzu.thstudio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TaupHatProperties properties;

    public SecurityConfig(TaupHatProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (properties.security().localMode()) {
            return localSecurityChain(http);
        }
        return oauthSecurityChain(http);
    }

    /**
     * Local development: permit all requests, no OAuth.
     */
    private SecurityFilterChain localSecurityChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Production: Google OAuth2 with email restriction.
     */
    private SecurityFilterChain oauthSecurityChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UserService())
                )
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessUrl("/")
            );
        return http.build();
    }

    /**
     * Custom OAuth2 user service that validates the user's email against the allowed emails list.
     */
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService() {
        var delegate = new DefaultOAuth2UserService();
        Set<String> allowedEmails = parseAllowedEmails(properties.security().allowedEmails());

        return request -> {
            OAuth2User user = delegate.loadUser(request);
            String email = user.getAttribute("email");

            if (!allowedEmails.isEmpty() && (email == null || !allowedEmails.contains(email.toLowerCase()))) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Access denied. Email " + email + " is not in the allowed list.");
            }

            return new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                    user.getAttributes(),
                    "email"
            );
        };
    }

    private static Set<String> parseAllowedEmails(String allowedEmails) {
        if (allowedEmails == null || allowedEmails.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}
