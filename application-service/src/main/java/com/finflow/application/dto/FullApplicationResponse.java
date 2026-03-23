package com.finflow.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;




@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullApplicationResponse {
    private LoanApplicationResponse application;
    private PersonalDetailsResponse personalDetails;
    private EmploymentDetailsResponse employmentDetails;
    private LoanDetailsResponse loanDetails;
}
