package com.finflow.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "target_entity", length = 100)
    private String targetEntity;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "action_summary", columnDefinition = "TEXT")
    private String actionSummary;

    @CreationTimestamp
    @Column(name = "action_at", updatable = false)
    private LocalDateTime actionAt;
}
