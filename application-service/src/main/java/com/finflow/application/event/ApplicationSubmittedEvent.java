package com.finflow.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSubmittedEvent {
    private String eventType;
    private Long applicationId;
    private String applicationNumber;
    private Long applicantId;
    private LocalDateTime submittedAt;
}
