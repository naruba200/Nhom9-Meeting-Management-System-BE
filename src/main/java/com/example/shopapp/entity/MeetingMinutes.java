package com.example.shopapp.entity;

import com.example.shopapp.enums.MinutesStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meeting_minutes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long meetingId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String noteTakerEmail;

    @Column(length = 255)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(columnDefinition = "TEXT")
    private String attendees;

    @Column(columnDefinition = "TEXT")
    private String absentees;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String decisions;

    @Column(columnDefinition = "TEXT")
    private String contributions;

    @Column(columnDefinition = "TEXT")
    private String voting;

    @Column(columnDefinition = "TEXT")
    private String conclusions;

    @Column(nullable = false)
    private LocalDateTime minutesCreatedAt;

    @Column(nullable = false)
    private LocalDateTime minutesClosedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MinutesStatus status;

    @OneToMany(mappedBy = "minutes", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<MinutesSignature> signatures = new ArrayList<>();

    @OneToMany(mappedBy = "minutes", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<MinutesTask> tasks = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 1000)
    private String pdfUrl;
}
