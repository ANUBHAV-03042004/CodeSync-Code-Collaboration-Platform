package com.authservice.codesync.service;

import com.authservice.codesync.entity.User;
import com.authservice.codesync.repository.UserRepository;
import com.authservice.codesync.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtTokenProvider      jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           AuthenticationManager authenticationManager) {
        this.userRepository       = userRepository;
        this.passwordEncoder      = passwordEncoder;
        this.jwtTokenProvider     = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Override
    public User register(String username, String email, String rawPassword, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use: " + email);
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .provider(User.AuthProvider.LOCAL)
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new user: {} ({})", username, email);
        return saved;
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    public String login(String email, String rawPassword) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, rawPassword));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        UserDetails userDetails = buildUserDetails(user);
        String token = jwtTokenProvider.generateAccessToken(userDetails, user.getUserId(), user.getRole().name());
        log.info("User logged in: {}", email);
        return token;
    }

    // ── Token operations ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    @Override
    @Transactional(readOnly = true)
    public String refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }
        String email = jwtTokenProvider.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found for token"));

        UserDetails userDetails = buildUserDetails(user);
        return jwtTokenProvider.generateAccessToken(userDetails, user.getUserId(), user.getRole().name());
    }

    // ── User lookup ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long userId) {
        return userRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> searchUsers(String query) {
        return userRepository.searchByUsername(query);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // ── Profile management ────────────────────────────────────────────────────

    @Override
    public User updateProfile(Long userId, String username, String fullName, String bio, String avatarUrl) {
        User user = findActiveUserById(userId);

        if (username != null && !username.equals(user.getUsername())) {
            if (userRepository.existsByUsername(username)) {
                throw new IllegalArgumentException("Username already taken: " + username);
            }
            user.setUsername(username);
        }
        if (fullName  != null) user.setFullName(fullName);
        if (bio       != null) user.setBio(bio);
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl);

        return userRepository.save(user);
    }

    @Override
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findActiveUserById(userId);

        if (user.getProvider() != User.AuthProvider.LOCAL) {
            throw new IllegalStateException("Password change is not available for OAuth accounts");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", userId);
    }

    // ── Account lifecycle ─────────────────────────────────────────────────────

    @Override
    public void deactivateAccount(Long userId) {
        User user = findActiveUserById(userId);
        user.setActive(false);
        userRepository.save(user);
        log.info("Account deactivated: {}", userId);
    }

    @Override
    public void reactivateAccount(Long userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setActive(true);
        userRepository.save(user);
        log.info("Account reactivated: {}", userId);
    }

    @Override
    public void deleteAccount(Long userId) {
        userRepository.deleteByUserId(userId);
        log.info("Account permanently deleted: {}", userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User findActiveUserById(Long userId) {
        return userRepository.findByUserId(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Active user not found: " + userId));
    }

    private UserDetails buildUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
