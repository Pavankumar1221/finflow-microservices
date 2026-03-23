package com.finflow.application.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "from_status", length = 50)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 50)
    private String toStatus;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_by_role", length = 50)
    private String changedByRole;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "changed_at", updatable = false)
    private LocalDateTime changedAt;
}
