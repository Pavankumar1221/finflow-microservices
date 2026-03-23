package com.finflow.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_verification_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVerificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "verified_by", nullable = false)
    private Long verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Document.VerificationStatus status;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "verified_at", updatable = false)
    private LocalDateTime verifiedAt;
}
