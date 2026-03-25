package com.example.shopapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "minutes_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinutesTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minutes_id", nullable = false)
    private MeetingMinutes minutes;

    @Column(nullable = false)
    private Long taskId;

    @Column(length = 255)
    private String taskTitle;

    @Column(length = 150)
    private String assigneeEmail;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
