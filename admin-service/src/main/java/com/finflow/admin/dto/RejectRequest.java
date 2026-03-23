package com.finflow.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectRequest {

    @NotBlank(message = "Decision reason is required for rejection")
    private String decisionReason;
}
