package com.finflow.document.repository;

import com.finflow.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByApplicationId(Long applicationId);
    List<Document> findByApplicationIdAndVerificationStatus(Long applicationId, Document.VerificationStatus status);
    boolean existsByApplicationIdAndDocumentType(Long applicationId, String documentType);
    java.util.Optional<Document> findByApplicationIdAndDocumentType(Long applicationId, String documentType);
    long countByApplicationId(Long applicationId);
}
