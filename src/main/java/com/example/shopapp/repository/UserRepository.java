package com.example.shopapp.repository;

import com.example.shopapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleOauthState(String googleOauthState);

    boolean existsByEmail(String email);

    // Batch query để tìm tất cả emails tồn tại trong 1 query thay vì N queries
    @Query("SELECT u.email FROM User u WHERE LOWER(u.email) IN :emails")
    List<String> findExistingEmails(@Param("emails") List<String> emails);
}