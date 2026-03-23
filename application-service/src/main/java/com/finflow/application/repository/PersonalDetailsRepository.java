package com.finflow.application.repository;

import com.finflow.application.entity.ApplicantPersonalDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PersonalDetailsRepository extends JpaRepository<ApplicantPersonalDetails, Long> {
    Optional<ApplicantPersonalDetails> findByApplicationId(Long applicationId);
}
