package com.finflow.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalDetailsResponse {
    private Long id;
    private Long applicationId;
    private String firstName;
    private String lastName;
    private LocalDate dob;
    private String gender;
    private String maritalStatus;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;
    private String nationality;
}
