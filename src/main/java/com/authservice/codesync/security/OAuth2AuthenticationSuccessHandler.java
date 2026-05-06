package com.authservice.codesync.security;

import com.authservice.codesync.entity.User;
import com.authservice.codesync.repository.UserRepository;
import com.authservice.codesync.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final JwtTokenProvider      jwtTokenProvider;
    private final UserRepository        userRepository;
    private final TokenBlacklistService blacklistService;

    @Value("${app.frontend-url:https://yourscode.netlify.app}")
    private String frontendUrl;

    public OAuth2AuthenticationSuccessHandler(JwtTokenProvider jwtTokenProvider,
                                              UserRepository userRepository,
                                              TokenBlacklistService blacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository   = userRepository;
        this.blacklistService = blacklistService;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        User user = resolveOrCreateUser(oauthUser, registrationId);

        org.springframework.security.core.userdetails.UserDetails userDetails =
                org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password("")
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                        .build();

        String accessToken  = jwtTokenProvider.generateAccessToken(userDetails, user.getUserId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        // Seed the inactivity window for this OAuth session
        blacklistService.recordActivity(user.getUserId());

        // Security fix: tokens go in the URL fragment (#), not query params (?).
        // Fragments are never sent to the server, never appear in access logs,
        // and are not included in the Referer header.
        // The Angular /oauth2/callback page reads window.location.hash.
        String redirectUrl = frontendUrl + "/oauth2/callback"
                + "#token="        + accessToken
                + "&refreshToken=" + refreshToken;

        log.info("OAuth2 login success for user: {} via {}", user.getEmail(), registrationId);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    @Transactional
    protected User resolveOrCreateUser(OAuth2User oauthUser, String registrationId) {
        Map<String, Object> attrs = oauthUser.getAttributes();

        String email;
        String name;
        String avatarUrl;
        String providerId;
        User.AuthProvider provider;

        if ("github".equals(registrationId)) {
            email      = (String) attrs.get("email");
            name       = (String) attrs.getOrDefault("name", attrs.get("login"));
            avatarUrl  = (String) attrs.get("avatar_url");
            providerId = String.valueOf(attrs.get("id"));
            provider   = User.AuthProvider.GITHUB;

            // Use GitHub's official noreply format: ID+login@users.noreply.github.com
            if (email == null || email.isBlank()) {
                String login = (String) attrs.get("login");
                email = providerId + "+" + login + "@users.noreply.github.com";
            }
        } else { // google
            email      = (String) attrs.get("email");
            name       = (String) attrs.get("name");
            avatarUrl  = (String) attrs.get("picture");
            providerId = (String) attrs.get("sub");
            provider   = User.AuthProvider.GOOGLE;
        }

        final String finalEmail               = email;
        final String finalName                = name;
        final String finalAvatar              = avatarUrl;
        final String finalProvider            = providerId;
        final User.AuthProvider finalProviderEnum = provider;

        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    String base = finalEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
                    if (base.isBlank()) base = "user";
                    String uniqueUsername = ensureUniqueUsername(base);

                    User newUser = User.builder()
                            .email(finalEmail)
                            .username(uniqueUsername)
                            .fullName(finalName)
                            .avatarUrl(finalAvatar)
                            .provider(finalProviderEnum)
                            .providerId(finalProvider)
                            .isActive(true)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    private String ensureUniqueUsername(String base) {
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        String candidate;
        do {
            candidate = base + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        } while (userRepository.existsByUsername(candidate));
        return candidate;
    }
}
