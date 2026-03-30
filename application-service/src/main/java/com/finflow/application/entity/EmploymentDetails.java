package com.finflow.application.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "Company name is required")
    @Size(max = 150, message = "Company name must not exceed 150 characters")
    private String companyName;

    @Column(length = 100)
    @NotBlank(message = "Designation is required")
    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;

    @Column(name = "monthly_income", precision = 15, scale = 2)
    @PositiveOrZero(message = "Monthly income cannot be negative")
    private BigDecimal monthlyIncome;

    @Column(name = "total_work_experience")
    @PositiveOrZero(message = "Total work experience cannot be negative")
    private Integer totalWorkExperience;

    @Column(name = "office_address")
    @NotBlank(message = "Office address is required")
    private String officeAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status")
    private EmploymentStatus employmentStatus;

    public enum EmploymentType { SALARIED, SELF_EMPLOYED, BUSINESS, UNEMPLOYED }
    public enum EmploymentStatus { ACTIVE, RESIGNED, RETIRED }
}
