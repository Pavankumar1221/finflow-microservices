package com.finflow.document.repository;

import com.finflow.document.entity.DocumentVerificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentVerificationHistoryRepository extends JpaRepository<DocumentVerificationHistory, Long> {
    List<DocumentVerificationHistory> findByDocumentIdOrderByVerifiedAtDesc(Long documentId);
    void deleteByDocumentId(Long documentId);
}
