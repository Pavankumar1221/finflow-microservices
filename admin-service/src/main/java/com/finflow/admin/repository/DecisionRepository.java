package com.finflow.admin.repository;

import com.finflow.admin.entity.Decision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DecisionRepository extends JpaRepository<Decision, Long> {
    Optional<Decision> findByApplicationId(Long applicationId);
    boolean existsByApplicationId(Long applicationId);
}
