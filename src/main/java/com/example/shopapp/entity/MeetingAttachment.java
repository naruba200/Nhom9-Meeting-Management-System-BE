package com.example.shopapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long meetingId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 100)
    private String fileType;

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false, length = 500)
    private String cloudUploadUrl;

    @Column(length = 255)
    private String cloudPublicId;

    @Column(nullable = false, length = 30)
    private String cloudUploadStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
