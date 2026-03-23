package com.finflow.application.repository;

import com.finflow.application.entity.LoanDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface LoanDetailsRepository extends JpaRepository<LoanDetails, Long> {
    Optional<LoanDetails> findByApplicationId(Long applicationId);
}
