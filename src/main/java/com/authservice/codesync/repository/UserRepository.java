package com.authservice.codesync.repository;

import com.authservice.codesync.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByUserId(Long userId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findAllByRole(User.Role role);

    // Find OAuth2 user by provider + providerId
    Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId);

    // Search users by partial username (for invite / mention lookup)
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) AND u.isActive = true")
    List<User> searchByUsername(@Param("query") String query);

    void deleteByUserId(Long userId);
}