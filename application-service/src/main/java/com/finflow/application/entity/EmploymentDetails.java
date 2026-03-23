package com.finflow.application.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "employment_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmploymentDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    private EmploymentType employmentType;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(length = 100)
    private String designation;

    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "total_work_experience")
    private Integer totalWorkExperience;

    @Column(name = "office_address")
    private String officeAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status")
    private EmploymentStatus employmentStatus;

    public enum EmploymentType { SALARIED, SELF_EMPLOYED, BUSINESS, UNEMPLOYED }
    public enum EmploymentStatus { ACTIVE, RESIGNED, RETIRED }
}
