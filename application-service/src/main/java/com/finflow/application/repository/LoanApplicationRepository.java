package com.finflow.application.repository;

import com.finflow.application.entity.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    List<LoanApplication> findByApplicantId(Long applicantId);
    Optional<LoanApplication> findByApplicationNumber(String applicationNumber);
    List<LoanApplication> findByStatus(LoanApplication.ApplicationStatus status);
    Optional<LoanApplication> findByIdAndApplicantId(Long id, Long applicantId);
}
