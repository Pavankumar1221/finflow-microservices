package com.finflow.application.repository;

import com.finflow.application.entity.EmploymentDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EmploymentDetailsRepository extends JpaRepository<EmploymentDetails, Long> {
    Optional<EmploymentDetails> findByApplicationId(Long applicationId);
}
