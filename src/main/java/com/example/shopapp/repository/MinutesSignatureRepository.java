package com.example.shopapp.repository;

import com.example.shopapp.entity.MinutesSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MinutesSignatureRepository extends JpaRepository<MinutesSignature, Long> {
    List<MinutesSignature> findByMinutesId(Long minutesId);

    Optional<MinutesSignature> findByMinutesIdAndSignerEmail(Long minutesId, String signerEmail);
}
