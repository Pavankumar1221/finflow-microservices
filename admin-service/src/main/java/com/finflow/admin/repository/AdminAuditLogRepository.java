package com.finflow.admin.repository;

import com.finflow.admin.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    Page<AdminAuditLog> findByAdminId(Long adminId, Pageable pageable);
    List<AdminAuditLog> findByTargetEntityAndTargetId(String targetEntity, Long targetId);
    Page<AdminAuditLog> findAll(Pageable pageable);
}
