package com.example.shopapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "minutes_signatures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinutesSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minutes_id", nullable = false)
    private MeetingMinutes minutes;

    @Column(nullable = false, length = 150)
    private String signerEmail;

    @Column(length = 255)
    private String signerName;

    @Column
    private LocalDateTime signedAt;

    @Column
    private boolean agreed;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
