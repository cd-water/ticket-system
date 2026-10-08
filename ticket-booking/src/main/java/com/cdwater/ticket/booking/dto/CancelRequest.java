package com.cdwater.ticket.booking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelRequest {

    @NotBlank
    private String orderNo;
}
